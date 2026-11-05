package io.snello.model;

import io.quarkus.runtime.annotations.RegisterForReflection;

import java.util.List;
import java.util.Map;

@RegisterForReflection
public class SearchResult {

    public long size;
    public long totalCount;
    public int start;
    public int limit;
    public boolean hasMore;
    public long remaining;
    public int nextStart;
    public List<Map<String, Object>> records;

    public SearchResult() {
    }

    public SearchResult(long size, long totalCount, int start, int limit, boolean hasMore, long remaining, int nextStart,
            List<Map<String, Object>> records) {
        this.size = size;
        this.totalCount = totalCount;
        this.start = start;
        this.limit = limit;
        this.hasMore = hasMore;
        this.remaining = remaining;
        this.nextStart = nextStart;
        this.records = records;
    }
}
