package io.snello.util;

import io.quarkus.logging.Log;
import jakarta.ws.rs.BadRequestException;

import java.util.List;
import java.util.regex.Pattern;

/**
 * Guards for the request values that end up as SQL <em>text</em> instead of bound parameters: {@code _sort} (ORDER BY)
 * and {@code select_fields} (SELECT list). Before these checks both were spliced verbatim, so a caller could put a
 * subquery there and read rows that the row-level security filter hides, e.g.
 * {@code select_fields=uuid, (SELECT string_agg(name, ',') FROM lovers) AS leak}.
 * <p>
 * Only request input is checked: metadata {@code order_by} / {@code select_fields} come from admin configuration.
 */
public final class SqlSafety {

    /** Plain, optionally table-qualified identifier. */
    public static final String IDENT = "[A-Za-z_][A-Za-z0-9_]*(?:\\.[A-Za-z_][A-Za-z0-9_]*)?";

    private static final Pattern IDENTIFIER = Pattern.compile("^" + IDENT + "$");
    private static final Pattern SORT_ITEM = Pattern.compile("^" + IDENT + "(?:\\s*[:\\s]\\s*(?i:asc|desc))?$");
    private static final Pattern SELECT_ITEM = Pattern.compile(
            "^(?:\\*|[A-Za-z_][A-Za-z0-9_]*\\.\\*|" + IDENT + "(?:\\s+(?i:as)\\s+[A-Za-z_][A-Za-z0-9_]*)?)$");

    /** ParamUtils operator suffixes: a key ending with one is parsed as "column OPERATOR value". */
    private static final List<String> OPERATOR_SUFFIXES = List.of(
            ParamUtils.NE, ParamUtils.LT, ParamUtils.GT, ParamUtils.LTE, ParamUtils.GTE,
            ParamUtils.CNT, ParamUtils.ICNT, ParamUtils.LIKE, ParamUtils.ILIKE, ParamUtils.CONTSS,
            ParamUtils.RLIKE, ParamUtils.LLIKE, ParamUtils.NCNT, ParamUtils.IN, ParamUtils.NN,
            ParamUtils.INN, ParamUtils.NIE, ParamUtils.IE);

    private SqlSafety() {
    }

    public static boolean isIdentifier(String name) {
        return name != null && IDENTIFIER.matcher(name).matches();
    }

    /**
     * True when {@code column} would not be read as a plain equality by {@link ParamUtils#where}: for an owner column
     * named e.g. {@code owner_ne} the row filter would become {@code owner != caller}, the opposite of the intent.
     */
    public static boolean isUnsafeOwnerColumn(String column) {
        if (!isIdentifier(column)) {
            return true;
        }
        String lower = column.toLowerCase();
        return OPERATOR_SUFFIXES.stream().anyMatch(lower::endsWith);
    }

    /** {@code _sort}: "random", or comma-separated {@code column[ asc|desc]} / {@code column:asc|desc}. */
    public static String requireSafeSort(String sort) {
        if (sort == null || sort.isBlank()) {
            return sort;
        }
        String trimmed = sort.trim();
        if (trimmed.equalsIgnoreCase("random")) {
            return sort;
        }
        for (String item : trimmed.split(",")) {
            if (!SORT_ITEM.matcher(item.trim()).matches()) {
                Log.warnf("rejected _sort (not column[:asc|desc] list): %s", sort);
                throw new BadRequestException("invalid _sort: " + sort);
            }
        }
        return sort;
    }

    /** {@code select_fields}: comma-separated {@code *}, {@code t.*}, {@code column} or {@code column AS alias}. */
    public static String requireSafeSelectFields(String selectFields) {
        if (selectFields == null || selectFields.isBlank()) {
            return selectFields;
        }
        for (String item : selectFields.split(",")) {
            if (!SELECT_ITEM.matcher(item.trim()).matches()) {
                Log.warnf("rejected select_fields (not a plain column list): %s", selectFields);
                throw new BadRequestException("invalid select_fields: " + selectFields);
            }
        }
        return selectFields;
    }
}
