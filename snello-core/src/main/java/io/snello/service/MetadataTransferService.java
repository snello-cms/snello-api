package io.snello.service;

import io.quarkus.logging.Log;
import io.snello.model.pojo.BackupDataImportResult;
import io.snello.util.MetadataUtils;
import io.snello.util.ZipUtils;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;
import tools.jackson.databind.json.JsonMapper;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipOutputStream;

import static io.snello.management.AppConstants.CREATED;
import static io.snello.management.AppConstants.METADATAS;
import static io.snello.management.AppConstants.TABLE_NAME;
import static io.snello.management.AppConstants.UUID;

/**
 * Data part of the metadata export/import: rows travel with their metadata, as JSON or ZIP.
 * <pre>
 * JSON: {"metadatas": [{"metadata": {...}, "fields": [...], "data": [rows]}]}
 * ZIP:  manifest.json                      {"kind": "snello-metadata-export", "data": true, "tables": [...]}
 *       metadatas.json                     {"metadatas": [{"metadata": {...}, "fields": [...]}]}
 *       data/&lt;table&gt;/0001.json, 0002.json  row chunks, same layout as the data backup zip
 * </pre>
 * Rows are imported with the backup rules: an existing key is skipped, errors are collected, not thrown.
 */
@ApplicationScoped
public class MetadataTransferService {

    public static final String KIND = "snello-metadata-export";
    public static final String METADATAS_ENTRY = "metadatas.json";
    public static final String MANIFEST_ENTRY = "manifest.json";
    public static final String DATA_KEY = "data";

    private static final byte[] ZIP_MAGIC = {'P', 'K', 3, 4};

    @Inject
    BackupService backupService;

    @Inject
    MetadataService metadataService;

    @Inject
    ApiService apiService;

    private final JsonMapper mapper = JsonMapper.builder().build();

    // ------------------------------------------------------------------ export

    /** Adds {@code "data": [rows]} to each export entry. Reserved tables get no data (logged). */
    public void attachInlineData(List<Map<String, Object>> entries) throws Exception {
        for (Map<String, Object> entry : entries) {
            String table = tableName(entry);
            if (table == null) {
                continue;
            }
            if (MetadataUtils.isReserved(table)) {
                Log.infof("metadata export: reserved table %s, data not exported", table);
                continue;
            }
            entry.put(DATA_KEY, backupService.exportTableRows(table));
        }
    }

    /**
     * @param entries export entries ({@code metadata} + {@code fields}), written as metadatas.json
     * @return temp ZIP (caller deletes after streaming)
     */
    public Path buildExportZip(List<Map<String, Object>> entries, boolean withData) throws Exception {
        Path zipPath = Files.createTempFile("snello-metadata-export-", ".zip");
        List<Map<String, Object>> tables = new ArrayList<>();
        Log.infof("metadata export zip: metadatas=%d withData=%s -> %s", entries.size(), withData, zipPath);
        try (OutputStream fos = Files.newOutputStream(zipPath);
             ZipOutputStream zos = new ZipOutputStream(fos)) {
            backupService.writeZipJson(zos, METADATAS_ENTRY, Map.of(METADATAS, entries));
            if (withData) {
                for (Map<String, Object> entry : entries) {
                    String table = tableName(entry);
                    if (table == null) {
                        continue;
                    }
                    if (MetadataUtils.isReserved(table)) {
                        tables.add(Map.of("name", table, "skipped", true, "reason", "reserved table"));
                        continue;
                    }
                    // a metadata whose table was never created exports with 0 rows instead of failing the zip
                    tables.add(backupService.writeTableData(zos, table, true));
                }
            }
            Map<String, Object> manifest = new LinkedHashMap<>();
            manifest.put("version", 1);
            manifest.put("kind", KIND);
            manifest.put("created_at", Instant.now().toString());
            manifest.put("data", withData);
            manifest.put("tables", tables);
            backupService.writeZipJson(zos, MANIFEST_ENTRY, manifest);
        } catch (Exception e) {
            Files.deleteIfExists(zipPath);
            throw e;
        }
        return zipPath;
    }

    // ------------------------------------------------------------------ import

    /** Peeks the first bytes: true for a ZIP. The stream must support mark/reset (see {@link #buffered}). */
    public static boolean isZip(InputStream in) throws IOException {
        in.mark(ZIP_MAGIC.length);
        byte[] head = in.readNBytes(ZIP_MAGIC.length);
        in.reset();
        if (head.length < ZIP_MAGIC.length) {
            return false;
        }
        for (int i = 0; i < ZIP_MAGIC.length; i++) {
            if (head[i] != ZIP_MAGIC[i]) {
                return false;
            }
        }
        return true;
    }

    public static InputStream buffered(InputStream in) {
        return in.markSupported() ? in : new BufferedInputStream(in);
    }

    /** Unzips an export ZIP into a temp dir and validates it. Caller deletes it with {@link #cleanup(Path)}. */
    public Path unzipImport(InputStream zip) throws Exception {
        Path root = Files.createTempDirectory("snello-metadata-import-");
        try {
            ZipUtils.unzip(zip, root);
            Path manifest = root.resolve(MANIFEST_ENTRY);
            if (Files.isRegularFile(manifest)) {
                @SuppressWarnings("unchecked")
                Map<String, Object> m = mapper.readValue(Files.readAllBytes(manifest), Map.class);
                Object kind = m.get("kind");
                Log.infof("metadata import zip: manifest kind=%s data=%s", kind, m.get("data"));
                if (BackupService.KIND.equals(kind)) {
                    throw new WebApplicationException(
                            "this is a data backup zip (no metadata): import it with POST /api/backup/data",
                            Response.Status.BAD_REQUEST);
                }
                if (kind != null && !KIND.equals(kind)) {
                    throw new WebApplicationException("invalid zip: kind must be " + KIND + ", got " + kind,
                            Response.Status.BAD_REQUEST);
                }
            }
            if (!Files.isRegularFile(root.resolve(METADATAS_ENTRY))) {
                throw new WebApplicationException("invalid zip: missing " + METADATAS_ENTRY, Response.Status.BAD_REQUEST);
            }
            return root;
        } catch (Exception e) {
            cleanup(root);
            throw e;
        }
    }

    public Map<String, Object> readMetadatas(Path root) throws Exception {
        @SuppressWarnings("unchecked")
        Map<String, Object> body = mapper.readValue(Files.readAllBytes(root.resolve(METADATAS_ENTRY)), Map.class);
        return body;
    }

    public void cleanup(Path root) {
        try {
            ZipUtils.deleteRecursively(root);
        } catch (Exception e) {
            Log.warnf(e, "cannot delete temp import dir %s", root);
        }
    }

    /** True when any entry carries rows, inline or as a {@code data/<table>/} dir of the unzipped import. */
    public boolean hasData(List<Map<String, Object>> entries, Path zipRoot) {
        for (Map<String, Object> entry : entries) {
            if (entry.get(DATA_KEY) instanceof List<?> || tableDataDir(zipRoot, tableName(entry)) != null) {
                return true;
            }
        }
        return false;
    }

    /**
     * Imports the rows of freshly imported metadatas. Missing tables are created first (the metadata import alone
     * only registers the schema).
     *
     * @param zipRoot unzipped import, or null for a JSON import (rows inline under {@code data})
     * @return {@code imported}, {@code skipped}, {@code errors}, {@code tables_created}
     */
    public Map<String, Object> importData(List<Map<String, Object>> entries, Path zipRoot) {
        // the metadata import refreshes caches asynchronously: rows need the new metadatas now
        metadataService.invalidateMetadataCaches();
        BackupDataImportResult result = new BackupDataImportResult();
        List<String> tablesCreated = new ArrayList<>();
        for (Map<String, Object> entry : entries) {
            String table = tableName(entry);
            Object inline = entry.get(DATA_KEY);
            Path dir = tableDataDir(zipRoot, table);
            if (table == null || (!(inline instanceof List<?>) && dir == null)) {
                continue;
            }
            Log.infof("metadata import data: table=%s source=%s", table, inline instanceof List<?> ? "json" : "zip");
            if (!ensureTable(entry, table, tablesCreated, result)) {
                continue;
            }
            if (inline instanceof List<?> list) {
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> rows = (List<Map<String, Object>>) list;
                backupService.importRows(table, rows, result);
            } else {
                backupService.importTableDir(table, dir, result);
            }
        }
        if (!tablesCreated.isEmpty()) {
            metadataService.invalidateMetadataCaches(); // created=true flags changed
        }
        Log.infof("metadata import data done: imported=%d skipped=%d errors=%d tablesCreated=%s",
                result.imported, result.skipped, result.errors.size(), tablesCreated);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("imported", result.imported);
        out.put("skipped", result.skipped);
        out.put("errors", result.errors);
        out.put("tables_created", tablesCreated);
        return out;
    }

    private boolean ensureTable(Map<String, Object> entry, String table, List<String> tablesCreated,
                                BackupDataImportResult result) {
        try {
            if (apiService.tableExists(table)) {
                return true;
            }
            String uuid = metadataUuid(entry);
            if (uuid == null) {
                result.errors.add(table + ": table missing and metadata has no uuid, rows not imported");
                return false;
            }
            Log.infof("metadata import data: creating table %s (metadata %s)", table, uuid);
            apiService.createMetadataTable(uuid);
            Map<String, Object> flag = new LinkedHashMap<>();
            flag.put(CREATED, true);
            apiService.merge(METADATAS, flag, uuid, UUID);
            tablesCreated.add(table);
            return true;
        } catch (Exception e) {
            Log.errorf(e, "metadata import data: cannot create table %s", table);
            result.errors.add(table + ": cannot create table (" + e.getMessage() + "), rows not imported");
            return false;
        }
    }

    /** {@code data/<table>} of the unzipped import, or null. Refuses names that would leave the data dir. */
    private static Path tableDataDir(Path zipRoot, String table) {
        if (zipRoot == null || table == null) {
            return null;
        }
        Path dataDir = zipRoot.resolve(DATA_KEY).normalize();
        Path dir = dataDir.resolve(table).normalize();
        if (!dir.getParent().equals(dataDir) || !Files.isDirectory(dir)) {
            return null;
        }
        return dir;
    }

    private static String tableName(Map<String, Object> entry) {
        Object meta = entry.get("metadata");
        Object name = null;
        if (meta instanceof Map<?, ?> m) {
            name = m.get(TABLE_NAME);
        } else if (meta instanceof io.snello.model.Metadata md) {
            name = md.table_name;
        }
        return name == null || name.toString().isBlank() ? null : name.toString();
    }

    private static String metadataUuid(Map<String, Object> entry) {
        if (entry.get("metadata") instanceof Map<?, ?> m && m.get(UUID) != null && !m.get(UUID).toString().isBlank()) {
            return m.get(UUID).toString();
        }
        return null;
    }
}
