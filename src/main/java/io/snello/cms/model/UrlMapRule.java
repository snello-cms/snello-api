package io.snello.cms.model;

import io.micronaut.http.HttpMethod;
import io.micronaut.security.config.InterceptUrlMapPattern;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

public class UrlMapRule {

    public String uuid;
    // e.g. /health
    public String pattern;
    //*  e.g. 'ROLE_USER','ROLE_ADMIN'
    public String access;
    // If the provided http method is null, the pattern will match all methods.
    public String httpMethods;

    public UrlMapRule() {

    }

    public UrlMapRule(String uuid,
                      String pattern,
                      String access,
                      String httpMethods) {
        this.uuid = uuid;
        this.pattern = pattern;
        this.access = access;
        this.httpMethods = httpMethods;
    }


    public UrlMapRule(Map<String, Object> map) {
        super();
        fromMap(map, this);
    }

    public List<InterceptUrlMapPattern> toInterceptUrlMapPattern() {
        List<InterceptUrlMapPattern> urls = new ArrayList<>();
        List<String> accessList = null;
        if (access != null && !access.trim().isEmpty()) {
            accessList = Arrays.asList(access.split(",|;"));
        }

        if (httpMethods != null && !httpMethods.trim().isEmpty()) {
            String[] ms = httpMethods.split(",|;");
            for (String mms : ms) {
                HttpMethod httpMethodEnum = HttpMethod.valueOf(mms.trim());
                urls.add(new InterceptUrlMapPattern(pattern, accessList, httpMethodEnum));
            }
        } else {
            urls.add(new InterceptUrlMapPattern(pattern, accessList, null));
        }
        return urls;
    }

    @Override
    public String toString() {
        return "UrlMapRule{" +
                "uuid='" + uuid + '\'' +
                ", pattern='" + pattern + '\'' +
                ", access='" + access + '\'' +
                ", httpMethods='" + httpMethods + '\'' +
                '}';
    }

    public static UrlMapRule fromMap(Map<String, Object> map, UrlMapRule urlMapRule) {
        if (map.get("uuid") instanceof String) {
            urlMapRule.uuid = (String) map.get("uuid");
        }
        if (map.get("pattern") instanceof String) {
            urlMapRule.pattern = (String) map.get("pattern");
        }
        if (map.get("access") instanceof String) {
            urlMapRule.access = (String) map.get("access");
        }

        if (map.get("httpMethods") instanceof String) {
            urlMapRule.httpMethods = (String) map.get("httpMethods");
        }
        return urlMapRule;
    }

}
