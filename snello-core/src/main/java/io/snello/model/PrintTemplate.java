package io.snello.model;

import io.quarkus.runtime.annotations.RegisterForReflection;
import io.snello.model.pojo.RowProtection;
import io.snello.util.MapReads;
import io.snello.util.MapWrites;

import java.util.Map;
import java.util.Set;

/**
 * A printable report: {@code sql_query} gives the rows, the body turns them into a file.
 * <ul>
 *     <li>{@code pdf} / {@code html}: {@code html_body} is a Qute template ({@code {#for row in rows}{row.name}{/for}})</li>
 *     <li>{@code csv}: {@code html_body} is a Qute text template; empty = automatic CSV of every column</li>
 *     <li>{@code xlsx}: {@code template_file} is a JXLS xlsx template (base64), {@code template_file_name} its name</li>
 * </ul>
 * Request parameters filter the query result by column alias ({@code ?city=Rome&price_gte=10}), like a
 * with_params SelectQuery. {@code api_protected} + {@code username_field}: same row-level security as SelectQuery.
 */
@RegisterForReflection
public class PrintTemplate {

    public static final String PDF = "pdf";
    public static final String HTML = "html";
    public static final String CSV = "csv";
    public static final String XLSX = "xlsx";
    public static final Set<String> EXPORT_TYPES = Set.of(PDF, HTML, CSV, XLSX);

    public String uuid;
    public String name;
    public String description;
    public String export_type;
    public String sql_query;
    public String html_body;
    public String template_file;
    public String template_file_name;
    public boolean api_protected;
    public String username_field;

    public PrintTemplate() {
    }

    public PrintTemplate(Map<String, Object> map) {
        fromMap(map, this);
    }

    public PrintTemplate fromMap(Map<String, Object> map, PrintTemplate target) {
        if (map == null || target == null) {
            return target;
        }
        target.uuid = MapReads.str(map, "uuid");
        target.name = MapReads.str(map, "name");
        target.description = MapReads.str(map, "description");
        target.export_type = MapReads.str(map, "export_type");
        target.sql_query = MapReads.str(map, "sql_query");
        target.html_body = MapReads.str(map, "html_body");
        target.template_file = MapReads.str(map, "template_file");
        target.template_file_name = MapReads.str(map, "template_file_name");
        target.api_protected = MapReads.bool(map, "api_protected", false);
        target.username_field = MapReads.str(map, "username_field");
        return target;
    }

    public Map<String, Object> toMap() {
        Map<String, Object> map = MapWrites.map();
        MapWrites.put(map, "uuid", uuid);
        MapWrites.put(map, "name", name);
        MapWrites.put(map, "description", description);
        MapWrites.put(map, "export_type", export_type);
        MapWrites.put(map, "sql_query", sql_query);
        MapWrites.put(map, "html_body", html_body);
        MapWrites.put(map, "template_file", template_file);
        MapWrites.put(map, "template_file_name", template_file_name);
        MapWrites.putBool(map, "api_protected", api_protected);
        MapWrites.put(map, "username_field", username_field);
        return map;
    }

    public RowProtection rowProtection() {
        return new RowProtection(api_protected, username_field);
    }

    /** The query as a with_params SelectQuery, so it runs through the same filter / owner-scope code. */
    public SelectQuery asSelectQuery() {
        SelectQuery query = new SelectQuery(name, sql_query, null, true);
        query.api_protected = api_protected;
        query.username_field = username_field;
        return query;
    }

    @Override
    public String toString() {
        return "PrintTemplate{" +
               "uuid='" + uuid + '\'' +
               ", name='" + name + '\'' +
               ", export_type='" + export_type + '\'' +
               ", api_protected=" + api_protected +
               ", username_field='" + username_field + '\'' +
               ", template_file_name='" + template_file_name + '\'' +
               '}';
    }
}
