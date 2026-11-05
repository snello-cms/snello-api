package io.snello.service;

import io.quarkus.logging.Log;
import io.quarkus.qute.Engine;
import io.quarkus.qute.Template;
import io.quarkus.qute.TemplateExtension;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import jakarta.ws.rs.core.MultivaluedMap;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;

@Singleton
public class TemplateService {

    @Inject
    Engine engine;

    @Inject
    Api api;

    public String parse(Map<String, Object> map, MultivaluedMap<String, String> queryParameters) {
        Log.info("creating content for template " + map);
        if (map == null) {
            return "";
        }
        Object body = map.get("body");
        if (!(body instanceof String) || ((String) body).trim().isEmpty()) {
            return "";
        }
        Template myTemplate = engine.parse((String) body);
        return myTemplate
                .data("values", map)
                .data("queryParameters", queryParameters)
                .data("api", api)
                .render();
    }

    @TemplateExtension
    public static String dateFormat(LocalDateTime date, String pattern) {
        try {
            if (date == null) {
                return "";
            }
            if (pattern == null || pattern.trim().isEmpty()) {
                return date.toString();
            }
            DateTimeFormatter dtf = DateTimeFormatter.ofPattern(pattern);
            return date.format(dtf);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return date != null ? date.toString() : "";
    }
}
