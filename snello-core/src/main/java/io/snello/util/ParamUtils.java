package io.snello.util;

import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.core.MultivaluedMap;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ParamUtils {


    public static final String AND = " AND ";
    public static final String OR = " OR ";
    public static final String _AND_ = "&&";
    public static final String _OR_ = "||";
    public static final String EQU = "=";

    public static final String NE = "_ne";
    public static final String _NE = "!=";

    public static final String LT = "_lt";
    public static final String _LT = "<";

    public static final String GT = "_gt";
    public static final String _GT = ">";

    public static final String LTE = "_lte";
    public static final String _LTE = "<=";

    public static final String GTE = "_gte";
    public static final String _GTE = ">=";

    public static final String CNT = "_contains";
    public static final String ICNT = "_icontains";
    public static final String LIKE = "_like";
    public static final String ILIKE = "_ilike";
    public static final String _CNT = " LIKE ";
    /** Legacy token; case-insensitive filters now use {@code lower(col) LIKE lower(?)} for all dialects. */
    public static final String _ICNT = " ILIKE ";

    public static final String CONTSS = "_containss";
    public static final String _LIKE = "%";

    public static final String RLIKE = "_rlike";
    public static final String LLIKE = "_llike";

    public static final String NCNT = "_ncontains";
    public static final String _NCNT = " NOT LIKE ";

    public static final String IN = "_in";
    public static final String _IN = " IN ";

    public static final String NN = "_nn";
    public static final String _NN = " IS NOT NULL ";

    public static final String INN = "_inn";
    public static final String _INN = " IS NULL ";

    public static final String NIE = "_nie";
    public static final String _NIE_ = " <> '' ";

    public static final String IE = "_ie";
    public static final String _IE = " = '' ";

    public static final String SPACE = " ";

    // _limit=2 _start=1 _sort=page_title:desc
    public static final String _LIMIT = "_limit";
    public static final String _START = "_start";
    public static final String _SORT = "_sort";
    public static final String _SELECT_FIELDS = "select_fields";

    /** {@code or1.field} / {@code and12.field_gt} — one nesting level only. */
    private static final Pattern GROUP_PREFIX = Pattern.compile("^(or|and)([1-9][0-9]{0,2})\\.(.+)$");

    private enum GroupKind {
        DEFAULT, OR, AND
    }

    private record ParsedParam(GroupKind kind, int groupId, String fieldKey, List<String> values) {
    }

    public static String select_fields(MultivaluedMap<String, String> httpParameters) {
        if (httpParameters == null || httpParameters.isEmpty()) {
            return null;
        }
        if (httpParameters.containsKey("select_fields") && httpParameters.get("select_fields") != null
                && !httpParameters.get("select_fields").isEmpty()) {
            return SqlSafety.requireSafeSelectFields(httpParameters.get("select_fields").get(0));
        }
        return null;
    }

    /**
     * Appends filter predicates from query parameters.
     * <p>
     * Unprefixed keys are AND-ed (legacy behaviour). Keys {@code orN.field[_suffix]} / {@code andN.field[_suffix]}
     * form parenthesized groups; groups are AND-ed with each other and with the default group. Multiple values for
     * the same key become separate predicates joined with the group operator.
     */
    public static void where(Map<String, List<String>> httpParameters, StringBuffer where, List<Object> in) {
        if (httpParameters == null || httpParameters.isEmpty()) {
            return;
        }
        List<ParsedParam> defaults = new ArrayList<>();
        TreeMap<Integer, List<ParsedParam>> orGroups = new TreeMap<>();
        TreeMap<Integer, List<ParsedParam>> andGroups = new TreeMap<>();

        for (Map.Entry<String, List<String>> entry : httpParameters.entrySet()) {
            String rawKey = entry.getKey();
            if (rawKey == null || rawKey.equals(_LIMIT) || rawKey.equals(_START) || rawKey.equals(_SORT)
                    || rawKey.equals(_SELECT_FIELDS)) {
                continue;
            }
            ParsedParam parsed = parseParam(rawKey, entry.getValue());
            switch (parsed.kind()) {
                case DEFAULT -> defaults.add(parsed);
                case OR -> orGroups.computeIfAbsent(parsed.groupId(), k -> new ArrayList<>()).add(parsed);
                case AND -> andGroups.computeIfAbsent(parsed.groupId(), k -> new ArrayList<>()).add(parsed);
            }
        }

        appendGroup(where, in, defaults, AND, false);
        for (List<ParsedParam> group : orGroups.values()) {
            appendGroup(where, in, group, OR, true);
        }
        for (List<ParsedParam> group : andGroups.values()) {
            appendGroup(where, in, group, AND, true);
        }
    }

    private static ParsedParam parseParam(String rawKey, List<String> values) {
        Matcher matcher = GROUP_PREFIX.matcher(rawKey);
        GroupKind kind = GroupKind.DEFAULT;
        int groupId = 0;
        String fieldKey = rawKey;
        if (matcher.matches()) {
            kind = "or".equals(matcher.group(1)) ? GroupKind.OR : GroupKind.AND;
            groupId = Integer.parseInt(matcher.group(2));
            fieldKey = matcher.group(3);
            if (fieldKey == null || fieldKey.isBlank() || fieldKey.indexOf('.') >= 0) {
                io.quarkus.logging.Log.warnf("rejected nested or empty filter group field: %s", rawKey);
                throw new BadRequestException("invalid query parameter name: " + rawKey);
            }
        } else if (rawKey.matches("^(or|and)\\d+\\..+") || rawKey.matches("^(or|and)\\..+")) {
            // Looks like a group (or0.x / or01.x / or1234.x / or.x) but failed GROUP_PREFIX
            io.quarkus.logging.Log.warnf("rejected query parameter name (bad group prefix): %s", rawKey);
            throw new BadRequestException("invalid query parameter name: " + rawKey);
        } else if ((rawKey.startsWith("or") || rawKey.startsWith("and")) && !SqlSafety.isIdentifier(rawKey)) {
            io.quarkus.logging.Log.warnf("rejected query parameter name (bad group prefix): %s", rawKey);
            throw new BadRequestException("invalid query parameter name: " + rawKey);
        }
        // Field key (with optional operator suffix) becomes SQL text — must be a plain identifier.
        if (!SqlSafety.isIdentifier(fieldKey)) {
            io.quarkus.logging.Log.warnf("rejected query parameter name (not a plain identifier): %s", rawKey);
            throw new BadRequestException("invalid query parameter name: " + rawKey);
        }
        return new ParsedParam(kind, groupId, fieldKey, values == null ? List.of() : values);
    }

    private static void appendGroup(StringBuffer where, List<Object> in, List<ParsedParam> params, String joinOp,
                                    boolean wrap) {
        if (params == null || params.isEmpty()) {
            return;
        }
        StringBuffer group = new StringBuffer();
        for (ParsedParam param : params) {
            appendFieldPredicates(group, in, joinOp, param.fieldKey(), param.values());
        }
        if (group.isEmpty()) {
            return;
        }
        if (!where.isEmpty()) {
            where.append(AND);
        }
        if (wrap) {
            where.append("( ").append(group).append(")");
        } else {
            where.append(group);
        }
    }

    /**
     * Appends one or more predicates for a single field key. Valued multi-entries each become a predicate;
     * empty-value keys may still match {@code _nn}/{@code _inn}/{@code _nie}/{@code _ie}.
     */
    private static void appendFieldPredicates(StringBuffer where, List<Object> in, String joinOp, String key,
                                              List<String> values) {
        List<String> nonEmpty = new ArrayList<>();
        if (values != null) {
            for (String v : values) {
                if (v != null && !v.trim().isEmpty()) {
                    nonEmpty.add(v);
                }
            }
        }
        if (nonEmpty.isEmpty()) {
            appendValuelessPredicate(where, joinOp, key);
            return;
        }
        for (String value : nonEmpty) {
            appendValuedPredicate(where, in, joinOp, key, value);
        }
    }

    private static void appendValuelessPredicate(StringBuffer where, String joinOp, String key) {
        if (key.endsWith(NN)) {
            join(where, joinOp);
            where.append(key.substring(0, key.length() - NN.length()));
            where.append(_NN).append(SPACE);
            return;
        }
        if (key.endsWith(INN)) {
            join(where, joinOp);
            where.append(key.substring(0, key.length() - INN.length()));
            where.append(_INN).append(SPACE);
            return;
        }
        if (key.endsWith(NIE)) {
            join(where, joinOp);
            where.append(key.substring(0, key.length() - NIE.length()));
            where.append(_NIE_).append(SPACE);
            return;
        }
        if (key.endsWith(IE)) {
            join(where, joinOp);
            where.append(key.substring(0, key.length() - IE.length()));
            where.append(_IE).append(SPACE);
        }
    }

    /**
     * Suffix order matches the historical {@code where} chain (including {@code _lt} matching before {@code _lte}).
     */
    private static void appendValuedPredicate(StringBuffer where, List<Object> in, String joinOp, String key,
                                              String value) {
        if (key.endsWith(IN)) {
            join(where, joinOp);
            where.append("( ").append(SPACE);
            where.append(key.substring(0, key.length() - IN.length()));
            where.append(_IN).append(SPACE);
            if (value != null && value.contains(",")) {
                String[] values = value.split(",");
                StringJoiner sj = new StringJoiner(",", "(", ")");
                for (String v : values) {
                    sj.add("?");
                    in.add(v == null ? null : v.trim());
                }
                where.append(sj.toString()).append(SPACE);
            } else {
                where.append(" ( ? ) ").append(SPACE);
                in.add(value == null ? null : value.trim());
            }
            where.append(") ").append(SPACE);
            return;
        }
        if (key.endsWith(LT)) {
            join(where, joinOp);
            where.append(key.substring(0, key.length() - LT.length()));
            where.append(_LT);
            where.append(" ? ").append(SPACE);
            in.add(value);
            return;
        }
        if (key.endsWith(GT)) {
            join(where, joinOp);
            where.append(key.substring(0, key.length() - GT.length()));
            where.append(_GT);
            where.append(" ? ").append(SPACE);
            in.add(value);
            return;
        }
        if (key.endsWith(LTE)) {
            join(where, joinOp);
            where.append(key.substring(0, key.length() - LTE.length()));
            where.append(_LTE);
            where.append(" ? ").append(SPACE);
            in.add(value);
            return;
        }
        if (key.endsWith(GTE)) {
            join(where, joinOp);
            where.append(key.substring(0, key.length() - GTE.length()));
            where.append(_GTE);
            where.append(" ? ").append(SPACE);
            in.add(value);
            return;
        }
        if (key.endsWith(CNT)) {
            join(where, joinOp);
            where.append(key.substring(0, key.length() - CNT.length()));
            where.append(_CNT);
            where.append(" ? ").append(SPACE);
            in.add(_LIKE + value + _LIKE);
            return;
        }
        if (key.endsWith(LIKE)) {
            join(where, joinOp);
            where.append(key.substring(0, key.length() - LIKE.length()));
            where.append(_CNT);
            where.append(" ? ").append(SPACE);
            in.add(_LIKE + value + _LIKE);
            return;
        }
        if (key.endsWith(ILIKE)) {
            join(where, joinOp);
            where.append("lower(").append(key.substring(0, key.length() - ILIKE.length())).append(")");
            where.append(_CNT);
            where.append(" lower(?) ").append(SPACE);
            in.add(_LIKE + value + _LIKE);
            return;
        }
        if (key.endsWith(ICNT)) {
            join(where, joinOp);
            where.append("lower(").append(key.substring(0, key.length() - ICNT.length())).append(")");
            where.append(_CNT);
            where.append(" lower(?) ").append(SPACE);
            in.add(_LIKE + value + _LIKE);
            return;
        }
        if (key.endsWith(RLIKE)) {
            join(where, joinOp);
            where.append("lower(").append(key.substring(0, key.length() - RLIKE.length())).append(")");
            where.append(_CNT);
            where.append(" lower(?) ").append(SPACE);
            in.add(value + _LIKE);
            return;
        }
        if (key.endsWith(LLIKE)) {
            join(where, joinOp);
            where.append("lower(").append(key.substring(0, key.length() - LLIKE.length())).append(")");
            where.append(_CNT);
            where.append(" lower(?) ").append(SPACE);
            in.add(_LIKE + value);
            return;
        }
        if (key.endsWith(CONTSS)) {
            join(where, joinOp);
            where.append(" lower( " + key.substring(0, key.length() - CONTSS.length()) + " ) ");
            where.append(_CNT);
            where.append(" ? ").append(SPACE);
            in.add(_LIKE + value.toLowerCase() + _LIKE);
            return;
        }
        if (key.endsWith(NCNT)) {
            join(where, joinOp);
            where.append(key.substring(0, key.length() - NCNT.length()));
            where.append(_NCNT);
            where.append(" ? ").append(SPACE);
            in.add(_LIKE + value + _LIKE);
            return;
        }
        join(where, joinOp);
        where.append(key).append(EQU).append(" ? ").append(SPACE);
        in.add(value);
    }

    private static void join(StringBuffer where, String joinOp) {
        if (!where.isEmpty()) {
            where.append(joinOp);
        }
    }

}
