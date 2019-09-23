package io.snello.util;

import io.snello.model.Condition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;

import static io.snello.util.ParamUtils.*;

public class ConditionUtils {

    static Logger logger = LoggerFactory.getLogger(ConditionUtils.class);


    public static void where(Map<String, List<String>> httpParameters, List<Condition> conditions, StringBuffer where, List<Object> in) {
        if (httpParameters == null || httpParameters.isEmpty()) {
            logger.info("no parameters");
            return;
        }
        if (conditions == null || conditions.isEmpty()) {
            logger.info("no conditions");
            return;
        }
        String[] conditions_array = null;
        for (Condition condition : conditions) {
            if (condition.condition.contains(_AND_)) {
                conditions_array = condition.condition.split(_AND_);
            } else if (condition.condition.contains(_OR_)) {
                conditions_array = condition.condition.split(_OR_);
            } else {
                conditions_array[0] = condition.condition;
            }
            if (conditions_array.length < 1) {
                continue;
            }
            for (String cond : conditions_array) {
                String value = null;
                if (cond.endsWith(NN)) {
                    String keySimple1 = cond.substring(0, cond.length() - NN.length());
                    if (!httpParameters.containsKey(keySimple1)) {
                        continue;
                    } else {
                        value = httpParameters.get(keySimple1).get(0);
                        if (value == null) {
                            continue;
                        }
                    }
                }
                if (cond.endsWith(NIE)) {
                    String keySimple2 = cond.substring(0, cond.length() - NIE.length());
                    if (!httpParameters.containsKey(keySimple2)) {
                        continue;
                    } else {
                        value = httpParameters.get(keySimple2).get(0);
                        if (value == null || value.trim().isEmpty()) {
                            continue;
                        }
                    }
                }
                if (cond.contains(GT)) {
                    String keySimple = cond.substring(0, cond.length() - GT.length());
                    if (!httpParameters.containsKey(keySimple)) {
                        continue;
                    } else {
                        value = httpParameters.get(keySimple).get(0);
                        if (value == null || Integer.valueOf(value) < 1) {
                            continue;
                        }
                    }
                }
                if (value != null) {
                    String[] params = condition.query_params.split(";");
                    for (String param : params) {
                        Object obj = httpParameters.get(param).get(0);
                        in.add(obj);
                    }
                    if (where.length() > 0) {
                        where.append(" " + condition.separator + " ");
                    }
                    where.append(condition.sub_query);
                }
            }
//            if (condition.condition != null) {
//                VariableResolverFactory functionFactory = new MapVariableResolverFactory(httpParameters.asMap());
//                Boolean result = (Boolean) MVEL.eval(condition.condition, functionFactory);
//                System.out.println(result);
//                if (result) {
//                    String[] params = condition.query_params.split(";");
//                    for (String param : params) {
//                        Object obj = httpParameters.get(param);
//                        in.add(obj);
//                    }
//                    if (where.length() > 0) {
//                        where.append(" " + condition.separator + " ");
//                    }
//                    where.append(condition.sub_query);
//                }
//            }
        }
        logger.info("where from conditions: " + where);
    }
}
