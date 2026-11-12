package io.snello.service;

import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import io.quarkus.logging.Log;
import io.quarkus.qute.Engine;
import io.quarkus.qute.Template;
import io.quarkus.qute.Variant;
import io.snello.api.service.JdbcRepository;
import io.snello.model.PrintTemplate;
import io.snello.model.pojo.PrintOutput;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.core.MultivaluedHashMap;
import jakarta.ws.rs.core.MultivaluedMap;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jsoup.Jsoup;
import org.jsoup.helper.W3CDom;
import org.jxls.transform.poi.JxlsPoiTemplateFillerBuilder;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static io.snello.management.AppConstants.PRINT_TEMPLATES;

/**
 * Renders {@link PrintTemplate}s. The rows come from {@link ApiService#listSelectQuery}, so filters, sort and the
 * owner scope behave exactly as for a with_params SelectQuery.
 * <p>
 * Template data (Qute and JXLS): {@code rows} (list of column-alias → value maps), {@code params} (first value of
 * each request parameter), {@code template} (name), {@code count}, {@code now} ({@code yyyy-MM-dd HH:mm}).
 */
@ApplicationScoped
public class PrintTemplateService {

    @Inject
    JdbcRepository jdbcRepository;

    @Inject
    ApiService apiService;

    @Inject
    Engine engine;

    /** Upper bound of rows a print may read; protects the server from a forgotten filter. */
    @ConfigProperty(name = "snello.print.max-rows", defaultValue = "50000")
    int maxRows;

    public Map<String, Object> findRow(String uuid) throws Exception {
        Map<String, Object> row = jdbcRepository.fetch(null, PRINT_TEMPLATES, "uuid", uuid);
        return row == null || row.isEmpty() ? null : row;
    }

    public PrintTemplate findByName(String name) throws Exception {
        MultivaluedMap<String, String> byName = new MultivaluedHashMap<>();
        byName.putSingle("name", name);
        List<Map<String, Object>> rows = jdbcRepository.list("SELECT * FROM " + PRINT_TEMPLATES, byName, null, null, 0, 0);
        Log.debugf("print template lookup name=%s -> %d row(s)", name, rows.size());
        return rows.isEmpty() ? null : new PrintTemplate(rows.get(0));
    }

    /**
     * Script entry point ({@code prints} binding):
     * {@code prints.print('events-year', {begin_date_gte: '2026-01-01'}, 'begin_date:asc')}.
     * Scripts cannot reach CDI beans or MicroProfile Config by class name (the script class loader resolves other
     * copies of the classes), so this binding is the way to render a template from an Action or Scheduled Action.
     *
     * @param params column filters, as the print endpoint query parameters (a JS object is a Map); may be null
     * @param sort   {@code _sort} value, e.g. {@code begin_date:asc}; may be null
     * @return bytes(), contentType(), fileName(), rows()
     */
    public PrintOutput print(String name, Map<String, ?> params, String sort) throws Exception {
        PrintTemplate template = findByName(name);
        if (template == null) {
            throw new IllegalArgumentException("print template not found: " + name);
        }
        MultivaluedMap<String, String> filters = new MultivaluedHashMap<>();
        if (params != null) {
            params.forEach((k, v) -> {
                if (v != null) {
                    filters.putSingle(k, v.toString());
                }
            });
        }
        Log.infof("PRINT [%s] from script, params=%s sort=%s", name, filters, sort);
        return print(template, filters, sort, null);
    }

    /**
     * @param filters request parameters (column filters, plus the owner value already bound for non-admin callers)
     * @param sort    {@code _sort} value or null
     */
    public PrintOutput print(PrintTemplate template, MultivaluedMap<String, String> filters, String sort,
                             String csvSeparator) throws Exception {
        long start = System.currentTimeMillis();
        String type = template.export_type == null ? PrintTemplate.PDF : template.export_type.toLowerCase();
        Log.infof("PRINT [%s] type=%s filters=%s sort=%s", template.name, type, filters, sort);
        List<Map<String, Object>> rows = apiService.listSelectQuery(template.name, template.asSelectQuery(), filters,
                sort, maxRows, 0);
        if (rows.size() >= maxRows) {
            Log.warnf("PRINT [%s] hit snello.print.max-rows=%d, output is truncated", template.name, maxRows);
        }
        Map<String, Object> data = data(template, rows, filters);
        PrintOutput out = switch (type) {
            case PrintTemplate.PDF -> new PrintOutput(pdf(html(template, data)), "application/pdf",
                    fileName(template, "pdf"), rows.size());
            case PrintTemplate.HTML -> new PrintOutput(html(template, data).getBytes(StandardCharsets.UTF_8),
                    "text/html; charset=UTF-8", fileName(template, "html"), rows.size());
            case PrintTemplate.CSV -> new PrintOutput(csv(template, data, rows, csvSeparator).getBytes(StandardCharsets.UTF_8),
                    "text/csv; charset=UTF-8", fileName(template, "csv"), rows.size());
            case PrintTemplate.XLSX -> new PrintOutput(xlsx(template, data),
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", fileName(template, "xlsx"),
                    rows.size());
            default -> throw new IllegalArgumentException("unknown export_type " + type);
        };
        Log.infof("PRINT [%s] %s: %d row(s), %d bytes in %d ms", template.name, type, rows.size(), out.bytes().length,
                System.currentTimeMillis() - start);
        return out;
    }

    private Map<String, Object> data(PrintTemplate template, List<Map<String, Object>> rows,
                                     MultivaluedMap<String, String> filters) {
        Map<String, String> params = new LinkedHashMap<>();
        if (filters != null) {
            filters.forEach((k, v) -> params.put(k, v == null || v.isEmpty() ? null : v.get(0)));
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("rows", rows);
        data.put("params", params);
        data.put("template", template.name);
        data.put("count", rows.size());
        data.put("now", LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")));
        return data;
    }

    /** Qute with HTML escaping: values from the database cannot inject markup. */
    String html(PrintTemplate template, Map<String, Object> data) {
        if (template.html_body == null || template.html_body.isBlank()) {
            throw new IllegalArgumentException("html_body is empty");
        }
        Template qute = engine.parse(template.html_body, Variant.forContentType(Variant.TEXT_HTML));
        return qute.data(data).render();
    }

    byte[] pdf(String html) throws Exception {
        // jsoup accepts real-world HTML (unclosed tags, no XHTML); openhtmltopdf wants a W3C DOM
        org.w3c.dom.Document doc = new W3CDom().fromJsoup(Jsoup.parse(html));
        ByteArrayOutputStream os = new ByteArrayOutputStream();
        PdfRendererBuilder builder = new PdfRendererBuilder();
        builder.useFastMode();
        builder.withW3cDocument(doc, "/");
        builder.toStream(os);
        builder.run();
        return os.toByteArray();
    }

    String csv(PrintTemplate template, Map<String, Object> data, List<Map<String, Object>> rows, String separator) {
        if (template.html_body != null && !template.html_body.isBlank()) {
            Log.debugf("PRINT [%s] csv from Qute body", template.name);
            return engine.parse(template.html_body).data(data).render();
        }
        String sep = separator == null || separator.isEmpty() ? "," : separator;
        Log.debugf("PRINT [%s] automatic csv, separator='%s'", template.name, sep);
        StringBuilder sb = new StringBuilder();
        List<String> columns = rows.isEmpty() ? List.of() : new ArrayList<>(rows.get(0).keySet());
        appendCsvLine(sb, new ArrayList<>(columns), sep);
        for (Map<String, Object> row : rows) {
            List<Object> values = new ArrayList<>(columns.size());
            for (String c : columns) {
                values.add(row.get(c));
            }
            appendCsvLine(sb, values, sep);
        }
        return sb.toString();
    }

    private static void appendCsvLine(StringBuilder sb, List<?> values, String sep) {
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) {
                sb.append(sep);
            }
            Object v = values.get(i);
            String s = v == null ? "" : v.toString();
            if (s.contains(sep) || s.contains("\"") || s.contains("\n") || s.contains("\r")) {
                s = "\"" + s.replace("\"", "\"\"") + "\"";
            }
            sb.append(s);
        }
        sb.append("\r\n");
    }

    byte[] xlsx(PrintTemplate template, Map<String, Object> data) {
        if (template.template_file == null || template.template_file.isBlank()) {
            throw new IllegalArgumentException("xlsx export needs template_file (a JXLS .xlsx template)");
        }
        byte[] xlsxTemplate = decodeTemplateFile(template.template_file);
        Log.debugf("PRINT [%s] JXLS template %s, %d bytes", template.name, template.template_file_name,
                xlsxTemplate.length);
        return JxlsPoiTemplateFillerBuilder.newInstance()
                .withTemplate(new ByteArrayInputStream(xlsxTemplate))
                .buildAndFill(data);
    }

    /** Accepts plain base64 or a data URL ({@code data:...;base64,....}) as sent by a browser FileReader. */
    public static byte[] decodeTemplateFile(String value) {
        String b64 = value.trim();
        int comma = b64.indexOf(',');
        if (b64.startsWith("data:") && comma > 0) {
            b64 = b64.substring(comma + 1);
        }
        try {
            return Base64.getMimeDecoder().decode(b64);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("template_file is not valid base64: " + e.getMessage(), e);
        }
    }

    private static String fileName(PrintTemplate template, String ext) {
        String base = template.name == null ? "print" : template.name.replaceAll("[^A-Za-z0-9_.-]", "_");
        return base + "." + ext;
    }
}
