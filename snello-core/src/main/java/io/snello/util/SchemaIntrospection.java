package io.snello.util;

import io.snello.model.enums.SemanticType;
import io.snello.model.pojo.DbColumn;
import io.snello.model.pojo.DbForeignKey;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Pure heuristics for turning physical schema (+ optional row samples) into semantic types and join targets.
 * Used by {@link io.snello.service.IntrospectService}; no JDBC here.
 */
public final class SchemaIntrospection {

    /** Varchar columns at or above this size default to textarea unless HTML is detected. */
    public static final int LONG_VARCHAR_THRESHOLD = 255;
    /** A wide varchar is a textarea only when its values really are long: snello itself uses varchar(2048) for references. */
    static final int LONG_VALUE_LENGTH = 150;
    public static final int ENUM_VALUE_MAX_LENGTH = 40;
    static final int SLUG_SOURCE_MAX_LENGTH = 300;
    public static final int ENUM_MIN_ROWS = 5;

    private static final Pattern HTML_TAG = Pattern.compile(
            "(?i)<\\s*(p|div|br|span|table|ul|ol|li|h[1-6]|a|strong|em|img|section|article)\\b");

    private static final Pattern UUID_VALUE = Pattern.compile(
            "(?i)^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$");

    private SchemaIntrospection() {
    }

    /**
     * Resolves primary key strategy for an existing table.
     *
     * @return key column name + type ({@code uuid}|{@code autoincrement}|{@code userdefined})
     */
    /** Column names a human reads to recognise a row, in preference order. */
    private static final List<String> LABEL_COLUMNS = List.of(
            "name", "title", "label", "username", "display_name", "full_name", "code", "email", "description");

    /**
     * Column a join widget shows for a row of {@code columns} (→ {@code join_table_select_fields}).
     * Preferred names first, then the first short text column that is not the key, then the key itself.
     */
    public static String guessLabelColumn(List<DbColumn> columns, String keyColumn) {
        Set<String> names = new HashSet<>();
        for (DbColumn c : columns) {
            names.add(c.name().toLowerCase(Locale.ROOT));
        }
        for (String preferred : LABEL_COLUMNS) {
            if (names.contains(preferred) && !preferred.equalsIgnoreCase(keyColumn)) {
                return preferred;
            }
        }
        for (DbColumn c : columns) {
            String type = c.typeName() == null ? "" : c.typeName().toLowerCase(Locale.ROOT);
            if (type.contains("char") && !c.name().equalsIgnoreCase(keyColumn)) {
                return c.name();
            }
        }
        return keyColumn;
    }

    /** Column-name words that mark a human-readable identifier: searched and shown in lists by default. */
    private static final Set<String> LABEL_TOKENS = Set.of(
            "name", "title", "label", "code", "username", "email", "surname", "sku");

    /** Columns that hold a manual ordering. */
    private static final List<String> POSITION_COLUMNS = List.of(
            "position", "sort", "sort_order", "ordine", "ordering", "order_index", "sort_index");

    /** Columns a list is naturally sorted by, alphabetically. Narrower than {@link #LABEL_COLUMNS}. */
    private static final List<String> ORDER_LABEL_COLUMNS = List.of(
            "name", "title", "label", "username", "display_name", "full_name", "code");

    /** Creation / update timestamps: newest first when nothing better exists; never listed by default. */
    private static final List<String> AUDIT_COLUMNS = List.of(
            "created_at", "creation_date", "created", "inserted_at", "updated_at", "lastupdate_date", "updated",
            "modified_at", "created_by", "updated_by");

    /** Column-name words that point at an uploaded file. */
    private static final Set<String> MEDIA_TOKENS = Set.of(
            "image", "photo", "picture", "logo", "avatar", "thumbnail");

    /** {@code name_of_start_point}, {@code action_username}: any word of the column name is a label word. */
    public static boolean isLabelLike(String columnName) {
        if (columnName == null) {
            return false;
        }
        for (String token : columnName.toLowerCase(Locale.ROOT).split("_")) {
            if (LABEL_TOKENS.contains(token)) {
                return true;
            }
        }
        return false;
    }

    public static boolean isAuditColumn(String columnName) {
        return columnName != null && AUDIT_COLUMNS.contains(columnName.toLowerCase(Locale.ROOT));
    }

    /**
     * Suggested {@code order_by}: manual position → label column A-Z → newest first → none.
     * The schema does not say it, so this is a guess to review.
     */
    public static String guessOrderBy(List<DbColumn> columns, String keyColumn) {
        Map<String, DbColumn> byName = index(columns);
        for (String position : POSITION_COLUMNS) {
            if (byName.containsKey(position)) {
                return position + " asc";
            }
        }
        for (String label : ORDER_LABEL_COLUMNS) {
            if (byName.containsKey(label) && !label.equalsIgnoreCase(keyColumn)) {
                return label + " asc";
            }
        }
        for (String audit : AUDIT_COLUMNS) {
            DbColumn column = byName.get(audit);
            if (column != null) {
                SemanticType type = FieldUiDerivation.semanticTypeFromSqlType(column.sqlType());
                if (type == SemanticType.DATE || type == SemanticType.DATETIME) {
                    return audit + " desc";
                }
            }
        }
        return null;
    }

    /**
     * Column the key is a slug of ({@code table_key_type=slug} + {@code table_key_addition}), found by slugifying
     * each text column of the sample rows and comparing with the key ({@code my-title} or {@code my-title-2}).
     * Empty when keys are not slugs of any column.
     */
    public static Optional<String> guessSlugSource(List<Map<String, Object>> sampleRows, String keyColumn,
                                                   List<DbColumn> columns) {
        if (sampleRows == null || sampleRows.isEmpty() || keyColumn == null) {
            return Optional.empty();
        }
        String best = null;
        int bestRatio = 0;
        int bestRank = Integer.MAX_VALUE;
        for (DbColumn column : columns) {
            String name = column.name().toLowerCase(Locale.ROOT);
            SemanticType type = FieldUiDerivation.semanticTypeFromSqlType(column.sqlType());
            if (name.equalsIgnoreCase(keyColumn) || type != SemanticType.TEXT && type != SemanticType.LONG_TEXT) {
                continue;
            }
            int total = 0;
            int matches = 0;
            for (Map<String, Object> row : sampleRows) {
                Object key = row.get(keyColumn);
                Object value = row.get(name);
                // titles are sometimes declared as text; bodies are never a slug source
                if (key == null || value == null || String.valueOf(value).isBlank()
                        || String.valueOf(value).length() > SLUG_SOURCE_MAX_LENGTH) {
                    continue;
                }
                total++;
                String slug = TableKeyUtils.createSlug(String.valueOf(value));
                String k = String.valueOf(key);
                if (!slug.isEmpty() && (k.equals(slug) || k.matches(Pattern.quote(slug) + "-\\d+"))) {
                    matches++;
                }
            }
            if (matches == 0) {
                continue;
            }
            int ratio = matches * 100 / total;
            int rank = LABEL_COLUMNS.indexOf(name) < 0 ? LABEL_COLUMNS.size() : LABEL_COLUMNS.indexOf(name);
            if (ratio >= 60 && (ratio > bestRatio || ratio == bestRatio && rank < bestRank)) {
                best = name;
                bestRatio = ratio;
                bestRank = rank;
            }
        }
        return Optional.ofNullable(best);
    }

    /**
     * True for a column whose values are a small closed set: at least 2 and at most {@code maxDistinct} short values
     * without commas (options are stored comma-separated), each used on average twice or more.
     */
    public static boolean looksLikeEnum(Map<String, Long> valueCounts, int maxDistinct) {
        if (valueCounts == null || valueCounts.size() < 2 || valueCounts.size() > maxDistinct) {
            return false;
        }
        long rows = 0;
        for (Map.Entry<String, Long> e : valueCounts.entrySet()) {
            if (e.getKey().length() > ENUM_VALUE_MAX_LENGTH || e.getKey().contains(",")) {
                return false;
            }
            rows += e.getValue();
        }
        return rows >= ENUM_MIN_ROWS && valueCounts.size() * 2L <= rows;
    }

    public static KeyGuess guessKey(List<String> primaryKeys, List<DbColumn> columns) {
        Map<String, DbColumn> byName = index(columns);
        if (primaryKeys != null && primaryKeys.size() == 1) {
            String pk = primaryKeys.getFirst().toLowerCase(Locale.ROOT);
            DbColumn col = byName.get(pk);
            if (col != null && col.autoIncrement()) {
                return new KeyGuess(pk, "autoincrement", false);
            }
            if ("uuid".equals(pk)) {
                return new KeyGuess("uuid", "uuid", false);
            }
            String sql = col == null ? "" : col.sqlType();
            if (looksLikeUuidColumn(pk, sql)) {
                return new KeyGuess(pk, "uuid", false);
            }
            return new KeyGuess(pk, "userdefined", true);
        }
        if (primaryKeys != null && primaryKeys.size() > 1) {
            String first = primaryKeys.getFirst().toLowerCase(Locale.ROOT);
            return new KeyGuess(first, "userdefined", true);
        }
        for (String candidate : List.of("uuid", "id")) {
            if (byName.containsKey(candidate)) {
                DbColumn col = byName.get(candidate);
                if (col.autoIncrement() || "id".equals(candidate)) {
                    return new KeyGuess(candidate, col.autoIncrement() ? "autoincrement" : "userdefined",
                            !col.autoIncrement());
                }
                return new KeyGuess(candidate, "uuid".equals(candidate) ? "uuid" : "userdefined",
                        !"uuid".equals(candidate));
            }
        }
        if (!columns.isEmpty()) {
            String name = columns.getFirst().name().toLowerCase(Locale.ROOT);
            return new KeyGuess(name, "userdefined", true);
        }
        return new KeyGuess("uuid", "uuid", false);
    }

    /** Best-effort semantic type from column metadata + optional sample values for that column. */
    public static SemanticType semanticFor(DbColumn column, Collection<Object> sampleValues) {
        String name = column.name() == null ? "" : column.name().toLowerCase(Locale.ROOT);
        String sql = column.sqlType();
        if (name.contains("password") || name.contains("passwd")) {
            return SemanticType.PASSWORD;
        }
        if (name.contains("email")) {
            return SemanticType.EMAIL;
        }
        SemanticType fromSql = FieldUiDerivation.semanticTypeFromSqlType(sql);
        boolean stringColumn = fromSql == null || fromSql == SemanticType.TEXT || fromSql == SemanticType.LONG_TEXT;
        if (stringColumn && isLongSqlText(sql) && looksLikeHtml(sampleValues)) {
            return SemanticType.HTML;
        }
        if (name.endsWith("_url") || name.equals("url") || name.equals("website")) {
            // a text column named website holds a URL, but it is edited as free text when declared as text/clob
            return isLongSqlText(sql) ? SemanticType.LONG_TEXT : SemanticType.URL;
        }
        if (fromSql == SemanticType.BOOLEAN || fromSql == SemanticType.DATE || fromSql == SemanticType.DATETIME
                || fromSql == SemanticType.TIME || fromSql == SemanticType.INTEGER || fromSql == SemanticType.DECIMAL) {
            return fromSql;
        }
        if (stringColumn && name.equals("tags")) {
            return SemanticType.TAGS;
        }
        if (stringColumn && hasMediaName(name) && (!hasValues(sampleValues) || allUuidValues(sampleValues))) {
            return SemanticType.MEDIA;
        }

        if (isLongSqlText(sql)) {
            return SemanticType.LONG_TEXT;
        }
        if (isWideVarchar(sql, column.size())) {
            if (looksLikeHtml(sampleValues)) {
                return SemanticType.HTML;
            }
            // without data the declared width is all there is; with data, only really long values need a textarea
            return !hasValues(sampleValues) || hasLongValue(sampleValues) ? SemanticType.LONG_TEXT : SemanticType.TEXT;
        }

        if (fromSql == SemanticType.TEXT || fromSql == null) {
            if (looksLikeHtml(sampleValues)) {
                return SemanticType.HTML;
            }
            return SemanticType.TEXT;
        }
        return fromSql;
    }

    /**
     * Obvious many-to-one target for a column, or empty when not confident.
     * Preference: JDBC FK → name {@code *_id}/{@code *_uuid} matching a known table.
     */
    public static Optional<JoinGuess> guessJoin(String columnName, List<DbForeignKey> foreignKeys,
                                                Set<String> knownTables) {
        return guessJoin(null, columnName, foreignKeys, knownTables);
    }

    /**
     * As {@link #guessJoin(String, List, Set)}, plus references named after the target without a key suffix:
     * {@code kayaks.brand} → {@code brands}, {@code video.type} → {@code video_types} (prefixed by the own table).
     * Anything not backed by a JDBC FK is only a name match: callers confirm it against the data.
     */
    public static Optional<JoinGuess> guessJoin(String ownTable, String columnName, List<DbForeignKey> foreignKeys,
                                                Set<String> knownTables) {
        Optional<JoinGuess> keyed = guessKeyedJoin(columnName, foreignKeys, knownTables);
        if (keyed.isPresent() || columnName == null || columnName.isBlank()) {
            return keyed;
        }
        String col = columnName.toLowerCase(Locale.ROOT);
        if (stripKeySuffix(col) != null) {
            return Optional.empty();
        }
        String own = ownTable == null ? null : ownTable.toLowerCase(Locale.ROOT);
        for (String candidate : tableCandidates(col)) {
            if (knownTables.contains(candidate) && !candidate.equals(own)) {
                return Optional.of(new JoinGuess(candidate, "uuid", false));
            }
        }
        if (own != null) {
            for (String prefix : tableCandidates(own)) {
                for (String candidate : tableCandidates(prefix + "_" + col)) {
                    if (knownTables.contains(candidate) && !candidate.equals(own)) {
                        return Optional.of(new JoinGuess(candidate, "uuid", false));
                    }
                }
            }
        }
        return Optional.empty();
    }

    private static Optional<JoinGuess> guessKeyedJoin(String columnName, List<DbForeignKey> foreignKeys,
                                                      Set<String> knownTables) {
        String col = columnName == null ? "" : columnName.toLowerCase(Locale.ROOT);
        if (foreignKeys != null) {
            for (DbForeignKey fk : foreignKeys) {
                if (fk.fkColumn() != null && fk.fkColumn().equalsIgnoreCase(col) && fk.pkTable() != null) {
                    String target = fk.pkTable().toLowerCase(Locale.ROOT);
                    if (knownTables.contains(target)) {
                        String key = fk.pkColumn() == null || fk.pkColumn().isBlank()
                                ? "uuid" : fk.pkColumn().toLowerCase(Locale.ROOT);
                        return Optional.of(new JoinGuess(target, key, true));
                    }
                }
            }
        }
        String base = stripKeySuffix(col);
        if (base == null || base.isBlank()) {
            return Optional.empty();
        }
        for (String candidate : tableCandidates(base)) {
            if (knownTables.contains(candidate)) {
                return Optional.of(new JoinGuess(candidate, "uuid", false));
            }
        }
        return Optional.empty();
    }

    /**
     * Detects a snello-style bridge table {@code a_b} with columns {@code a_id} + {@code b_id} (plus at most an own
     * id/uuid). Only this exact convention: it is what the runtime join-list endpoint expects. A link table shaped
     * differently, or one carrying its own data ({@code orders} with two FKs), is a normal entity with two joins.
     */
    public static Optional<BridgeGuess> guessBridge(String tableName, List<DbColumn> columns,
                                                    List<DbForeignKey> foreignKeys, Set<String> knownTables) {
        if (tableName == null || columns == null || columns.size() > 4) {
            return Optional.empty();
        }
        String table = tableName.toLowerCase(Locale.ROOT);
        Set<String> colNames = new HashSet<>();
        for (DbColumn c : columns) {
            colNames.add(c.name().toLowerCase(Locale.ROOT));
        }
        for (String left : knownTables) {
            if (!table.startsWith(left + "_")) {
                continue;
            }
            String right = table.substring(left.length() + 1);
            if (!knownTables.contains(right) || right.equals(left)) {
                continue;
            }
            if (colNames.contains(left + "_id") && colNames.contains(right + "_id")) {
                return Optional.of(new BridgeGuess(left, right));
            }
        }
        return Optional.empty();
    }

    public static boolean looksLikeHtml(Collection<Object> sampleValues) {
        if (sampleValues == null || sampleValues.isEmpty()) {
            return false;
        }
        int nonEmpty = 0;
        int html = 0;
        for (Object v : sampleValues) {
            if (v == null) {
                continue;
            }
            String s = String.valueOf(v).trim();
            if (s.isEmpty()) {
                continue;
            }
            nonEmpty++;
            if (HTML_TAG.matcher(s).find()) {
                html++;
            }
        }
        // plain text almost never contains real tags: a few rich rows are enough (many editors leave short texts bare)
        return html > 0 && html * 100 / nonEmpty >= 5;
    }

    static boolean hasValues(Collection<Object> sampleValues) {
        if (sampleValues == null) {
            return false;
        }
        for (Object v : sampleValues) {
            if (v != null && !String.valueOf(v).isBlank()) {
                return true;
            }
        }
        return false;
    }

    static boolean hasLongValue(Collection<Object> sampleValues) {
        for (Object v : sampleValues) {
            if (v != null) {
                String s = String.valueOf(v);
                if (s.length() > LONG_VALUE_LENGTH || s.indexOf('\n') >= 0) {
                    return true;
                }
            }
        }
        return false;
    }

    /** At least one value, and every non-blank one is UUID-shaped. */
    public static boolean allUuidValues(Collection<Object> sampleValues) {
        boolean any = false;
        if (sampleValues != null) {
            for (Object v : sampleValues) {
                if (v == null || String.valueOf(v).isBlank()) {
                    continue;
                }
                if (!looksLikeUuidValue(v)) {
                    return false;
                }
                any = true;
            }
        }
        return any;
    }

    static boolean hasMediaName(String columnName) {
        for (String token : columnName.split("_")) {
            if (MEDIA_TOKENS.contains(token)) {
                return true;
            }
        }
        return false;
    }

    public static boolean looksLikeUuidValue(Object value) {
        if (value == null) {
            return false;
        }
        return UUID_VALUE.matcher(String.valueOf(value).trim()).matches();
    }

    static boolean isLongSqlText(String sql) {
        if (sql == null) {
            return false;
        }
        String t = sql.toLowerCase(Locale.ROOT);
        return t.equals("text") || t.startsWith("clob") || t.startsWith("longtext")
                || t.startsWith("mediumtext") || t.startsWith("longvarchar");
    }

    static boolean isWideVarchar(String sql, int size) {
        if (size >= LONG_VARCHAR_THRESHOLD) {
            return true;
        }
        if (sql == null) {
            return false;
        }
        String t = sql.toLowerCase(Locale.ROOT);
        if (!t.startsWith("varchar") && !t.startsWith("character varying") && !t.startsWith("nvarchar")) {
            return false;
        }
        int open = t.indexOf('(');
        int close = t.indexOf(')');
        if (open > 0 && close > open) {
            try {
                return Integer.parseInt(t.substring(open + 1, close).trim()) >= LONG_VARCHAR_THRESHOLD;
            } catch (NumberFormatException ignored) {
                return false;
            }
        }
        return false;
    }

    static String stripKeySuffix(String column) {
        if (column.endsWith("_uuid")) {
            return column.substring(0, column.length() - 5);
        }
        if (column.endsWith("_id")) {
            return column.substring(0, column.length() - 3);
        }
        return null;
    }

    static List<String> tableCandidates(String base) {
        List<String> out = new ArrayList<>();
        out.add(base);
        if (!base.endsWith("s")) {
            out.add(base + "s");
        }
        if (base.endsWith("y") && base.length() > 1) {
            out.add(base.substring(0, base.length() - 1) + "ies");
        }
        if (base.endsWith("ies") && base.length() > 3) {
            out.add(base.substring(0, base.length() - 3) + "y");
        }
        if (base.endsWith("s") && base.length() > 1) {
            out.add(base.substring(0, base.length() - 1));
        }
        return out;
    }

    private static boolean looksLikeUuidColumn(String name, String sql) {
        if ("uuid".equals(name) || name.endsWith("_uuid")) {
            return true;
        }
        if (sql == null) {
            return false;
        }
        String t = sql.toLowerCase(Locale.ROOT);
        return t.equals("uuid") || t.startsWith("varchar(36)") || t.startsWith("varchar(50)")
                || t.startsWith("char(36)");
    }

    private static Map<String, DbColumn> index(List<DbColumn> columns) {
        Map<String, DbColumn> map = new java.util.LinkedHashMap<>();
        if (columns == null) {
            return map;
        }
        for (DbColumn c : columns) {
            if (c != null && c.name() != null) {
                map.put(c.name().toLowerCase(Locale.ROOT), c);
            }
        }
        return map;
    }

    /** @param addition column the key is derived from ({@code table_key_addition}); null unless {@code slug} */
    public record KeyGuess(String column, String keyType, boolean declareKeyField, String addition) {
        public KeyGuess(String column, String keyType, boolean declareKeyField) {
            this(column, keyType, declareKeyField, null);
        }
    }

    public record JoinGuess(String targetTable, String targetKey, boolean fromFk) {
    }

    public record BridgeGuess(String leftTable, String rightTable) {
    }
}
