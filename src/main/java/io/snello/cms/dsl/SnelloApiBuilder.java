package io.snello.cms.dsl;

import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MutableHttpRequest;
import io.micronaut.http.client.DefaultHttpClient;
import io.micronaut.http.client.RxHttpClient;
import io.micronaut.security.authentication.UsernamePasswordCredentials;
import io.micronaut.security.token.jwt.render.BearerAccessRefreshToken;
import io.reactivex.Flowable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URL;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static io.micronaut.http.HttpRequest.GET;
import static io.snello.cms.management.AppConstants.API_PATH;

public class SnelloApiBuilder {

    Logger logger = LoggerFactory.getLogger(getClass());

    static String HOST = "http://localhost:8080";
    static String login_uri = "/login";
    static String api_uri = API_PATH;

    private String token;
    private String username;
    private String password;

    private String table;
    private String uuid;
    private String sort;
    private String limit;
    private String start;

    private MetadataDsl metadataDsl;


    public SnelloApiBuilder(String username, String password) {
        this.username = username;
        this.password = password;

    }

    public SnelloApiBuilder(String token) {
        this.token = token;
    }

    public SnelloApiBuilder login() throws Exception {
        isLoggable();
        logger.info("login: " + login_uri);
        try (RxHttpClient client = new DefaultHttpClient(new URL(HOST))) {
            UsernamePasswordCredentials credentials = new UsernamePasswordCredentials(this.username, this.password);
            HttpRequest request = HttpRequest.POST("/login", credentials);
            BearerAccessRefreshToken bearerAccessRefreshToken = client.toBlocking().retrieve(request, BearerAccessRefreshToken.class);
            if (bearerAccessRefreshToken != null) {
                this.token = bearerAccessRefreshToken.getAccessToken();
                logger.info("token: " + this.token);
            }
        } catch (Exception e) {
            logger.error("login error:", e);
        }
        return this;
    }

    private void isLoggable() throws Exception {
        if (this.username == null && this.password == null) {
            throw new Exception("username or password is invalid");
        }
    }

    public SnelloApiBuilder table(String table) {
        this.table = table;
        return this;
    }

    public SnelloApiBuilder uuid(String uuid) {
        this.uuid = uuid;
        return this;
    }

    public SnelloApiBuilder sort(String sort) {
        this.sort = sort;
        return this;
    }

    public SnelloApiBuilder limit(String limit) {
        this.limit = limit;
        return this;
    }

    public SnelloApiBuilder start(String start) {
        this.start = start;
        return this;
    }

    public SnelloApiBuilder list(String table) throws Exception {
        table(table);
        return list();
    }

    public SnelloApiBuilder list(String table, String start, String limit) throws Exception {
        start(start).limit(limit).table(table);
        return list();
    }

    public SnelloApiBuilder single(String table, String uuid) throws Exception {
        uuid(uuid).table(table);
        return list();
    }


    private SnelloApiBuilder list() throws Exception {

        StringBuffer uri = new StringBuffer();
        if (start != null) {
            uri.append("&").append("start=" + start);
        }
        if (sort != null) {
            uri.append("&").append("sort=" + sort);
        }
        if (limit != null) {
            uri.append("&").append("limit=" + limit);
        }
        String uriFinal = null;
        if (uri.length() < 1) {
            uriFinal = api_uri + "/" + this.table;
        } else {
            uriFinal = api_uri + "/" + this.table + "?" + uri.toString().substring(1);
        }
        logger.info("list: " + uriFinal);
        try (RxHttpClient client = new DefaultHttpClient(new URL(HOST))) {
            MutableHttpRequest<Object> request = GET(uriFinal);
            if (this.token != null) {
                request = request.header("Authorization", "Bearer " + this.token);
            }
            Flowable<HttpResponse<List>> call = client.exchange(
                    request, List.class
            );
            HttpResponse<List> response = call.blockingFirst();
            Optional<List> metadataResponse = response.getBody(List.class);
            if (response.getStatus().equals(HttpStatus.OK)) {
                if (metadataResponse.isPresent()) {
                    List<Map<String, Object>> map = metadataResponse.get();
                    if (map != null && !map.isEmpty()) {
                        logger.info(map.toString());
                    }
                }
            }
        } catch (Exception e) {
            logger.error("get table error: ", e);
        }
        return this;
    }

    public SnelloApiBuilder single() throws Exception {
        StringBuffer uri = new StringBuffer(api_uri + "/" + this.table);
        if (uuid == null) {
            throw new Exception(" uuid is null");
        }
        uri.append("/" + uuid);
        logger.info("single: " + uri.toString());
        try (RxHttpClient client = new DefaultHttpClient(new URL(HOST))) {
            MutableHttpRequest<Map> request = GET(uri.toString());
            if (this.token != null) {
                request = request.header("Authorization", "Bearer " + this.token);
            }
            Flowable<HttpResponse<Map>> call = client.exchange(
                    request, Map.class
            );
            HttpResponse<Map> response = call.blockingFirst();
            Optional<Map> metadataResponse = response.getBody(Map.class);
            if (response.getStatus().equals(HttpStatus.OK)) {
                if (metadataResponse.isPresent()) {
                    Map<String, Object> map = metadataResponse.get();
                    if (map != null && !map.isEmpty()) {
                        logger.info(map.toString());
                    }
                }
            }
        } catch (Exception e) {
            logger.error("get table error: ", e);
        }
        return this;
    }


}
