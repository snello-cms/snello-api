package io.snello.service;

import io.quarkus.logging.Log;
import io.snello.api.service.JdbcRepository;
import io.snello.model.Metadata;
import io.snello.model.pojo.BackupDataImportResult;
import io.snello.model.pojo.TableExport;
import io.snello.util.MetadataUtils;
import io.snello.util.ZipUtils;
import jakarta.inject.Inject;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static io.snello.management.AppConstants.UUID;

/**
 * Business-data backup: chunked JSON pages packed into a ZIP (no metadata schema, no DDL on import).
 */
@ApplicationScoped
public class BackupService {

    public static final String KIND = "snello-backup-data";
    public static final int DEFAULT_PAGE_SIZE = 500;

    @Inject
    ApiService apiService;

    @Inject
    MetadataService metadataService;

    @Inject
    JdbcRepository jdbcRepository;

    @ConfigProperty(name = "snello.backup.data.page-size", defaultValue = "" + DEFAULT_PAGE_SIZE)
    int pageSize;

    private final JsonMapper mapper = JsonMapper.builder().build();

    /** Legacy stub API kept for /files callers — not used for data backup. */
    public java.util.zip.ZipFile exportData() {
        return null;
    }

    /**
     * Export all non-reserved business tables. Missing physical tables are skipped (logged).
     *
     * @return path to a temp ZIP file (caller should delete after streaming)
     */
    public Path exportAllDataZip() throws Exception {
        List<String> tables = new ArrayList<>();
        for (String name : metadataService.metadataMap().keySet()) {
            if (!MetadataUtils.isReserved(name)) {
                tables.add(name);
            }
        }
        tables.sort(String::compareTo);
        return buildDataZip(tables, true);
    }

    /**
     * Export a single metadata table. Fails if metadata is unknown or the physical table is missing.
     */
    public Path exportTableDataZip(String metadataName) throws Exception {
        if (metadataName == null || metadataName.isBlank()) {
            throw new WebApplicationException("metadata_name is required", Response.Status.BAD_REQUEST);
        }
        if (MetadataUtils.isReserved(metadataName)) {
            throw new WebApplicationException("reserved metadata name: " + metadataName, Response.Status.BAD_REQUEST);
        }
        Metadata meta = metadataService.metadataMap().get(metadataName);
        if (meta == null) {
            throw new NotFoundException("metadata not found: " + metadataName);
        }
        return buildDataZip(List.of(metadataName), false);
    }

    public BackupDataImportResult importData(InputStream zipStream) throws Exception {
        if (zipStream == null) {
            throw new WebApplicationException("missing zip body", Response.Status.BAD_REQUEST);
        }
        Path tmpRoot = Files.createTempDirectory("snello-backup-import-");
        try {
            ZipUtils.unzip(zipStream, tmpRoot);
            Path manifestPath = tmpRoot.resolve("manifest.json");
            if (!Files.isRegularFile(manifestPath)) {
                throw new WebApplicationException("invalid backup zip: missing manifest.json", Response.Status.BAD_REQUEST);
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> manifest = mapper.readValue(Files.readAllBytes(manifestPath), Map.class);
            if (!KIND.equals(manifest.get("kind"))) {
                throw new WebApplicationException(
                        "invalid backup zip: kind must be " + KIND + ", got " + manifest.get("kind"),
                        Response.Status.BAD_REQUEST);
            }

            Path dataDir = tmpRoot.resolve("data");
            BackupDataImportResult result = new BackupDataImportResult();
            if (!Files.isDirectory(dataDir)) {
                return result;
            }

            try (Stream<Path> tableDirs = Files.list(dataDir)) {
                List<Path> dirs = tableDirs.filter(Files::isDirectory)
                        .sorted(Comparator.comparing(p -> p.getFileName().toString()))
                        .toList();
                for (Path tableDir : dirs) {
                    String tableName = tableDir.getFileName().toString();
                    importTableDir(tableName, tableDir, result);
                }
            }
            return result;
        } finally {
            ZipUtils.deleteRecursively(tmpRoot);
        }
    }

    /** Imports the {@code NNNN.json} chunks of one table directory (backup zip layout {@code data/<table>/}). */
    public void importTableDir(String tableName, Path tableDir, BackupDataImportResult result) {
        String tableKey = resolveImportTableKey(tableName, result);
        if (tableKey == null) {
            return;
        }

        List<Path> chunks;
        try (Stream<Path> files = Files.list(tableDir)) {
            chunks = files.filter(p -> p.getFileName().toString().endsWith(".json"))
                    .sorted(Comparator.comparing(p -> p.getFileName().toString()))
                    .toList();
        } catch (IOException e) {
            result.errors.add(tableName + ": cannot list chunks (" + e.getMessage() + ")");
            return;
        }

        Set<String> physicalColumns = null;
        try {
            physicalColumns = jdbcRepository.columns(tableName);
        } catch (Exception e) {
            Log.warnf(e, "data import table=%s: cannot list columns, inserting raw rows", tableName);
        }

        for (Path chunk : chunks) {
            try {
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> rows = mapper.readValue(Files.readAllBytes(chunk), List.class);
                if (rows == null) {
                    continue;
                }
                Log.infof("backup import table=%s chunk=%s rows=%d", tableName, chunk.getFileName(), rows.size());
                for (Map<String, Object> row : rows) {
                    importRow(tableName, tableKey, row, physicalColumns, result);
                }
            } catch (Exception e) {
                Log.errorf(e, "backup import failed table=%s chunk=%s", tableName, chunk.getFileName());
                result.errors.add(tableName + "/" + chunk.getFileName() + ": " + e.getMessage());
                return;
            }
        }
    }

    /**
     * Imports rows into a registered metadata table. Rows whose key already exists are skipped, failures are
     * collected in {@code result} (never thrown).
     */
    public void importRows(String tableName, List<Map<String, Object>> rows, BackupDataImportResult result) {
        String tableKey = resolveImportTableKey(tableName, result);
        if (tableKey == null || rows == null) {
            return;
        }
        Set<String> physicalColumns = null;
        try {
            physicalColumns = jdbcRepository.columns(tableName);
        } catch (Exception e) {
            Log.warnf(e, "data import table=%s: cannot list columns, inserting raw rows", tableName);
        }
        Log.infof("data import table=%s key=%s rows=%d columns=%s", tableName, tableKey, rows.size(),
                physicalColumns == null ? "?" : physicalColumns.size());
        for (Map<String, Object> row : rows) {
            importRow(tableName, tableKey, row, physicalColumns, result);
        }
    }

    /** @return the table key column, or null (error recorded) when the table can't take an import */
    private String resolveImportTableKey(String tableName, BackupDataImportResult result) {
        if (MetadataUtils.isReserved(tableName)) {
            result.errors.add(tableName + ": reserved name, skipped");
            return null;
        }
        Metadata meta;
        try {
            meta = metadataService.metadataMap().get(tableName);
        } catch (Exception e) {
            result.errors.add(tableName + ": cannot resolve metadata (" + e.getMessage() + ")");
            return null;
        }
        if (meta == null) {
            result.errors.add(tableName + ": metadata not registered");
            return null;
        }
        return (meta.table_key != null && !meta.table_key.isBlank()) ? meta.table_key : UUID;
    }

    private void importRow(String tableName, String tableKey, Map<String, Object> row, Set<String> physicalColumns,
                           BackupDataImportResult result) {
        Object keyVal = row.get(tableKey);
        if (keyVal == null || keyVal.toString().isBlank()) {
            result.errors.add(tableName + ": row missing key '" + tableKey + "'");
            return;
        }
        try {
            Map<String, Object> existing = apiService.fetch(null, tableName, keyVal.toString(), tableKey);
            if (existing != null && !existing.isEmpty()) {
                result.skipped++;
                return;
            }
            Map<String, Object> payload = row;
            if (physicalColumns != null && !physicalColumns.isEmpty()) {
                payload = new LinkedHashMap<>();
                for (Map.Entry<String, Object> e : row.entrySet()) {
                    if (e.getKey() != null && physicalColumns.contains(e.getKey().toLowerCase())) {
                        payload.put(e.getKey(), e.getValue());
                    }
                }
            }
            apiService.create(tableName, payload, tableKey);
            result.imported++;
        } catch (Exception e) {
            // Missing physical table or other JDBC failure — collect and continue other tables when possible
            String msg = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
            if (msg.toLowerCase().contains("not found") || msg.contains("42S02") || msg.contains("42102")) {
                result.errors.add(tableName + ": table missing or not readable (" + msg + ")");
            } else {
                result.errors.add(tableName + "/" + keyVal + ": " + msg);
            }
        }
    }

    private Path buildDataZip(List<String> tableNames, boolean skipMissingTables) throws Exception {
        Path zipPath = Files.createTempFile("snello-backup-data-", ".zip");
        List<Map<String, Object>> tableSummaries = new ArrayList<>();

        try (OutputStream fos = Files.newOutputStream(zipPath);
             ZipOutputStream zos = new ZipOutputStream(fos)) {

            for (String tableName : tableNames) {
                tableSummaries.add(writeTableData(zos, tableName, skipMissingTables));
            }

            Map<String, Object> manifest = new LinkedHashMap<>();
            manifest.put("version", 1);
            manifest.put("kind", KIND);
            manifest.put("created_at", Instant.now().toString());
            manifest.put("page_size", Math.max(1, pageSize));
            manifest.put("tables", tableSummaries);
            writeZipJson(zos, "manifest.json", manifest);
        }
        return zipPath;
    }

    /**
     * Writes one table as {@code data/<table>/NNNN.json} chunks.
     *
     * @return manifest summary: name, chunks, rows (+ skipped/reason when the table could not be read)
     */
    public Map<String, Object> writeTableData(ZipOutputStream zos, String tableName, boolean skipMissingTable) throws Exception {
        TableExport export = exportTablePages(tableName, skipMissingTable);
        int chunkIndex = 1;
        for (List<Map<String, Object>> page : export.pages()) {
            writeZipJson(zos, String.format("data/%s/%04d.json", tableName, chunkIndex++), page);
        }
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("name", tableName);
        summary.put("chunks", export.pages().size());
        summary.put("rows", export.totalRows());
        if (export.skipped()) {
            summary.put("skipped", true);
            summary.put("reason", export.skipReason());
        }
        Log.infof("data export table=%s rows=%d chunks=%d skipped=%s", tableName, export.totalRows(), export.pages().size(), export.skipped());
        return summary;
    }

    /** All rows of one table (paged reads), for the inline JSON export. Fails if the table can't be read. */
    public List<Map<String, Object>> exportTableRows(String tableName) throws Exception {
        TableExport export = exportTablePages(tableName, false);
        List<Map<String, Object>> rows = new ArrayList<>(export.totalRows());
        export.pages().forEach(rows::addAll);
        Log.infof("data export table=%s rows=%d (inline json)", tableName, rows.size());
        return rows;
    }

    private TableExport exportTablePages(String tableName, boolean skipMissingTables) throws Exception {
        List<List<Map<String, Object>>> pages = new ArrayList<>();
        int total = 0;
        int start = 0;
        int size = Math.max(1, pageSize);
        try {
            while (true) {
                List<Map<String, Object>> page = apiService.list(tableName, null, null, size, start);
                if (page == null || page.isEmpty()) {
                    break;
                }
                pages.add(page);
                total += page.size();
                start += page.size();
                if (page.size() < size) {
                    break;
                }
            }
            // empty but existing table → still record with 0 chunks
            return new TableExport(pages, total, false, null);
        } catch (Exception e) {
            Log.warnf(e, "backup export: cannot read table=%s", tableName);
            if (skipMissingTables) {
                return new TableExport(List.of(), 0, true, e.getMessage());
            }
            throw new WebApplicationException(
                    "cannot export table '" + tableName + "': " + e.getMessage(),
                    Response.Status.CONFLICT);
        }
    }

    public void writeZipJson(ZipOutputStream zos, String entryName, Object value) throws IOException {
        zos.putNextEntry(new ZipEntry(entryName));
        // write bytes so Jackson does not close the ZipOutputStream
        byte[] json = mapper.writeValueAsBytes(value);
        zos.write(json);
        zos.closeEntry();
    }
}
