package io.snello.cms.util;

import io.micronaut.http.HttpParameters;

import java.util.List;
import java.util.Map;

public class ParamUtils {

    static final String AND = " AND ";
    static final String EQU = "=";
    static final String NE = "_ne";
    static final String _NE = "!=";
    static final String LT = "_lt";
    static final String _LT = "<";
    static final String GT = "_gt";
    static final String _GT = ">";
    static final String LTE = "_lte";
    static final String _LTE = "<=";
    static final String GTE = "_gte";
    static final String _GTE = ">=";
    static final String CNT = "_contains";
    static final String _CNT = " LIKE ";
    static final String _LIKE = "%";
    static final String CONTSS = "_containss";
    static final String NCNT = "_ncontains";
    static final String _NCNT = " NOT LIKE ";
    static final String SPACE = " ";

    // _limit=2 _start=1 _sort=page_title:desc
    static final String _LIMIT = "_limit";
    static final String _START = "_start";
    static final String _SORT = "_sort";


    public static void where(HttpParameters httpParameters, StringBuffer where, List<Object> in) {
        if (httpParameters == null || httpParameters.isEmpty()) {
            return;
        }
        /*
            =: Equals
            _ne: Not equals
            _lt: Lower than
            _gt: Greater than
            _lte: Lower than or equal to
            _gte: Greater than or equal to
            _contains: Contains
            _containss: Contains case sensitive
         */
        for (Map.Entry<String, List<String>> key_value : httpParameters) {
            String key = key_value.getKey();
            String value;
            if (key.equals(_LIMIT) || key.equals(_START) || key.equals(_SORT)) {
                continue;
            }
            if (key_value.getValue() != null && key_value.getValue().size() > 0) {
                value = key_value.getValue().get(0);
            } else {
                continue;
            }

            if (key.endsWith(NE)) {
                if (where.length() > 0) {
                    where.append(AND);
                }
                where.append(key.substring(0, key.length() - NE.length()));
                where.append(_NE);
                where.append(" ? ").append(SPACE);
                in.add(value);
                continue;
            }
            if (key.endsWith(LT)) {
                if (where.length() > 0) {
                    where.append(AND);
                }
                where.append(key.substring(0, key.length() - LT.length()));
                where.append(_LT);
                where.append(" ? ").append(SPACE);
                in.add(value);
                continue;
            }
            if (key.endsWith(GT)) {
                if (where.length() > 0) {
                    where.append(AND);
                }
                where.append(key.substring(0, key.length() - GT.length()));
                where.append(_GT);
                where.append(" ? ").append(SPACE);
                in.add(value);
                continue;
            }
            if (key.endsWith(LTE)) {
                if (where.length() > 0) {
                    where.append(AND);
                }
                where.append(key.substring(0, key.length() - LTE.length()));
                where.append(_LTE);
                where.append(" ? ").append(SPACE);
                in.add(value);
                continue;
            }

            if (key.endsWith(GTE)) {
                if (where.length() > 0) {
                    where.append(AND);
                }
                where.append(key.substring(0, key.length() - GTE.length()));
                where.append(_GT);
                where.append(" ? ").append(SPACE);
                in.add(value);
                continue;
            }
            if (key.endsWith(CNT)) {
                if (where.length() > 0) {
                    where.append(AND);
                }
                where.append(key.substring(0, key.length() - CNT.length()));
                where.append(_CNT);
                where.append(" ? ").append(SPACE);
                in.add(_LIKE + value + _LIKE);
                continue;
            }
            if (key.endsWith(CONTSS)) {
                if (where.length() > 0) {
                    where.append(AND);
                }
                where.append(" lower( " + key.substring(0, key.length() - CONTSS.length()) + " ) ");
                where.append(_CNT);
                where.append(" ? ").append(SPACE);
                in.add(_LIKE + value.toLowerCase() + _LIKE);
                continue;
            }
            if (key.endsWith(NCNT)) {
                if (where.length() > 0) {
                    where.append(AND);
                }
                where.append(key);
                where.append(_NCNT);
                where.append(" ? ").append(SPACE);
                in.add(_LIKE + value.toLowerCase() + _LIKE);
                continue;
            }
            if (where.length() > 0) {
                where.append(AND);
            }
            where.append(key).append(EQU).append(" ? ").append(SPACE);
            in.add(value);
        }
    }
}
