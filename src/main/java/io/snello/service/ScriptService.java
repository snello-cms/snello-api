package io.snello.service;

import io.quarkus.logging.Log;
import io.snello.api.service.MailService;
import io.snello.model.Action;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import javax.script.Bindings;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import java.util.HashMap;
import java.util.Map;

@Singleton
public class ScriptService {

    @Inject
    MetadataService metadataService;

    @Inject
    MailService mailService;

    ScriptEngine engine = new ScriptEngineManager().getEngineByName("nashorn");

    public void execute(String js, Action action, String table, String table_key, Map<String, Object> map) throws Exception {
        if (js == null || js.trim().isEmpty()) {
            throw new Exception("javascript source is null or empty");
        }
        if (engine == null) {
            throw new Exception("nashorn engine is not available");
        }
        Map<String, Object> values = new HashMap<>();
        Log.info("executeJs javascript_function : ");
        Bindings bindings = engine.createBindings();
        bindings.put("action", action);
        bindings.put("metadataService", metadataService);
        bindings.put("mailService", mailService);
        bindings.put("values", values);
        bindings.put("table", table);
        bindings.put("table_key", table_key);
        bindings.put("map", map);
        engine.eval(js, bindings);
    }

}
