package io.snello.util;

import io.micronaut.http.HttpParameters;
import io.snello.cms.model.Condition;
import org.jboss.logging.Logger;

import java.util.List;

import static io.snello.util.ParamUtils.*;

public class ConditionUtils {

    static Logger logger = Logger.getLogger(ConditionUtils.class);


    public static void where(HttpParameters httpParameters, List<Condition> conditions, StringBuffer where, List<Object> in) {
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
                    if (!httpParameters.asMap().containsKey(keySimple1)) {
                        continue;
                    } else {
                        value = httpParameters.get(keySimple1);
                        if (value == null) {
                            continue;
                        }
                    }
                }
                if (cond.endsWith(NIE)) {
                    String keySimple2 = cond.substring(0, cond.length() - NIE.length());
                    if (!httpParameters.asMap().containsKey(keySimple2)) {
                        continue;
                    } else {
                        value = httpParameters.get(keySimple2);
                        if (value == null || value.trim().isEmpty()) {
                            continue;
                        }
                    }
                }
                if (cond.contains(GT)) {
                    String keySimple = cond.substring(0, cond.length() - GT.length());
                    if (!httpParameters.asMap().containsKey(keySimple)) {
                        continue;
                    } else {
                        value = httpParameters.get(keySimple);
                        if (value == null || Integer.valueOf(value) < 1) {
                            continue;
                        }
                    }
                }
                if (value != null) {
                    String[] params = condition.query_params.split(";");
                    for (String param : params) {
                        Object obj = httpParameters.asMap().get(param);
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
//                        Object obj = httpParameters.asMap().get(param);
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
