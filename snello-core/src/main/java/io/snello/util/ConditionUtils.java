package io.snello.util;

import io.quarkus.logging.Log;
import io.snello.model.Condition;
import io.snello.model.events.ConditionCreateUpdateEvent;
import io.snello.service.ApiService;
import jakarta.enterprise.event.Event;

import java.util.List;
import java.util.Map;

import static io.snello.management.AppConstants.UUID;
import static io.snello.util.ParamUtils.*;

public class ConditionUtils {
    

    public static Map<String, Object> createCondition(Map<String, Object> map, ApiService apiService, String table, Event<ConditionCreateUpdateEvent> eventPublisher) throws Exception {
        map.put(UUID, java.util.UUID.randomUUID().toString());
        map = apiService.create(table, map, UUID);
        eventPublisher.fireAsync(new ConditionCreateUpdateEvent(map));
        return map;
    }


    /**
     * WHERE for a metadata list/count: matched conditions <em>and</em> the generic request filters.
     * <p>
     * It used to be either/or: a matched condition replaced the generic filters, which also carry the row-level
     * security owner value set by {@code ApiServiceRs}, so a matching condition returned other owners' rows. Now the
     * conditions go in parentheses (an OR inside a sub_query cannot escape the owner predicate), and the generic
     * filters always follow, minus the parameter names the conditions consume (they are not columns).
     */
    public static void whereWithParams(Map<String, List<String>> httpParameters, List<Condition> conditions,
                                       StringBuffer where, List<Object> in) {
        StringBuffer conditionWhere = new StringBuffer();
        List<Object> conditionIn = new java.util.ArrayList<>();
        try {
            where(httpParameters, conditions, conditionWhere, conditionIn);
        } catch (Exception e) {
            Log.warnf(e, "conditions ignored: %s", e.getMessage());
            conditionWhere.setLength(0);
            conditionIn.clear();
        }
        if (!conditionWhere.isEmpty()) {
            if (!where.isEmpty()) {
                where.append(AND);
            }
            where.append("( ").append(conditionWhere).append(" )");
            in.addAll(conditionIn);
        }
        ParamUtils.where(withoutConditionKeys(httpParameters, conditions), where, in);
    }

    /** {@code httpParameters} without the trigger and bound parameter names declared by {@code conditions}. */
    static Map<String, List<String>> withoutConditionKeys(Map<String, List<String>> httpParameters, List<Condition> conditions) {
        if (httpParameters == null || conditions == null || conditions.isEmpty()) {
            return httpParameters;
        }
        java.util.Set<String> keys = new java.util.HashSet<>();
        for (Condition condition : conditions) {
            if (condition.condition != null) {
                for (String piece : condition.condition.split("&&|\\|\\|")) {
                    keys.add(stripOperatorSuffix(piece.trim()));
                }
            }
            if (condition.query_params != null) {
                for (String param : condition.query_params.split(",|;")) {
                    keys.add(param.trim());
                }
            }
        }
        Map<String, List<String>> rest = new java.util.LinkedHashMap<>(httpParameters);
        rest.keySet().removeAll(keys);
        if (rest.size() != httpParameters.size()) {
            Log.debugf("generic filters without condition keys %s", keys);
        }
        return rest;
    }

    private static String stripOperatorSuffix(String piece) {
        for (String suffix : new String[]{NIE, NN, GTE, LTE, GT, LT}) {
            if (piece.endsWith(suffix)) {
                return piece.substring(0, piece.length() - suffix.length());
            }
        }
        return piece;
    }

    public static boolean where(Map<String, List<String>> httpParameters, List<Condition> conditions, StringBuffer where, List<Object> in) throws Exception {
        if (httpParameters == null || httpParameters.isEmpty()) {
            Log.info("no parameters");
            return false;
        }
        if (conditions == null || conditions.isEmpty()) {
            Log.info("no conditions");
            return false;
        }
        String[] conditions_array = null;
        for (Condition condition : conditions) {
            String conditionType = null;
            if (condition.condition.contains(_AND_)) {
                // tutti i pezzi devono ESSERE veri
                conditions_array = condition.condition.split(_AND_);
                conditionType = "AND";
            } else if (condition.condition.contains(_OR_)) {
                // basta che sia vero almeno 1 pezzo
                conditions_array = condition.condition.split(_OR_);
                conditionType = "OR";
            } else {
                // basta che sia vera (a single trigger: the array was still null here, so every simple condition
                // threw, was swallowed, and the request fell back to treating its parameters as columns)
                conditions_array = new String[]{condition.condition};
                conditionType = "OR";
            }
            if (conditions_array.length < 1) {
                continue;
            }
            // la condition deve essere vera (se sono in AND tutto VERO, SE SONO IN OR NE BASTA UNO)
            // poi concateno la query, e aggiungo tutti i parametri
            int size = conditions_array.length;
            int i = 0;
            for (String cond : conditions_array) {
                i++;
                String value = null;
                cond = cond.trim();
                if (cond.endsWith(NN)) {
                    String keySimple1 = cond.substring(0, cond.length() - NN.length());
                    if (!httpParameters.containsKey(keySimple1)) {
                        if ("AND".equals(conditionType)) break;
                        continue;
                    } else {
                        value = httpParameters.get(keySimple1).getFirst();
                        if (value == null) {
                            if ("AND".equals(conditionType)) break;
                            continue;
                        }
                    }
                } else if (cond.endsWith(NIE)) {
                    String keySimple2 = cond.substring(0, cond.length() - NIE.length());
                    if (!httpParameters.containsKey(keySimple2)) {
                        if ("AND".equals(conditionType)) break;
                        continue;
                    } else {
                        value = httpParameters.get(keySimple2).getFirst();
                        if (value == null || value.trim().isEmpty()) {
                            if ("AND".equals(conditionType)) break;
                            continue;
                        }
                    }
                }
                // PROVARE
                else if (cond.contains(GT)) {
                    String keySimple3 = cond.substring(0, cond.length() - GT.length());
                    String value3 = cond.substring(cond.length() - GT.length());
                    if (!httpParameters.containsKey(keySimple3)) {
                        if ("AND".equals(conditionType)) break;
                        continue;
                    } else {
                        value = httpParameters.get(keySimple3).getFirst();
                        if (value == null || Integer.valueOf(value) <= Integer.valueOf(value3)) {
                            if ("AND".equals(conditionType)) break;
                            continue;
                        }
                    }
                }
                // PROVARE
                else if (cond.contains(GTE)) {
                    String keySimple3 = cond.substring(0, cond.length() - GTE.length());
                    String value3 = cond.substring(cond.length() - GTE.length());
                    if (!httpParameters.containsKey(keySimple3)) {
                        if ("AND".equals(conditionType)) break;
                        continue;
                    } else {
                        value = httpParameters.get(keySimple3).getFirst();
                        if (value == null || Integer.valueOf(value) < Integer.valueOf(value3)) {
                            if ("AND".equals(conditionType)) break;
                            continue;
                        }
                    }
                }
                // PROVARE
                else if (cond.contains(LT)) {
                    String keySimple4 = cond.substring(0, cond.length() - LT.length());
                    String value4 = cond.substring(cond.length() - LT.length());
                    if (!httpParameters.containsKey(keySimple4)) {
                        if ("AND".equals(conditionType)) break;
                        continue;
                    } else {
                        value = httpParameters.get(keySimple4).getFirst();
                        if (value == null || Integer.valueOf(value) >= Integer.valueOf(value4)) {
                            if ("AND".equals(conditionType)) break;
                            continue;
                        }
                    }
                }
                // PROVARE
                else if (cond.contains(LTE)) {
                    String keySimple4 = cond.substring(0, cond.length() - LTE.length());
                    String value4 = cond.substring(cond.length() - LTE.length());
                    if (!httpParameters.containsKey(keySimple4)) {
                        if ("AND".equals(conditionType)) break;
                        continue;
                    } else {
                        value = httpParameters.get(keySimple4).getFirst();
                        if (value == null || Integer.valueOf(value) > Integer.valueOf(value4)) {
                            if ("AND".equals(conditionType)) break;
                            continue;
                        }
                    }
                }

                if ("OR".equals(conditionType)) {
                    // ==> la condizione VA AGGIUNTA
                    addCondition(where, condition, httpParameters, in);
                    break;
                }
                if ("AND".equals(conditionType) && (i == size)) {
                    // ==> la condizione VA AGGIUNTA
                    addCondition(where, condition, httpParameters, in);
                    break;
                }
            }
        }
        Log.info("where from conditions: " + where);
        return where.length() > 0;
    }

    private static void addCondition(StringBuffer where, Condition condition, Map<String, List<String>> httpParameters, List<Object> in) {
//        if (in.size() > 0) {
//            in.clear();
//        }
        //PERCHE VUOTARE IN??
        String[] params = condition.query_params.split(",|;");
        for (String param : params) {
            Object obj = httpParameters.get(param).getFirst();
            in.add(obj);
        }
        if (!where.isEmpty() && condition.separator != null && !condition.separator.trim().isEmpty()) {
            where.append(" ").append(condition.separator).append(" ");
        }
        where.append(condition.sub_query);
    }
}
