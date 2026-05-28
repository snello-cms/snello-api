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
import java.util.concurrent.CompletableFuture;
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
        String condition = ActionUtils.conditionByMethod(methodName);
        Object[] paramsSnapshot = context.getParameters() == null
                ? new Object[0]
                : Arrays.copyOf(context.getParameters(), context.getParameters().length);
        String configuredPhase = resolveConfiguredPhase(condition, paramsSnapshot);
        String params = Arrays.toString(paramsSnapshot);
        Log.info(String.format("ACTION [%s.%s] called with params %s", className, methodName, params));

        if (ActionUtils.PHASE_PRE.equals(configuredPhase)) {
            runSyncPreAction(className, methodName, condition, paramsSnapshot);
        }
        Object ret = context.proceed();
        if (ActionUtils.PHASE_POST.equals(configuredPhase)) {
            triggerAsyncPostAction(className, methodName, condition, paramsSnapshot, ret);
        }
        return ret;
    }

    private String resolveConfiguredPhase(String condition, Object[] params) throws Exception {
        if (condition == null || params == null || params.length < 1) {
            return null;
        }
        if (!(params[0] instanceof String)) {
            return null;
        }

        String table = (String) params[0];
        Action actionPre = metadataService.actionsMap().get(ActionUtils.actionKey(table, condition, ActionUtils.PHASE_PRE));
        Action actionPost = metadataService.actionsMap().get(ActionUtils.actionKey(table, condition, ActionUtils.PHASE_POST));

        boolean hasPre = actionPre != null && actionPre.body != null && !actionPre.body.trim().isEmpty();
        boolean hasPost = actionPost != null && actionPost.body != null && !actionPost.body.trim().isEmpty();

        if (hasPre && hasPost) {
            Log.warn(String.format("ACTION [%s.%s] found both PRE and POST actions. Using PRE only.", table, condition));
            return ActionUtils.PHASE_PRE;
        }
        if (hasPre) {
            return ActionUtils.PHASE_PRE;
        }
        if (hasPost) {
            return ActionUtils.PHASE_POST;
        }
        return null;
    }

    private void runSyncPreAction(String className, String methodName, String condition, Object[] params) throws Exception {
        if (condition == null) {
            return;
        }
        try {
            runConfiguredAction(condition, ActionUtils.PHASE_PRE, params, null);
        } catch (Exception e) {
            Log.error(String.format("ACTION [%s.%s] pre execution failed", className, methodName), e);
            throw e;
        }
    }

    private void triggerAsyncPostAction(String className, String methodName, String condition, Object[] params, Object ret) {
        if (condition == null) {
            return;
        }
        CompletableFuture.runAsync(() -> {
            try {
                runConfiguredAction(condition, ActionUtils.PHASE_POST, params, ret);
            } catch (Exception e) {
                Log.error(String.format("ACTION [%s.%s] post async execution failed", className, methodName), e);
            }
        });
    }

    private void runConfiguredAction(String condition, String phase, Object[] params, Object ret) throws Exception {
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

        Action action = metadataService.actionsMap().get(ActionUtils.actionKey(table, condition, phase));
        if (action == null || action.body == null || action.body.trim().isEmpty()) {
            return;
        }

        Map<String, Object> map = ActionUtils.extractPayloadMap(params, ret);
        scriptService.execute(action.body, action, table, tableKey, map);
        Log.info(String.format("ACTION [%s:%s -> %s.%s] completed", action.name, ActionUtils.normalizePhase(action.phase), table, tableKey));

    }
}
