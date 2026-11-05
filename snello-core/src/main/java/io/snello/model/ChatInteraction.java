package io.snello.model;

import io.quarkus.runtime.annotations.RegisterForReflection;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@RegisterForReflection
public class ChatInteraction {

    public String uuid;
    public String conversation_uuid;
    public String user_id;
    public String user_message;
    public String ai_response;
    public LocalDateTime creation_date;

    public Map<String, Object> toMap() {
        Map<String, Object> map = new HashMap<>();
        if (uuid != null) {
            map.put("uuid", uuid);
        }
        if (conversation_uuid != null) {
            map.put("conversation_uuid", conversation_uuid);
        }
        if (user_id != null) {
            map.put("user_id", user_id);
        }
        if (user_message != null) {
            map.put("user_message", user_message);
        }
        if (ai_response != null) {
            map.put("ai_response", ai_response);
        }
        if (creation_date != null) {
            map.put("creation_date", creation_date);
        }
        return map;
    }
}
