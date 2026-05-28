package io.snello.service.actions;

import io.quarkus.logging.Log;
import io.snello.model.Action;
import io.snello.service.MetadataService;
import io.snello.service.ScriptService;
import io.snello.util.ActionUtils;
import jakarta.annotation.Priority;
import jakarta.inject.Inject;
import jakarta.interceptor.AroundInvoke;
import jakarta.interceptor.Interceptor;
import jakarta.interceptor.InvocationContext;

import java.util.Arrays;
import java.util.Map;

@ActionEvent
@Interceptor
@Priority(Interceptor.Priority.APPLICATION + 200)
public class ActionInterceptor {

    @Inject
    MetadataService metadataService;

    @Inject
    ScriptService scriptService;

    @AroundInvoke
    Object executeAction(InvocationContext context) throws Exception {
        String methodName = context.getMethod().getName();
        String className = context.getTarget().getClass().getSimpleName();
        String params = Arrays.toString(context.getParameters());
        Log.info(String.format("ACTION [%s.%s] called with params %s", className, methodName, params));
        Object ret = context.proceed();
        runConfiguredAction(methodName, context.getParameters(), ret);
        return ret;
    }

    private void runConfiguredAction(String methodName, Object[] params, Object ret) throws Exception {
        String condition = ActionUtils.conditionByMethod(methodName);
        if (condition == null || params == null || params.length < 1) {
            return;
        }

        if (!(params[0] instanceof String)) {
            return;
        }

        String table = (String) params[0];
        String tableKey = null;
        for (int i = params.length - 1; i >= 0; i--) {
            if (params[i] instanceof String) {
                tableKey = (String) params[i];
                break;
            }
        }
        if (tableKey == null || tableKey.isBlank()) {
            return;
        }

        Action action = metadataService.actionsMap().get(ActionUtils.actionKey(table, condition));
        if (action == null || action.body == null || action.body.trim().isEmpty()) {
            return;
        }

        Map<String, Object> map = ActionUtils.extractPayloadMap(params, ret);
        scriptService.execute(action.body, action, table, tableKey, map);
        Log.info(String.format("ACTION [%s -> %s.%s] completed", action.name, table, tableKey));

    }
}
