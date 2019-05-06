package io.snello.util;

import io.micronaut.http.HttpParameters;

import java.sql.Array;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;

public class ParamUtils {

  public  static final String AND = " AND ";
  public  static final String _AND_ = "&&";
  public  static final String _OR_ = "||";
  public  static final String EQU = "=";

  public  static final String NE = "_ne";
  public  static final String _NE = "!=";

  public  static final String LT = "_lt";
  public  static final String _LT = "<";

  public  static final String GT = "_gt";
  public  static final String _GT = ">";

  public  static final String LTE = "_lte";
  public  static final String _LTE = "<=";

  public  static final String GTE = "_gte";
  public  static final String _GTE = ">=";

  public  static final String CNT = "_contains";
  public  static final String LIKE = "_like";
  public  static final String _CNT = " LIKE ";


  public  static final String CONTSS = "_containss";
  public  static final String _LIKE = "%";

  public  static final String RLIKE = "_rlike";
  public  static final String LLIKE = "_llike";

  public  static final String NCNT = "_ncontains";
  public  static final String _NCNT = " NOT LIKE ";

  public  static final String IN = "_in";
  public  static final String _IN = " IN (?) ";

  public  static final String NN = "_nn";
  public  static final String _NN = " NOT NULL ";

  public  static final String INN = "_inn";
  public  static final String _INN = " IS NOT NULL ";

  public  static final String NIE = "_nie";
  public  static final String _NIE_ = " <> '' ";

  public  static final String IE = "_ie";
  public  static final String _IE = " = '' ";


  public  static final String SPACE = " ";

    // _limit=2 _start=1 _sort=page_title:desc
  public  static final String _LIMIT = "_limit";
  public  static final String _START = "_start";
  public  static final String _SORT = "_sort";
  public  static final String _SELECT_FIELDS = "select_fields";


    public static String select_fields(HttpParameters httpParameters) {
        if (httpParameters == null || httpParameters.isEmpty()) {
            return null;
        }
        if (httpParameters.contains("select_fields") && httpParameters.get("select_fields") != null && !httpParameters.get("select_fields").trim().isEmpty()) {
            return httpParameters.get("select_fields");
        }
        return null;
    }

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
            if (key.equals(_LIMIT) || key.equals(_START) || key.equals(_SORT) || key.equals(_SELECT_FIELDS)) {
                continue;
            }
            if (key_value.getValue() != null && key_value.getValue().size() > 0 && key_value.getValue().get(0) != null
                    && !key_value.getValue().get(0).trim().isEmpty()) {
                value = key_value.getValue().get(0);
            } else {
                continue;
            }
            // NN = "_nn";
            // _NN = " NOT NULL ";
            if (key.endsWith(NN)) {
                if (where.length() > 0) {
                    where.append(AND);
                }
                where.append(key.substring(0, key.length() - NN.length()));
                where.append(_NN).append(SPACE);
                in.add(null);
                continue;
            }

            if (key.endsWith(INN)) {
                if (where.length() > 0) {
                    where.append(AND);
                }
                where.append(key.substring(0, key.length() - INN.length()));
                where.append(_INN).append(SPACE);
                in.add(null);
                continue;
            }
            if (key.endsWith(IN)) {
                try {
                    if (where.length() > 0) {
                        where.append(AND);
                    }
                    // conn.prepareStatement("select * from employee where id in (?)");
                    //Array array = conn.createArrayOf("VARCHAR", list.toArray());
                    //pstmt.setArray(1, array);
                    Connection connection = null;
                    Array array = connection.createArrayOf("VARCHAR", value.split(","));
                    in.add(array);
                    where.append(key.substring(0, key.length() - IN.length()));
                    where.append(_IN).append(SPACE);
                } catch (SQLException e) {
                    e.printStackTrace();
                }
                continue;
            }

            // NIE = "_nie";
            if (key.endsWith(NIE)) {
                if (where.length() > 0) {
                    where.append(AND);
                }
                where.append(key.substring(0, key.length() - NIE.length()));
                where.append(_NIE_).append(SPACE);
                continue;
            }
            // IE = "_ie";
            if (key.endsWith(IE)) {
                if (where.length() > 0) {
                    where.append(AND);
                }
                where.append(key.substring(0, key.length() - IE.length()));
                where.append(_IE).append(SPACE);
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
            if (key.endsWith(LIKE)) {
                if (where.length() > 0) {
                    where.append(AND);
                }
                where.append(key.substring(0, key.length() - LIKE.length()));
                where.append(_CNT);
                where.append(" ? ").append(SPACE);
                in.add(_LIKE + value + _LIKE);
                continue;
            }
            if (key.endsWith(RLIKE)) {
                if (where.length() > 0) {
                    where.append(AND);
                }
                where.append(key.substring(0, key.length() - RLIKE.length()));
                where.append(_CNT);
                where.append(" ? ").append(SPACE);
                in.add(value + _LIKE);
                continue;
            }
            if (key.endsWith(LLIKE)) {
                if (where.length() > 0) {
                    where.append(AND);
                }
                where.append(key.substring(0, key.length() - LLIKE.length()));
                where.append(_CNT);
                where.append(" ? ").append(SPACE);
                in.add(_LIKE + value);
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
