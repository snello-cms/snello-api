package io.snello.model.events;

import io.snello.model.AiTool;

import java.util.Map;

public class AiToolCreateUpdateEvent {
    public AiTool aiTool;

    public AiToolCreateUpdateEvent(AiTool aiTool) {
        this.aiTool = aiTool;
    }

    public AiToolCreateUpdateEvent(Map<String, Object> map) {
        this.aiTool = new AiTool(map);
    }

    @Override
    public String toString() {
        return "AiToolCreateUpdateEvent{" +
                "aiTool=" + aiTool +
                '}';
    }
}
