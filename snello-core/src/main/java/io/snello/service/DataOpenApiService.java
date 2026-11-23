package io.snello.service;

import io.quarkus.logging.Log;
import io.snello.management.SnelloPlane;
import io.snello.model.FieldDefinition;
import io.snello.model.Metadata;
import io.snello.model.enums.KeyType;
import io.snello.model.enums.WidgetType;
import io.snello.util.FieldDefinitionUtils;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;

/**
 * OpenAPI 3.0 document of the generic data API ({@code /api/{table}}), generated from the metadata catalog: one
 * path set per table, the row schema from the field types, and every list filter the server accepts
 * ({@link io.snello.util.ParamUtils}) spelled out per field and per type. {@code _ne} is declared there but not parsed
 * (a {@code field_ne} key is read as a column name and refused), so it is not offered. The static {@code /openapi} only knows the
 * generic {@code /api/{table}}; this one is what an API consumer (or a client generator) needs.
 * <p>
 * Security follows the server rules: reads are public unless the table is {@code api_protected} (then a bearer token,
 * and with {@code username_field} a plain user sees only their own rows); writes need one of the roles of
 * {@code role-policy1} (Admin / Manager / User by default). Writes are listed only on the control plane.
 */
@ApplicationScoped
public class DataOpenApiService {

    static final String STRING = "string";
    static final String INTEGER = "integer";
    static final String NUMBER = "number";
    static final String BOOLEAN = "boolean";
    static final String DATE = "date";
    static final String DATETIME = "date-time";
    static final String TIME = "time";
    static final String KEYREF = "keyref";

    private static final List<String> STRING_OPS = List.of("", "_contains", "_containss", "_icontains",
            "_ncontains", "_rlike", "_llike", "_in", "_nn", "_inn", "_ie", "_nie");
    private static final List<String> ORDERED_OPS = List.of("", "_lt", "_lte", "_gt", "_gte", "_in", "_nn", "_inn");
    private static final List<String> BOOLEAN_OPS = List.of("", "_nn", "_inn");
    private static final List<String> KEYREF_OPS = List.of("", "_in", "_nn", "_inn");
    private static final Set<String> VALUELESS_OPS = Set.of("_nn", "_inn", "_ie", "_nie");
    private static final Set<String> SINGLE_JOIN_TYPES = Set.of(WidgetType.JOIN.dbValue(), WidgetType.LOOKUP.dbValue(),
            WidgetType.RELATIONSHIP.dbValue(), WidgetType.REALTIONSHIP.dbValue());

    @Inject
    MetadataService metadataService;

    @ConfigProperty(name = SnelloPlane.CONTROL_PLANE_PROPERTY, defaultValue = "true")
    boolean controlPlane;

    @ConfigProperty(name = "quarkus.http.auth.policy.role-policy1.roles-allowed", defaultValue = "Admin,Manager,User")
    String writeRoles;

    @ConfigProperty(name = "quarkus.application.version", defaultValue = "unknown")
    String appVersion;

    @ConfigProperty(name = "quarkus.oidc.auth-server-url")
    Optional<String> oidcServerUrl;

    /** Names of the tables the data API serves, sorted. */
    public List<String> tables() throws Exception {
        return new ArrayList<>(new TreeMap<>(metadataService.metadataMap()).keySet());
    }

    /**
     * Document for the given tables; {@code tables} empty = every table, {@code group} (metadata_group, case-insensitive)
     * narrows further. Returns null when nothing matches.
     */
    public Map<String, Object> document(Collection<String> tables, String group) throws Exception {
        Map<String, Metadata> all = new TreeMap<>(metadataService.metadataMap());
        List<Metadata> selected = new ArrayList<>();
        for (Metadata m : all.values()) {
            boolean byName = tables == null || tables.isEmpty() || tables.contains(m.table_name);
            boolean byGroup = group == null || group.isBlank()
                    || (m.metadata_group != null && m.metadata_group.trim().equalsIgnoreCase(group.trim()));
            if (byName && byGroup) {
                selected.add(m);
            }
        }
        Log.infof("data openapi: tables=%s group=%s -> %d of %d tables (control plane %s, write roles %s)",
                tables, group, selected.size(), all.size(), controlPlane, writeRoles);
        if (selected.isEmpty()) {
            return null;
        }

        Map<String, Object> doc = new LinkedHashMap<>();
        doc.put("openapi", "3.0.3");
        String title = selected.size() == 1 ? "snello data API: " + selected.get(0).table_name
                : group != null && !group.isBlank() ? "snello data API: group " + group.trim() : "snello data API";
        // catalog checksum when loaded (changes with the metadata), else the app version: never "null"
        String catalogVersion = metadataService.version();
        doc.put("info", map("title", title, "version", catalogVersion == null || catalogVersion.isBlank() ? appVersion : catalogVersion,
                "description", infoDescription()));
        // relative: resolved against the URL the document was loaded from (works behind any proxy / host)
        doc.put("servers", List.of(map("url", "/")));

        Map<String, Object> paths = new LinkedHashMap<>();
        Map<String, Object> schemas = new LinkedHashMap<>();
        List<Object> tags = new ArrayList<>();
        for (Metadata m : selected) {
            List<FieldDefinition> fields = fields(m.table_name);
            Log.debugf("data openapi: table %s key=%s(%s) protected=%s fields=%d", m.table_name, m.table_key,
                    m.table_key_type, m.api_protected, fields.size());
            tags.add(map("name", m.table_name, "description", tableDescription(m)));
            schemas.put(schemaName(m.table_name), rowSchema(m, fields, all));
            addPaths(paths, m, fields, all);
        }
        doc.put("tags", tags);
        doc.put("paths", paths);
        doc.put("components", map("schemas", schemas, "securitySchemes", securitySchemes()));
        Log.infof("data openapi: %d paths, %d schemas", paths.size(), schemas.size());
        return doc;
    }

    // ------------------------------------------------------------------ paths

    private void addPaths(Map<String, Object> paths, Metadata m, List<FieldDefinition> fields, Map<String, Metadata> all) {
        String t = m.table_name;
        String ref = "#/components/schemas/" + schemaName(t);
        Map<String, Object> rowRef = map("$ref", ref);
        String keyName = keyName(m);
        Map<String, Object> keyParam = map("name", "key", "in", "path", "required", true,
                "description", "Value of the table key `" + keyName + "` (" + keyTypeOf(m) + ")",
                "schema", map("type", keyIsInteger(m) ? INTEGER : STRING));

        Map<String, Object> collection = new LinkedHashMap<>();
        List<Object> listParams = new ArrayList<>(commonListParams(fields, keyName));
        listParams.addAll(filterParams(fields, keyName, m, all));
        Map<String, Object> list = operation(t, "list" + pascal(t), "List " + t,
                "Paged list. Filters combine with AND; or1./and1. prefixes build OR / AND groups (see the API description)."
                        + readNote(m));
        list.put("parameters", listParams);
        list.put("responses", map(
                "200", map("description", "Rows of the requested page",
                        "headers", map(
                                "x-total-count", map("description", "Rows matching the filters (all pages)", "schema", map("type", INTEGER)),
                                "size", map("description", "Same as x-total-count", "schema", map("type", INTEGER))),
                        "content", json(map("type", "array", "items", rowRef))),
                "400", map("description", "Invalid filter, sort or select_fields"),
                "404", map("description", "Unknown table")));
        secureRead(list, m);
        collection.put("get", list);

        Map<String, Object> item = new LinkedHashMap<>();
        Map<String, Object> fetch = operation(t, "get" + pascal(t), "Get one " + t, "One row by its key." + readNote(m));
        fetch.put("parameters", List.of(keyParam));
        fetch.put("responses", map("200", map("description", "The row", "content", json(rowRef)),
                "404", map("description", "Unknown table or key")));
        secureRead(fetch, m);
        item.put("get", fetch);

        if (controlPlane) {
            Map<String, Object> create = operation(t, "create" + pascal(t), "Create " + t, writeNote(m));
            create.put("requestBody", map("required", true, "content", json(rowRef)));
            create.put("responses", writeResponses(map("200", map("description", "The created row (with its key)", "content", json(rowRef)))));
            secureWrite(create);
            collection.put("post", create);

            Map<String, Object> update = operation(t, "update" + pascal(t), "Update " + t,
                    "Send the fields to change. " + writeNote(m));
            update.put("parameters", List.of(keyParam));
            update.put("requestBody", map("required", true, "content", json(rowRef)));
            update.put("responses", writeResponses(map("200", map("description", "The updated row", "content", json(rowRef)))));
            secureWrite(update);
            item.put("put", update);

            Map<String, Object> delete = operation(t, "delete" + pascal(t), "Delete " + t, writeNote(m));
            delete.put("parameters", List.of(keyParam));
            delete.put("responses", writeResponses(map("200", map("description", "Deleted"))));
            secureWrite(delete);
            item.put("delete", delete);
        }
        paths.put("/api/" + t, collection);
        paths.put("/api/" + t + "/{key}", item);

        // many-to-many fields live in a join table: their rows are a sub-resource, not a column of the row
        for (FieldDefinition fd : fields) {
            if (!FieldDefinitionUtils.isMultiJoin(fd) || fd.join_table_name == null || fd.join_table_name.isBlank()) {
                continue;
            }
            String target = fd.join_table_name;
            Metadata tm = all.get(target);
            Map<String, Object> linked = operation(t, "list" + pascal(t) + pascal(fd.name), "Linked " + target + " (" + label(fd) + ")",
                    "Rows of `" + target + "` linked to this `" + t + "` row through the many-to-many field `" + fd.name
                            + "` (join table `" + t + "_" + target + "`). Same paging and filters as `GET /api/" + target + "`."
                            + (tm != null ? readNote(tm) : ""));
            List<Object> params = new ArrayList<>();
            params.add(keyParam);
            List<FieldDefinition> targetFields = tm == null ? List.of() : safeFields(target);
            params.addAll(commonListParams(targetFields, tm == null ? "uuid" : keyName(tm)));
            Map<String, Object> items = tm != null ? map("$ref", "#/components/schemas/" + schemaName(target)) : map("type", "object");
            linked.put("parameters", params);
            linked.put("responses", map("200", map("description", "Linked rows", "content", json(map("type", "array", "items", items))),
                    "404", map("description", "Unknown table")));
            if (tm != null) {
                secureRead(linked, tm);
            }
            paths.put("/api/" + t + "/{key}/" + target, map("get", linked));
        }
    }

    private List<Object> commonListParams(List<FieldDefinition> fields, String keyName) {
        List<String> names = new ArrayList<>();
        names.add(keyName);
        for (FieldDefinition fd : fields) {
            if (isColumn(fd) && !fd.name.equals(keyName)) {
                names.add(fd.name);
            }
        }
        String sample = names.size() > 1 ? names.get(1) : keyName;
        return List.of(
                map("name", "_limit", "in", "query", "description", "Page size (default 10)",
                        "schema", map("type", INTEGER, "minimum", 0, "default", 10)),
                map("name", "_start", "in", "query", "description", "Rows to skip (default 0)",
                        "schema", map("type", INTEGER, "minimum", 0, "default", 0)),
                map("name", "_sort", "in", "query",
                        "description", "Order: `field:asc` / `field:desc`, comma-separated for several, or `random`, e.g. `"
                                + sample + ":asc`. Fields: "
                                + String.join(", ", names),
                        "schema", map("type", STRING)),
                map("name", "select_fields", "in", "query",
                        "description", "Comma-separated fields to return (default: all), e.g. `"
                                + String.join(",", names.subList(0, Math.min(3, names.size()))) + "`. Fields: " + String.join(", ", names),
                        "schema", map("type", STRING)));
    }

    private List<Object> filterParams(List<FieldDefinition> fields, String keyName, Metadata m, Map<String, Metadata> all) {
        List<Object> params = new ArrayList<>();
        boolean keySeen = false;
        for (FieldDefinition fd : fields) {
            if (!isColumn(fd)) {
                continue;
            }
            keySeen |= fd.name.equals(keyName);
            String kind = kindOf(fd);
            for (String op : opsFor(kind)) {
                params.add(filterParam(fd.name, label(fd), kind, op, fd, m, all));
            }
        }
        if (!keySeen) {
            String kind = keyIsInteger(m) ? INTEGER : STRING;
            for (String op : List.of("", "_in")) {
                params.add(filterParam(keyName, "key", kind, op, null, m, all));
            }
        }
        return params;
    }

    private Map<String, Object> filterParam(String field, String label, String kind, String op, FieldDefinition fd,
                                            Metadata m, Map<String, Metadata> all) {
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("name", field + op);
        p.put("in", "query");
        p.put("description", opDescription(op, label, kind));
        if (VALUELESS_OPS.contains(op)) {
            p.put("allowEmptyValue", true);
            p.put("schema", map("type", STRING));
        } else if (op.equals("_in")) {
            // no `example`: Swagger UI pre-fills examples in "Try it out" and would send every filter at once
            p.put("schema", map("type", STRING));
        } else if (op.startsWith("_") && !ORDERED_OPS.contains(op)) {
            p.put("schema", map("type", STRING)); // text match operators
        } else {
            p.put("schema", valueSchema(kind, fd, m, all, true));
        }
        return p;
    }

    private static List<String> opsFor(String kind) {
        return switch (kind) {
            case INTEGER, NUMBER, DATE, DATETIME, TIME -> ORDERED_OPS;
            case BOOLEAN -> BOOLEAN_OPS;
            case KEYREF -> KEYREF_OPS;
            default -> STRING_OPS;
        };
    }

    private static String opDescription(String op, String label, String kind) {
        String what = label + " (" + (KEYREF.equals(kind) ? "key of the linked row" : kind) + ")";
        return switch (op) {
            case "" -> what + " equals";
            case "_lt" -> what + " is less than";
            case "_lte" -> what + " is less than or equal to";
            case "_gt" -> what + " is greater than";
            case "_gte" -> what + " is greater than or equal to";
            case "_in" -> what + " is one of (comma-separated)";
            case "_contains" -> what + " contains (case as stored: depends on the database collation)";
            case "_containss", "_icontains" -> what + " contains, ignoring case";
            case "_ncontains" -> what + " does not contain";
            case "_rlike" -> what + " starts with, ignoring case";
            case "_llike" -> what + " ends with, ignoring case";
            case "_nn" -> what + " is not null (no value needed)";
            case "_inn" -> what + " is null (no value needed)";
            case "_ie" -> what + " is the empty string (no value needed)";
            case "_nie" -> what + " is not the empty string (no value needed)";
            default -> what + " " + op;
        };
    }

    // ------------------------------------------------------------------ schemas

    private Map<String, Object> rowSchema(Metadata m, List<FieldDefinition> fields, Map<String, Metadata> all) {
        Map<String, Object> props = new LinkedHashMap<>();
        List<String> required = new ArrayList<>();
        String keyName = keyName(m);
        boolean keySeen = false;
        for (FieldDefinition fd : fields) {
            if (!isColumn(fd)) {
                continue;
            }
            Map<String, Object> s = valueSchema(kindOf(fd), fd, m, all, false);
            if (fd.name.equals(keyName)) {
                keySeen = true;
                if (keyGenerated(m)) {
                    s.put("readOnly", true);
                }
            } else if (fd.mandatory || fd.required) {
                required.add(fd.name);
            }
            props.put(fd.name, s);
        }
        if (!keySeen) {
            Map<String, Object> s = map("type", keyIsInteger(m) ? INTEGER : STRING, "description", "Table key (" + keyTypeOf(m) + ")");
            if (keyGenerated(m)) {
                s.put("readOnly", true);
            }
            props.put(keyName, s);
        }
        Map<String, Object> schema = map("type", "object", "title", m.table_name);
        if (m.description != null && !m.description.isBlank()) {
            schema.put("description", m.description);
        }
        schema.put("properties", props);
        if (!required.isEmpty()) {
            schema.put("required", required);
        }
        return schema;
    }

    private Map<String, Object> valueSchema(String kind, FieldDefinition fd, Metadata m, Map<String, Metadata> all, boolean forFilter) {
        Map<String, Object> s = new LinkedHashMap<>();
        switch (kind) {
            case INTEGER -> s.put("type", INTEGER);
            case NUMBER -> s.put("type", NUMBER);
            case BOOLEAN -> s.put("type", BOOLEAN);
            case DATE -> { s.put("type", STRING); s.put("format", "date"); }
            case DATETIME -> { s.put("type", STRING); s.put("format", "date-time"); }
            case TIME -> { s.put("type", STRING); s.put("format", "time"); }
            case KEYREF -> {
                Metadata target = fd == null || fd.join_table_name == null ? null : all.get(fd.join_table_name);
                s.put("type", target != null && keyIsInteger(target) ? INTEGER : STRING);
            }
            default -> s.put("type", STRING);
        }
        if (fd == null) {
            return s;
        }
        if (!forFilter) {
            String d = fieldDescription(fd, kind);
            if (d != null) {
                s.put("description", d);
            }
            if (fd.label != null && !fd.label.isBlank()) {
                s.put("title", fd.label);
            }
        }
        String input = lower(fd.input_type);
        String type = lower(fd.type);
        if (STRING.equals(kind)) {
            if ("email".equals(input)) {
                s.put("format", "email");
            } else if ("url".equals(input)) {
                s.put("format", "uri");
            } else if ("password".equals(input) && !forFilter) {
                s.put("format", "password");
                s.put("writeOnly", true);
            }
            List<String> options = options(fd);
            if (!options.isEmpty() && (WidgetType.SELECT.dbValue().equals(type) || WidgetType.RADIOBUTTON.dbValue().equals(type))) {
                s.put("enum", options);
            }
        }
        if (!forFilter && fd.input_disabled) {
            s.put("readOnly", true);
        }
        if (!forFilter && fd.default_value != null && !fd.default_value.isBlank() && STRING.equals(s.get("type"))) {
            s.put("default", fd.default_value);
        }
        return s;
    }

    private static String fieldDescription(FieldDefinition fd, String kind) {
        List<String> parts = new ArrayList<>();
        if (fd.description != null && !fd.description.isBlank()) {
            parts.add(fd.description.trim());
        }
        if (KEYREF.equals(kind) && fd.join_table_name != null) {
            parts.add("Key of a `" + fd.join_table_name + "` row (" + (fd.join_table_key == null ? "its key" : "`" + fd.join_table_key + "`")
                    + "): GET /api/" + fd.join_table_name + "/{key}");
        }
        String type = lower(fd.type);
        if (WidgetType.TINYMCE.dbValue().equals(type)) {
            parts.add("HTML");
        } else if (WidgetType.MONACO.dbValue().equals(type)) {
            parts.add("Code / text");
        } else if (WidgetType.IMAGE.dbValue().equals(type) || WidgetType.MEDIA.dbValue().equals(type)) {
            parts.add("Document reference (see /api/documents)");
        } else if (WidgetType.TAGS.dbValue().equals(type)) {
            parts.add("Comma-separated tags");
        }
        return parts.isEmpty() ? null : String.join(". ", parts);
    }

    /** Value kind of a field: widget type first (what the admin shows), then input type, then the SQL type. */
    static String kindOf(FieldDefinition fd) {
        String type = lower(fd.type);
        String input = lower(fd.input_type);
        String sql = lower(fd.sql_type);
        if (SINGLE_JOIN_TYPES.contains(type)) {
            return KEYREF;
        }
        if (WidgetType.CHECKBOX.dbValue().equals(type) || "checkbox".equals(input)) {
            return BOOLEAN;
        }
        if (WidgetType.DATETIME.dbValue().equals(type) || "datetime-local".equals(input)) {
            return DATETIME;
        }
        if (WidgetType.DATE.dbValue().equals(type) || "date".equals(input)) {
            return DATE;
        }
        if (WidgetType.TIME.dbValue().equals(type) || "time".equals(input)) {
            return TIME;
        }
        if (sql.matches(".*\\b(tinyint|smallint|int|integer|bigint|serial|bigserial)\\b.*")) {
            return INTEGER;
        }
        if (sql.matches(".*\\b(decimal|numeric|float|double|real|money)\\b.*") || "decimal".equals(input)) {
            return NUMBER;
        }
        if ("number".equals(input) || "range".equals(input)) {
            return NUMBER;
        }
        if (sql.matches(".*\\b(bit|bool|boolean)\\b.*")) {
            return BOOLEAN;
        }
        if (sql.matches(".*\\b(datetime2?|timestamp|datetimeoffset)\\b.*")) {
            return DATETIME;
        }
        if (sql.matches(".*\\bdate\\b.*")) {
            return DATE;
        }
        return STRING;
    }

    /** A real column of the row: not a button, not a many-to-many (join table) field. */
    private static boolean isColumn(FieldDefinition fd) {
        return fd != null && fd.name != null && !fd.name.isBlank()
                && !WidgetType.BUTTON.dbValue().equals(lower(fd.type))
                && !FieldDefinitionUtils.isMultiJoin(fd);
    }

    private static List<String> options(FieldDefinition fd) {
        if (fd.options == null || fd.options.isBlank()) {
            return List.of();
        }
        return Arrays.stream(fd.options.split(",")).map(String::trim).filter(s -> !s.isEmpty()).distinct().toList();
    }

    // ------------------------------------------------------------------ security

    private Map<String, Object> securitySchemes() {
        Map<String, Object> schemes = new LinkedHashMap<>();
        schemes.put("bearerAuth", map("type", "http", "scheme", "bearer", "bearerFormat", "JWT",
                "description", "Keycloak access token (Authorization: Bearer ...)"));
        oidcServerUrl.map(String::trim).filter(s -> s.startsWith("http")).ifPresent(url ->
                schemes.put("oidc", map("type", "openIdConnect",
                        "openIdConnectUrl", url.replaceAll("/+$", "") + "/.well-known/openid-configuration")));
        return schemes;
    }

    private List<Object> securityRequirement() {
        List<Object> req = new ArrayList<>();
        req.add(map("bearerAuth", List.of()));
        if (oidcServerUrl.map(String::trim).filter(s -> s.startsWith("http")).isPresent()) {
            req.add(map("oidc", List.of()));
        }
        return req;
    }

    private void secureRead(Map<String, Object> op, Metadata m) {
        if (!m.api_protected) {
            return; // public read: no security requirement
        }
        op.put("security", securityRequirement());
        @SuppressWarnings("unchecked")
        Map<String, Object> responses = (Map<String, Object>) op.get("responses");
        responses.put("401", map("description", "Login required (api protected table)"));
        if (hasOwner(m)) {
            responses.put("403", map("description", "Row of another user"));
        }
    }

    private void secureWrite(Map<String, Object> op) {
        op.put("security", securityRequirement());
    }

    private static Map<String, Object> writeResponses(Map<String, Object> ok) {
        Map<String, Object> r = new LinkedHashMap<>(ok);
        r.put("400", map("description", "Invalid row (validation)"));
        r.put("401", map("description", "Login required"));
        r.put("403", map("description", "Role not allowed, or row of another user"));
        r.put("404", map("description", "Unknown table or key"));
        return r;
    }

    private String readNote(Metadata m) {
        if (!m.api_protected) {
            return " Public: no login needed.";
        }
        return hasOwner(m)
                ? " Login required. A user with only the User role sees just their own rows (`" + m.username_field
                + "` = their username); Admin and Manager see all."
                : " Login required.";
    }

    private String writeNote(Metadata m) {
        String roles = String.join(", ", roles());
        String own = m.api_protected && hasOwner(m)
                ? " A user without Admin / Manager writes only their own rows: `" + m.username_field + "` is set to their username."
                : "";
        return "Login required, role: " + roles + "." + own;
    }

    private List<String> roles() {
        return Arrays.stream(writeRoles.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList();
    }

    private static boolean hasOwner(Metadata m) {
        return m.username_field != null && !m.username_field.isBlank();
    }

    // ------------------------------------------------------------------ helpers

    private List<FieldDefinition> fields(String table) throws Exception {
        List<FieldDefinition> list = metadataService.fielddefinitions(table);
        if (list == null) {
            return List.of();
        }
        List<FieldDefinition> sorted = new ArrayList<>(list);
        sorted.sort((a, b) -> Integer.compare(a.view_index, b.view_index));
        return sorted;
    }

    private List<FieldDefinition> safeFields(String table) {
        try {
            return fields(table);
        } catch (Exception e) {
            Log.warnf(e, "data openapi: fields of %s not readable", table);
            return List.of();
        }
    }

    private static String keyName(Metadata m) {
        return m.table_key == null || m.table_key.isBlank() ? "uuid" : m.table_key;
    }

    private static String keyTypeOf(Metadata m) {
        return m.table_key_type == null || m.table_key_type.isBlank() ? KeyType.UUID.dbValue() : m.table_key_type.toLowerCase(Locale.ROOT);
    }

    private static boolean keyIsInteger(Metadata m) {
        return KeyType.AUTOINCREMENT.dbValue().equals(keyTypeOf(m));
    }

    /** uuid / slug / autoincrement keys are set by the server; userdefined ones are sent by the client. */
    private static boolean keyGenerated(Metadata m) {
        return !KeyType.USERDEFINED.dbValue().equals(keyTypeOf(m));
    }

    private static String tableDescription(Metadata m) {
        List<String> parts = new ArrayList<>();
        if (m.description != null && !m.description.isBlank()) {
            parts.add(m.description.trim());
        }
        if (m.metadata_group != null && !m.metadata_group.isBlank()) {
            parts.add("Group: " + m.metadata_group.trim());
        }
        return String.join(". ", parts);
    }

    private static String label(FieldDefinition fd) {
        return fd.label != null && !fd.label.isBlank() ? fd.label.trim() : fd.name;
    }

    private Map<String, Object> operation(String tag, String operationId, String summary, String description) {
        Map<String, Object> op = new LinkedHashMap<>();
        op.put("tags", List.of(tag));
        op.put("operationId", operationId);
        op.put("summary", summary);
        op.put("description", description.trim());
        return op;
    }

    private static Map<String, Object> json(Map<String, Object> schema) {
        return map("application/json", map("schema", schema));
    }

    static String schemaName(String table) {
        return table.replaceAll("[^A-Za-z0-9._-]", "_");
    }

    static String pascal(String s) {
        StringBuilder b = new StringBuilder();
        for (String part : s.split("[^A-Za-z0-9]+")) {
            if (!part.isEmpty()) {
                b.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
            }
        }
        return b.toString();
    }

    private static String lower(String s) {
        return s == null ? "" : s.trim().toLowerCase(Locale.ROOT);
    }

    private static String infoDescription() {
        return """
                Data API generated from the snello metadata: one path set per table, row schema from the field types.

                **Reads** (`GET`) are public unless the table is *api protected*. **Writes** (`POST`, `PUT`, `DELETE`) need a \
                Keycloak bearer token.

                **Lists**: `_limit` (default 10) and `_start` page the rows; the `x-total-count` header holds the total. \
                `_sort=field:asc` (comma-separated for several, or `random`), `select_fields=a,b`.

                **Filters**: `field=value` (equals) or `field<op>=value`, combined with AND:

                | op | meaning | types |
                |----|---------|-------|
                | `_lt` `_lte` `_gt` `_gte` | less / greater (or equal) | numbers, dates, times |
                | `_in` | one of, comma-separated | all |
                | `_contains` / `_ncontains` | contains / does not contain | text |
                | `_containss`, `_icontains` | contains, ignoring case | text |
                | `_rlike` / `_llike` | starts / ends with, ignoring case | text |
                | `_nn` / `_inn` | not null / null (no value) | all |
                | `_nie` / `_ie` | not empty / empty string (no value) | text |

                **Groups**: prefix filters with `or1.` (any of them) or `and1.` (all of them), e.g. \
                `or1.name_containss=ros&or1.surname_containss=ros`; one level, numbered groups (`or2.`, `and3.` ...).
                """;
    }

    private static Map<String, Object> map(Object... kv) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) {
            m.put((String) kv[i], kv[i + 1]);
        }
        return m;
    }
}
