package io.snello.model.events;

import io.snello.model.UrlMapRule;

import java.util.Map;

public class UrlMapRuleCreateUpdateEvent {
    public UrlMapRule urlMapRule;

    public UrlMapRuleCreateUpdateEvent(UrlMapRule urlMapRule) {
        this.urlMapRule = urlMapRule;
    }

    public UrlMapRuleCreateUpdateEvent(Map<String, Object> map) {
        this.urlMapRule = new UrlMapRule(map);
    }

    @Override
    public String toString() {
        return "UrlMapRuleCreateUpdateEvent{" +
                "UrlMapRule=" + urlMapRule +
                '}';
    }
}
