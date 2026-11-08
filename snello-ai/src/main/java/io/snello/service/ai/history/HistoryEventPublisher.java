package io.snello.service.ai.history;

import io.quarkus.logging.Log;
import io.snello.model.ai.pojo.HistoryEvent;
import io.snello.model.ai.pojo.HistoryEvent.Type;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.inject.Inject;

/**
 * Fire-and-forget history tracking via CDI async events (no external queue).
 */
@ApplicationScoped
public class HistoryEventPublisher {

    @Inject
    Event<HistoryEvent> history;

    public void login(HistoryEvent.Channel channel, String userId, String username, String sessionId) {
        track(channel, userId, username, Type.LOGIN, null, sessionId, null);
    }

    public void logout(HistoryEvent.Channel channel, String userId, String username, String sessionId) {
        track(channel, userId, username, Type.LOGOUT, null, sessionId, null);
    }

    public void request(HistoryEvent.Channel channel, String userId, String username, String body, String sessionId,
            String model) {
        track(channel, userId, username, Type.REQUEST, body, sessionId, model);
    }

    public void response(HistoryEvent.Channel channel, String userId, String username, String body, String sessionId,
            String model) {
        track(channel, userId, username, Type.RESPONSE, body, sessionId, model);
    }

    public void error(HistoryEvent.Channel channel, String userId, String username, String code, String message,
            String sessionId, String model) {
        track(channel, userId, username, Type.ERROR, code + ": " + message, sessionId, model);
    }

    public void feedback(HistoryEvent.Channel channel, String userId, String username, String body, String sessionId,
            String model) {
        track(channel, userId, username, Type.FEEDBACK, body, sessionId, model);
    }

    public void tool(HistoryEvent.Channel channel, String userId, String username, String body, String sessionId,
            String model) {
        track(channel, userId, username, Type.TOOL, body, sessionId, model);
    }

    private void track(HistoryEvent.Channel channel, String userId, String username, Type type, String body,
            String sessionId, String model) {
        HistoryEvent event = HistoryEvent.of(channel, userId, username, type, body, sessionId, model);
        try {
            history.fireAsync(event);
        } catch (Exception failure) {
            Log.warnf(failure, "History event dropped, session: %s, type: %s", sessionId, type);
        }
    }
}
