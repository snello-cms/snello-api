package io.snello.util;

import tools.jackson.databind.json.JsonMapper;

import java.util.Map;

public class JsonUtils {

    public static Map<String, Object> fromJson(String body) throws Exception {
        JsonMapper mapper = JsonMapper.builder().build();
        return mapper.readValue(body, Map.class);
    }
}
