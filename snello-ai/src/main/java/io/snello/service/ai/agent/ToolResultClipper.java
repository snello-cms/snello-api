package io.snello.service.ai.agent;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.util.Iterator;
import java.util.List;

@ApplicationScoped
public class ToolResultClipper {
    private static final int MAX_CHARS = CompositeToolProvider.MAX_PAYLOAD_CHARS;
    private static final int MAX_ITEMS = 6;
    private static final List<String> COMPACT_FIELDS = List.of(
            "uuid", "name", "title", "preview", "description", "table_name", "catalogPageUrl",
            "entity", "status", "creation_date", "last_update_date");

    @Inject
    JsonMapper mapper;

    public String clip(String value, String resultType, String resultPath) {
        if (value == null) {
            value = "";
        }
        boolean oversized = value.length() > MAX_CHARS;
        if (oversized) {
            String preview = tryJsonPreview(value, resultType, resultPath);
            if (preview != null) {
                if (preview.length() <= MAX_CHARS) {
                    return preview;
                }
                return preview.substring(0, MAX_CHARS) + "…";
            }
            String truncated = value.substring(0, MAX_CHARS) + "…";
            if (resultType != null && !resultType.isBlank()) {
                return wrapRaw(truncated, resultType, resultPath);
            }
            return truncated;
        }
        if (resultType != null && !resultType.isBlank()) {
            String wrapped = tryWrapFull(value, resultType, resultPath);
            if (wrapped != null) {
                return wrapped;
            }
            return wrapRaw(value, resultType, resultPath);
        }
        return value;
    }

    private String tryWrapFull(String value, String resultType, String resultPath) {
        try {
            JsonNode root = mapper.readTree(value);
            JsonNode itemsNode = resolveItems(root, resultPath);
            ObjectNode envelope = mapper.createObjectNode();
            envelope.put("resultType", resultType);
            if (resultPath != null && !resultPath.isBlank()) {
                envelope.put("resultPath", resultPath);
            }
            copyTabularMeta(root, envelope);
            if (itemsNode != null && itemsNode.isArray()) {
                envelope.set("items", itemsNode);
                envelope.put("total", itemsNode.size());
            } else if (itemsNode != null && itemsNode.isObject()) {
                ArrayNode items = envelope.putArray("items");
                items.add(itemsNode);
                envelope.put("total", 1);
            } else {
                envelope.set("data", root);
            }
            return mapper.writeValueAsString(envelope);
        } catch (Exception ignored) {
            return null;
        }
    }

    private String tryJsonPreview(String value, String resultType, String resultPath) {
        try {
            JsonNode root = mapper.readTree(value);
            JsonNode itemsNode = resolveItems(root, resultPath);
            if (itemsNode == null) {
                return null;
            }
            ObjectNode envelope = mapper.createObjectNode();
            envelope.put("preview", true);
            if (resultType != null && !resultType.isBlank()) {
                envelope.put("resultType", resultType);
            }
            if (resultPath != null && !resultPath.isBlank()) {
                envelope.put("resultPath", resultPath);
            }
            copyTabularMeta(root, envelope);
            if (itemsNode.isArray()) {
                int total = itemsNode.size();
                envelope.put("total", total);
                ArrayNode items = envelope.putArray("items");
                for (int i = 0; i < Math.min(total, MAX_ITEMS); i++) {
                    items.add(compactItem(itemsNode.get(i)));
                }
                if (total > MAX_ITEMS) {
                    envelope.put("truncated", true);
                }
            } else {
                ArrayNode items = envelope.putArray("items");
                items.add(compactItem(itemsNode));
                envelope.put("total", 1);
            }
            return mapper.writeValueAsString(envelope);
        } catch (Exception ignored) {
            return null;
        }
    }

    private String wrapRaw(String value, String resultType, String resultPath) {
        try {
            ObjectNode envelope = mapper.createObjectNode();
            if (resultType != null && !resultType.isBlank()) {
                envelope.put("resultType", resultType);
            }
            if (resultPath != null && !resultPath.isBlank()) {
                envelope.put("resultPath", resultPath);
            }
            envelope.put("raw", value);
            return mapper.writeValueAsString(envelope);
        } catch (Exception ignored) {
            return value;
        }
    }

    private void copyTabularMeta(JsonNode root, ObjectNode envelope) {
        if (root == null || !root.isObject()) {
            return;
        }
        JsonNode title = root.get("title");
        if (title != null && title.isTextual() && !title.asText().isBlank()) {
            envelope.put("title", title.asText());
        }
        JsonNode columns = root.get("columns");
        if (columns != null && columns.isArray() && !columns.isEmpty()) {
            envelope.set("columns", columns);
        }
    }

    private JsonNode resolveItems(JsonNode root, String resultPath) {
        if (root != null && root.isObject() && root.has("items")) {
            JsonNode items = root.get("items");
            if (items != null && !items.isNull()) {
                return items;
            }
        }
        JsonNode node = navigate(root, resultPath);
        if (node == null || node.isNull()) {
            if (root != null && (root.isArray() || root.isObject())) {
                return root;
            }
            return null;
        }
        return node;
    }

    private JsonNode navigate(JsonNode root, String resultPath) {
        if (resultPath == null || resultPath.isBlank()) {
            return root;
        }
        JsonNode current = root;
        for (String segment : resultPath.split("\\.")) {
            if (segment.isBlank()) {
                continue;
            }
            if (current == null || !current.isObject()) {
                return null;
            }
            current = current.get(segment);
        }
        return current;
    }

    private JsonNode compactItem(JsonNode node) {
        if (node == null || node.isNull()) {
            return mapper.nullNode();
        }
        if (!node.isObject()) {
            return node;
        }
        ObjectNode compact = mapper.createObjectNode();
        for (String field : COMPACT_FIELDS) {
            if (node.has(field) && !node.get(field).isNull()) {
                compact.set(field, node.get(field));
            }
        }
        if (compact.isEmpty()) {
            Iterator<String> names = node.propertyNames().iterator();
            int kept = 0;
            while (names.hasNext() && kept < 8) {
                String name = names.next();
                JsonNode value = node.get(name);
                if (value != null && !value.isObject() && !value.isArray()) {
                    compact.set(name, value);
                    kept++;
                }
            }
        }
        return compact;
    }
}
