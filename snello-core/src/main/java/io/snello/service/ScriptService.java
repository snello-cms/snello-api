package io.snello.service;

import io.quarkus.logging.Log;
import io.snello.api.service.JdbcRepository;
import io.snello.api.service.MailService;
import io.snello.api.service.StorageService;
import io.snello.model.Action;
import io.snello.service.http.HttpCallService;
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

    @Inject
    HttpCallService httpCallService;

    // call(GET, url[, body[, headers]]) sugar over the `http` binding; GET/POST/... are plain strings.
    // result() is parsed with the script's own JSON.parse, so it is a real JS object/array: r.name, list.map(...),
    // JSON.stringify(r) all work (a Java Map/List from Jackson would not support map/filter/stringify).
    // Kept on one line: a separate eval, so line/column numbers of the user script stay intact.
    private static final String PRELUDE = """
            var GET='GET',POST='POST',PUT='PUT',PATCH='PATCH',DELETE='DELETE',HEAD='HEAD';\
            function call(m,u,b,h){\
            var r=http.call(m,u,b==null?null:b,h==null?null:h);\
            return {\
            status:function(){return r.status();},\
            ok:function(){return r.ok();},\
            body:function(){return r.body();},\
            error:function(){return r.error();},\
            result:function(){\
            var s=r.body();if(s==null)return null;s=String(s);var t=s.trim();if(t==='')return null;\
            var c=t.charAt(0);if(c!=='{'&&c!=='[')return s;\
            try{return JSON.parse(t);}catch(e){return s;}},\
            toString:function(){return String(r);}};}""";

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
        bindings.put("http", httpCallService);
        // JBoss logger per script: category snello.script.<action name>, so log lines say which action wrote them.
        // log.info('x'), log.infof('renter %s saved', key), log.warn(...), log.error(...), log.debug(...)
        String loggerName = "snello.script." + (action != null && action.name != null && !action.name.isBlank()
                ? action.name.replaceAll("[^A-Za-z0-9_.-]", "_") : "anonymous");
        bindings.put("log", org.jboss.logging.Logger.getLogger(loggerName));
        bindings.put("table", table);
        bindings.put("table_key", table_key);
        bindings.put("key", key);
        bindings.put("map", map);
        engine.eval(PRELUDE, bindings); // separate eval: keeps line/column numbers of the user script intact
        engine.eval(js, bindings);
    }

}
