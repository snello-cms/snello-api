package io.snello.service;

import io.quarkus.logging.Log;
import io.snello.api.service.JdbcRepository;
import io.snello.api.service.MailService;
import io.snello.api.service.StorageService;
import io.snello.model.Action;
import jakarta.inject.Inject;
import jakarta.enterprise.context.ApplicationScoped;

import javax.script.Bindings;
import javax.script.ScriptEngine;
import java.util.HashMap;
import java.util.Map;

@ApplicationScoped
public class ScriptService {

    @Inject
    MetadataService metadataService;

    @Inject
    StorageService documentsService;

    @Inject
    MailService mailService;

    @Inject
    JdbcRepository jdbcRepository;

    // Created from the factory directly: ScriptEngineManager discovers engines through the thread context
    // classloader's META-INF/services, which finds nothing under Quarkus ("nashorn engine is not available"), so no
    // action ever ran and every PRE action turned its table's writes into 500s.
    ScriptEngine engine = new org.openjdk.nashorn.api.scripting.NashornScriptEngineFactory().getScriptEngine();

    public void execute(String js, Action action, String table, String table_key, Map<String, Object> map) throws Exception {
        execute(js, action, table, table_key, null, map);
    }

    /**
     * @param table_key key column name (e.g. {@code uuid}); kept for existing scripts
     * @param key       key value of the row the action fires for (bound as {@code key})
     */
    public void execute(String js, Action action, String table, String table_key, Object key, Map<String, Object> map) throws Exception {
        if (js == null || js.trim().isEmpty()) {
            throw new Exception("javascript source is null or empty");
        }
        if (engine == null) {
            throw new Exception("nashorn engine is not available");
        }
        Log.info("executeJs javascript_function : " + action.body + " for table " + table + " with map " + map);
        Bindings bindings = engine.createBindings();
        bindings.put("action", action);
        bindings.put("metadata", metadataService);
        bindings.put("db", jdbcRepository);
        bindings.put("documents", documentsService);
        bindings.put("mail", mailService);
        bindings.put("table", table);
        bindings.put("table_key", table_key);
        bindings.put("key", key);
        bindings.put("map", map);
        engine.eval(js, bindings);
    }

}
