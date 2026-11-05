package io.snello.service.ai;

import jakarta.enterprise.context.RequestScoped;

@RequestScoped
public class AiRequestContext {

    private String conversationId;

    public String getConversationId() {
        return conversationId;
    }

    public void setConversationId(String conversationId) {
        this.conversationId = conversationId;
    }
}