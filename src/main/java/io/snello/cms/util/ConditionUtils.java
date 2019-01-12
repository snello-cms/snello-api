package io.snello.cms.util;

import io.micronaut.http.HttpParameters;
import io.snello.cms.model.Condition;
import org.mvel2.MVEL;
import org.mvel2.integration.VariableResolverFactory;
import org.mvel2.integration.impl.MapVariableResolverFactory;

import java.util.List;

public class ConditionUtils {


    public static void where(HttpParameters httpParameters, List<Condition> conditions, StringBuffer where, List<Object> in) {
        if (httpParameters == null || httpParameters.isEmpty()) {
            System.out.println("no parameters");
            return;
        }
        if (conditions == null || conditions.isEmpty()) {
            System.out.println("no conditions");
            return;
        }
        for (Condition condition : conditions) {
            if (condition.condition != null) {
                VariableResolverFactory functionFactory = new MapVariableResolverFactory(httpParameters.asMap());
                Boolean result = (Boolean) MVEL.eval(condition.condition, functionFactory);
                System.out.println(result);
                if (result) {
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
        }
    }
}