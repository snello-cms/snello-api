package io.snello.util;

import io.micronaut.context.event.ApplicationEventPublisher;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.annotation.Body;
import io.snello.model.Condition;
import io.snello.model.events.ConditionCreateUpdateEvent;
import io.snello.service.ApiService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;

import static io.micronaut.http.HttpResponse.ok;
import static io.snello.management.AppConstants.UUID;
import static io.snello.util.ParamUtils.*;

public class ConditionUtils {

    static Logger logger = LoggerFactory.getLogger(ConditionUtils.class);


    public static Map<String, Object> createCondition(Map<String, Object> map, ApiService apiService, String table, ApplicationEventPublisher eventPublisher) throws Exception {
        map.put(UUID, java.util.UUID.randomUUID().toString());
        map = apiService.create(table, map, UUID);
        eventPublisher.publishEvent(new ConditionCreateUpdateEvent(map));
        return map;
    }


    public static boolean where(Map<String, List<String>> httpParameters, List<Condition> conditions, StringBuffer where, List<Object> in) {
        if (httpParameters == null || httpParameters.isEmpty()) {
            logger.info("no parameters");
            return false;
        }
        if (conditions == null || conditions.isEmpty()) {
            logger.info("no conditions");
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
                // basta che sia vera
                conditions_array[0] = condition.condition;
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
                        value = httpParameters.get(keySimple1).get(0);
                        if (value == null) {
                            if ("AND".equals(conditionType)) break;
                            continue;
                        }
                    }
                }
                if (cond.endsWith(NIE)) {
                    String keySimple2 = cond.substring(0, cond.length() - NIE.length());
                    if (!httpParameters.containsKey(keySimple2)) {
                        if ("AND".equals(conditionType)) break;
                        continue;
                    } else {
                        value = httpParameters.get(keySimple2).get(0);
                        if (value == null || value.trim().isEmpty()) {
                            if ("AND".equals(conditionType)) break;
                            continue;
                        }
                    }
                }
                if (cond.contains(GT)) {
                    String keySimple = cond.substring(0, cond.length() - GT.length());
                    if (!httpParameters.containsKey(keySimple)) {
                        if ("AND".equals(conditionType)) break;
                        continue;
                    } else {
                        value = httpParameters.get(keySimple).get(0);
                        if (value == null || Integer.valueOf(value) < 1) {
                            if ("AND".equals(conditionType)) break;
                            continue;
                        }
                    }
                }

                if ("OR".equals(conditionType)) {
                    // ==> la condizione VA AGGIUNTA
                    addCondition(where, condition, httpParameters, in);
                }
                if ("AND".equals(conditionType) && (i == size)) {
                    // ==> la condizione VA AGGIUNTA
                    addCondition(where, condition, httpParameters, in);
                }
            }
        }
        logger.info("where from conditions: " + where);
        return where.length() > 0;
    }

    private static void addCondition(StringBuffer where, Condition condition, Map<String, List<String>> httpParameters, List<Object> in) {
        if (in.size() > 0) {
            in.clear();
        }
        String[] params = condition.query_params.split(";");
        for (String param : params) {
            Object obj = httpParameters.get(param).get(0);
            in.add(obj);
        }
        if (where.length() > 0 && condition.separator != null && !condition.separator.trim().isEmpty()) {
            where.append(" " + condition.separator + " ");
        }
        where.append(condition.sub_query);
    }
}
