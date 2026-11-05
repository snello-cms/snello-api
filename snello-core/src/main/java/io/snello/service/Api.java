package io.snello.service;

import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.inject.Singleton;
import jakarta.ws.rs.core.MultivaluedMap;

import java.util.List;
import java.util.Map;
import java.util.Objects;

@Singleton
@Named("api")
public class Api {

    @Inject
    ApiService apiService;

    public List<Map<String, Object>> list(String table,
                                          String sort,
                                          int limit,
                                          int start,
                                          MultivaluedMap<String, String> queryParameters) throws Exception {
        return apiService.list(table, queryParameters, sort, limit, start);
    }


    public Map<String, Object> single(String table,
                                      String uuid,
                                      MultivaluedMap<String, String> queryParameters) throws Exception {
        Objects.requireNonNull(table, "table cannot be null");
        Objects.requireNonNull(uuid, "uuid cannot be null");
        String key = apiService.table_key(table);
        return apiService.fetch(queryParameters, table, uuid, key);
    }

}
