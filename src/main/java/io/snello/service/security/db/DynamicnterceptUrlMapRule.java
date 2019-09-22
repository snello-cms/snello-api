package io.snello.service.security.db;

import io.micronaut.core.util.AntPathMatcher;
import io.micronaut.core.util.PathMatcher;
import io.micronaut.http.HttpMethod;
import io.micronaut.http.HttpRequest;
import io.micronaut.runtime.event.annotation.EventListener;
import io.micronaut.scheduling.annotation.Async;
import io.micronaut.security.config.InterceptUrlMapPattern;
import io.micronaut.security.rules.SecurityRule;
import io.micronaut.security.rules.SecurityRuleResult;
import io.micronaut.security.rules.SensitiveEndpointRule;
import io.micronaut.web.router.RouteMatch;
import io.snello.model.UrlMapRule;
import io.snello.model.events.DbCreatedEvent;
import io.snello.model.events.UrlMapRuleCreateUpdateEvent;
import io.snello.model.events.UrlMapRuleDeleteEvent;
import io.snello.repository.JdbcRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.*;

import static io.snello.management.AppConstants.URL_MAP_RULES;

@Singleton
public class DynamicnterceptUrlMapRule
        implements SecurityRule {

    Logger logger = LoggerFactory.getLogger(getClass());
    private boolean first = true;

    @Inject
    JdbcRepository jdbcRepository;

    public static final Integer ORDER = SensitiveEndpointRule.ORDER - 500;
    List<InterceptUrlMapPattern> patternList = null;
    AntPathMatcher pathMatcher = null;

    public DynamicnterceptUrlMapRule() {
        pathMatcher = PathMatcher.ANT;
        this.patternList = new ArrayList<>();
        logger.info("DynamicnterceptUrlMapRule up...");
    }

    @EventListener
    public void onStartup(DbCreatedEvent event) {
        logger.info("DbCreatedEvent event load");
        load();
    }


    @Async
    @EventListener
    void urlMapRuleCreateUpdateEvent(UrlMapRuleCreateUpdateEvent urlMapRuleCreateUpdateEvent) {
        logger.info("urlMapRuleCreateUpdateEvent: " + urlMapRuleCreateUpdateEvent.toString());
        load();
    }

    @Async
    @EventListener
    void urlMapRuleDeleteEvent(UrlMapRuleDeleteEvent urlMapRuleDeleteEvent) {
        logger.info("UrlMapRuleDeleteEvent: " + urlMapRuleDeleteEvent.toString());
        load();
    }

    void load() {
        try {
            this.patternList = new ArrayList<>();
            List<Map<String, Object>> lista = jdbcRepository.list(URL_MAP_RULES, "pattern asc");
            if (lista != null) {
                lista.forEach(map -> {
                    UrlMapRule urlMapRule = new UrlMapRule();
                    urlMapRule.fromMap(map, urlMapRule);
                    this.patternList.addAll(urlMapRule.toInterceptUrlMapPattern());
                });
            }
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
        }
    }


//        this.patternList.add(new InterceptUrlMapPattern("/books", Arrays.asList("isAuthenticated()"), null));
//        this.patternList.add(new InterceptUrlMapPattern("/books/grails", Arrays.asList("ROLE_GRAILS", "ROLE_GROOVY"), GET));
//        this.patternList.add(new InterceptUrlMapPattern("/users/admin", Arrays.asList("ADMIN", "USER"), GET));
//        pattern:
//        http - httpMethod:GET
//        access:
//           -isAnonymous()
//                -
//        pattern: /books
//        access:
//
//                -
//        pattern: /books / grails
//        http - httpMethod:GET
//        access:
//            -ROLE_GRAILS
//           - ROLE_GROOVY


    protected List<InterceptUrlMapPattern> getPatternList() {
        return this.patternList;
    }

    @Override
    public int getOrder() {
        return ORDER;
    }

    @Override
    public SecurityRuleResult check(HttpRequest request, @Nullable RouteMatch routeMatch, @Nullable Map<String, Object> claims) {
        final String path = request.getUri().getPath();
        final HttpMethod httpMethod = request.getMethod();
        logger.info("check: " + path + ", httpMethod: " + httpMethod + ", claims: " + claims);
//        if (first) {
//            first = false;
//            load();
//            logger.info("LOAD URL MAP RULES");
//        }
        if (getPatternList().size() == 0) {
            logger.info("NO URL MAP RULES");
            return SecurityRuleResult.ALLOWED;
        }
        Optional<InterceptUrlMapPattern> matchedPattern = getPatternList()
                .stream()
                .filter(p -> p.getHttpMethod().map(method -> method.equals(httpMethod)).orElse(true))
                .filter(p -> pathMatcher.matches(p.getPattern(), path))
                .findFirst();
        if (matchedPattern.isEmpty()) {
            logger.info("REAL check RESULT: no pattern to match " + SecurityRuleResult.ALLOWED);
            return SecurityRuleResult.ALLOWED;
        }

        SecurityRuleResult realResut = matchedPattern
                .map(pattern -> compareRoles(pattern.getAccess(), getRoles(claims)))
                .orElse(SecurityRuleResult.UNKNOWN);
        logger.info("REAL check RESULT: " + realResut.toString());
        return realResut;
    }

    protected List<String> getRoles(Map<String, Object> claims) {
        List<String> roles = new ArrayList();
        if (claims == null) {
            roles.add("isAnonymous()");
        } else {
            if (!claims.isEmpty()) {
                Object rolesObject = claims.get("roles");
                if (rolesObject != null) {
                    if (rolesObject instanceof Iterable) {
                        Iterator var4 = ((Iterable) rolesObject).iterator();
                        while (var4.hasNext()) {
                            Object o = var4.next();
                            roles.add(o.toString());
                        }
                    } else {
                        roles.add(rolesObject.toString());
                    }
                }
            }
//            roles.add("isAnonymous()");
//            roles.add("isAuthenticated()");
        }

        return roles;
    }

    protected SecurityRuleResult compareRoles(List<String> requiredRoles, List<String> grantedRoles) {
        if (requiredRoles.contains("isAnonymous()")) {
            logger.info("isAnonymous => ALL ROLES ARE OK");
            return SecurityRuleResult.ALLOWED;
        }
        if (requiredRoles.contains("isAuthenticated()") && grantedRoles != null && !grantedRoles.isEmpty()) {
            logger.info("isAuthenticated => THE USER HAVE SOME RULE");
            return SecurityRuleResult.ALLOWED;
        }
        requiredRoles = new ArrayList<>(requiredRoles);
        requiredRoles.retainAll(grantedRoles);
        if (requiredRoles.isEmpty()) {
            logger.info("no required role!!");
            return SecurityRuleResult.REJECTED;
        } else {
            logger.info("required role is present!");
            return SecurityRuleResult.ALLOWED;
        }
    }
}
