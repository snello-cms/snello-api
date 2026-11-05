package io.snello.service.ai.tools;

import jakarta.enterprise.context.ApplicationScoped;

import java.time.Duration;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

@ApplicationScoped
public class AiPaginationContextStore {

    public static final String PAGINATION_CONTEXT_MARKER = "__PAGINATION_CONTEXT__";
    private static final int MAX_CONTEXTS = 500;
    private static final Duration CONTEXT_TTL = Duration.ofMinutes(30);

    private final Map<String, CacheEntry> contexts = new LinkedHashMap<>(16, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, CacheEntry> eldest) {
            return size() > MAX_CONTEXTS;
        }
    };

    public synchronized void save(String conversationId, LastSearchContext context) {
        if (conversationId == null || conversationId.isBlank() || context == null) {
            return;
        }
        pruneExpiredEntries();
        contexts.put(conversationId, new CacheEntry(context));
    }

    public synchronized LastSearchContext get(String conversationId) {
        if (conversationId == null || conversationId.isBlank()) {
            return null;
        }
        CacheEntry entry = contexts.get(conversationId);
        if (entry == null) {
            return null;
        }
        if (entry.isExpired()) {
            contexts.remove(conversationId);
            return null;
        }
        entry.touch();
        return entry.context;
    }

    private void pruneExpiredEntries() {
        Iterator<Map.Entry<String, CacheEntry>> iterator = contexts.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, CacheEntry> entry = iterator.next();
            if (entry.getValue().isExpired()) {
                iterator.remove();
            }
        }
    }

    private static class CacheEntry {
        private final LastSearchContext context;
        private long expiresAt;

        private CacheEntry(LastSearchContext context) {
            this.context = context;
            touch();
        }

        private void touch() {
            this.expiresAt = System.currentTimeMillis() + CONTEXT_TTL.toMillis();
        }

        private boolean isExpired() {
            return System.currentTimeMillis() > expiresAt;
        }
    }

    public static class LastSearchContext {
        public String entityName;
        public HashMap<String, String> params;
        public int limit;
        public int nextStart;
        public boolean hasMore;

        public static LastSearchContext of(String entityName, Map<String, String> params, int limit, int nextStart,
                boolean hasMore) {
            LastSearchContext context = new LastSearchContext();
            context.entityName = entityName;
            context.params = params == null ? new HashMap<>() : new HashMap<>(params);
            context.limit = limit;
            context.nextStart = nextStart;
            context.hasMore = hasMore;
            return context;
        }

        public Map<String, Object> toMap() {
            Map<String, Object> map = new HashMap<>();
            map.put("entityName", entityName);
            map.put("params", params == null ? new HashMap<>() : new HashMap<>(params));
            map.put("limit", limit);
            map.put("nextStart", nextStart);
            map.put("hasMore", hasMore);
            return map;
        }
    }
}