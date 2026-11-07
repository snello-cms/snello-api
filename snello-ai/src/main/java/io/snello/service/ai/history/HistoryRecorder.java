package io.snello.service.ai.history;

import io.quarkus.logging.Log;
import io.snello.model.ai.HistoryEntry;
import io.snello.model.ai.HistorySession;
import io.snello.service.ApiService;
import io.snello.service.ai.pojo.HistoryEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.ObservesAsync;
import jakarta.inject.Inject;
import jakarta.ws.rs.core.MultivaluedHashMap;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static io.snello.management.AppConstants.HISTORY_EVENT;
import static io.snello.management.AppConstants.HISTORY_SESSION;
import static io.snello.management.AppConstants.UUID;

@ApplicationScoped
public class HistoryRecorder {

    @Inject
    ApiService apiService;

    void onHistory(@ObservesAsync HistoryEvent event) {
        try {
            persist(event);
        } catch (Exception failure) {
            Log.errorf(failure, "History event not persisted, session: %s, type: %s", event.sessionId(), event.type());
        }
    }

    private void persist(HistoryEvent event) throws Exception {
        if (event.channel() == null || event.type() == null) {
            Log.warnf("Discarding history event without channel or type, session: %s", event.sessionId());
            return;
        }
        Instant occurredAt = occurredAt(event);
        HistoryEntry entry = new HistoryEntry();
        entry.uuid = java.util.UUID.randomUUID().toString();
        entry.occurred_at = occurredAt.toString();
        entry.recorded_at = Instant.now().toString();
        entry.channel = event.channel().value();
        entry.user_id = event.userId();
        entry.username = event.username();
        entry.type = event.type().value();
        entry.body = event.body();
        entry.session_id = event.sessionId();
        entry.model = blankToNull(event.model());
        apiService.create(HISTORY_EVENT, entry.toMap(), UUID);
        recordSession(event, occurredAt);
    }

    private void recordSession(HistoryEvent event, Instant occurredAt) throws Exception {
        if (event.sessionId() == null || event.sessionId().isBlank()) {
            return;
        }
        MultivaluedHashMap<String, String> params = new MultivaluedHashMap<>();
        params.putSingle("session_id", event.sessionId());
        List<Map<String, Object>> rows = apiService.list(HISTORY_SESSION, params, null, 1, 0);
        HistorySession session;
        if (rows == null || rows.isEmpty()) {
            session = new HistorySession();
            session.uuid = java.util.UUID.randomUUID().toString();
            session.session_id = event.sessionId();
            session.user_id = event.userId();
            session.message_count = 0;
            session.started_at = occurredAt.toString();
            apiService.create(HISTORY_SESSION, session.toMap(), UUID);
            rows = apiService.list(HISTORY_SESSION, params, null, 1, 0);
        }
        if (rows == null || rows.isEmpty()) {
            return;
        }
        session = new HistorySession(rows.getFirst());
        boolean increment = event.type() == HistoryEvent.Type.REQUEST || event.type() == HistoryEvent.Type.RESPONSE;
        if (increment) {
            session.message_count++;
        }
        if (session.user_id == null && event.userId() != null) {
            session.user_id = event.userId();
        }
        if (session.started_at != null) {
            try {
                if (occurredAt.isBefore(Instant.parse(session.started_at))) {
                    session.started_at = occurredAt.toString();
                }
            } catch (DateTimeParseException ignored) {
                session.started_at = occurredAt.toString();
            }
        } else {
            session.started_at = occurredAt.toString();
        }
        apiService.merge(HISTORY_SESSION, session.toMap(), session.uuid, UUID);
    }

    private static String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private Instant occurredAt(HistoryEvent event) {
        if (event.datetime() == null || event.datetime().isBlank()) {
            return Instant.now();
        }
        try {
            return Instant.parse(event.datetime());
        } catch (DateTimeParseException failure) {
            Log.warnf("Unreadable history timestamp: %s, session: %s", event.datetime(), event.sessionId());
            return Instant.now();
        }
    }
}
