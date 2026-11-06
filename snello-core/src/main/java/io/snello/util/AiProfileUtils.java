package io.snello.util;

import io.quarkus.logging.Log;
import io.snello.model.pojo.EntityAiProfile;
import io.snello.model.pojo.FieldAiProfile;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

import java.util.Map;

/**
 * (De)serialization of the {@code ai_profile} JSON column on {@code metadatas} / {@code fielddefinitions}.
 * <ul>
 *     <li>reads are lenient: a corrupt stored value is logged and treated as empty, never breaks the catalog;</li>
 *     <li>writes are strict: {@link #normalizeColumn} rejects invalid JSON with {@link IllegalArgumentException}.</li>
 * </ul>
 */
public final class AiProfileUtils {

    public static final String AI_PROFILE = "ai_profile";

    private static final JsonMapper MAPPER = JsonMapper.builder()
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .build();

    private AiProfileUtils() {
    }

    public static EntityAiProfile parseEntity(String json) {
        return parse(json, EntityAiProfile.class, EntityAiProfile.EMPTY);
    }

    public static FieldAiProfile parseField(String json) {
        return parse(json, FieldAiProfile.class, FieldAiProfile.EMPTY);
    }

    /** JSON for the column, or {@code null} when the profile carries nothing (keeps the column NULL). */
    public static String toJson(EntityAiProfile profile) {
        return profile == null || profile.isEmpty() ? null : MAPPER.writeValueAsString(profile);
    }

    public static String toJson(FieldAiProfile profile) {
        return profile == null || profile.isEmpty() ? null : MAPPER.writeValueAsString(profile);
    }

    /**
     * Prepares an incoming REST/import row for JDBC: accepts {@code ai_profile} as a JSON object or as a JSON
     * string, validates it against {@code type}, and stores it back as a canonical JSON string (or null).
     *
     * @throws IllegalArgumentException when the value is not valid JSON for {@code type}
     */
    public static void normalizeColumn(Map<String, Object> row, Class<?> type) {
        if (row == null || !row.containsKey(AI_PROFILE)) {
            return;
        }
        Object raw = row.get(AI_PROFILE);
        if (raw == null || (raw instanceof String s && s.isBlank())) {
            row.put(AI_PROFILE, null);
            return;
        }
        try {
            Object typed = raw instanceof String s
                    ? MAPPER.readValue(s, type)
                    : MAPPER.convertValue(raw, type);
            String canonical = typed instanceof EntityAiProfile e ? toJson(e)
                    : typed instanceof FieldAiProfile f ? toJson(f)
                    : MAPPER.writeValueAsString(typed);
            row.put(AI_PROFILE, canonical);
            Log.debugf("ai_profile normalized type=%s value=%s", type.getSimpleName(), canonical);
        } catch (JacksonException e) {
            throw new IllegalArgumentException("invalid ai_profile for " + type.getSimpleName() + ": " + e.getOriginalMessage(), e);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("invalid ai_profile for " + type.getSimpleName() + ": " + e.getMessage(), e);
        }
    }

    private static <T> T parse(String json, Class<T> type, T empty) {
        if (json == null || json.isBlank()) {
            return empty;
        }
        try {
            T value = MAPPER.readValue(json, type);
            return value == null ? empty : value;
        } catch (JacksonException e) {
            Log.warnf("ignoring unreadable ai_profile type=%s error=%s value=%s", type.getSimpleName(), e.getOriginalMessage(), json);
            return empty;
        }
    }
}
