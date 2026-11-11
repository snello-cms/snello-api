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
        if (name.endsWith("_url") || name.equals("url") || name.equals("website")) {
            return SemanticType.URL;
        }

        SemanticType fromSql = FieldUiDerivation.semanticTypeFromSqlType(sql);
        if (fromSql == SemanticType.BOOLEAN || fromSql == SemanticType.DATE || fromSql == SemanticType.DATETIME
                || fromSql == SemanticType.TIME || fromSql == SemanticType.INTEGER || fromSql == SemanticType.DECIMAL) {
            return fromSql;
        }

        if (isLongSqlText(sql) || isWideVarchar(sql, column.size())) {
            if (looksLikeHtml(sampleValues)) {
                return SemanticType.HTML;
            }
            return SemanticType.LONG_TEXT;
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
     * Detects a snello-style bridge table {@code a_b} with columns {@code a_id} + {@code b_id}
     * (optionally a uuid/id and little else).
     */
    public static Optional<BridgeGuess> guessBridge(String tableName, List<DbColumn> columns,
                                                    List<DbForeignKey> foreignKeys, Set<String> knownTables) {
        if (tableName == null || columns == null) {
            return Optional.empty();
        }
        String table = tableName.toLowerCase(Locale.ROOT);
        List<String> ends = new ArrayList<>();
        if (foreignKeys != null) {
            for (DbForeignKey fk : foreignKeys) {
                if (fk.pkTable() != null && knownTables.contains(fk.pkTable().toLowerCase(Locale.ROOT))) {
                    String t = fk.pkTable().toLowerCase(Locale.ROOT);
                    if (!ends.contains(t)) {
                        ends.add(t);
                    }
                }
            }
        }
        if (ends.size() == 2) {
            return Optional.of(new BridgeGuess(ends.get(0), ends.get(1)));
        }

        Set<String> colNames = new HashSet<>();
        for (DbColumn c : columns) {
            colNames.add(c.name().toLowerCase(Locale.ROOT));
        }
        // name pattern tableA_tableB
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
        // two *_id columns matching known tables, few extras
        List<JoinGuess> refs = new ArrayList<>();
        for (DbColumn c : columns) {
            String n = c.name().toLowerCase(Locale.ROOT);
            if ("uuid".equals(n) || "id".equals(n)) {
                continue;
            }
            Optional<JoinGuess> join = guessJoin(n, foreignKeys, knownTables);
            if (join.isPresent()) {
                refs.add(join.get());
            }
        }
        if (refs.size() == 2 && columns.size() <= 4) {
            return Optional.of(new BridgeGuess(refs.get(0).targetTable(), refs.get(1).targetTable()));
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
        return nonEmpty > 0 && html * 100 / nonEmpty >= 30;
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

    public record KeyGuess(String column, String keyType, boolean declareKeyField) {
    }

    public record JoinGuess(String targetTable, String targetKey, boolean fromFk) {
    }

    public record BridgeGuess(String leftTable, String rightTable) {
    }
}
