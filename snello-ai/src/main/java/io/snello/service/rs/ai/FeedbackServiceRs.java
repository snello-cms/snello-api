package io.snello.service.rs.ai;

import io.quarkus.arc.properties.IfBuildProperty;
import io.quarkus.logging.Log;
import io.quarkus.security.identity.SecurityIdentity;
import io.smallrye.common.annotation.RunOnVirtualThread;
import io.snello.management.SnelloPlane;
import io.snello.service.ai.history.HistoryEventPublisher;
import io.snello.service.ai.pojo.FeedbackRequest;
import io.snello.service.ai.pojo.HistoryEvent;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.jwt.JsonWebToken;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

import java.security.Principal;

import static io.snello.management.AppConstants.AI_FEEDBACK_PATH;

@Path(AI_FEEDBACK_PATH)
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@RunOnVirtualThread
@IfBuildProperty(name = SnelloPlane.CONTROL_PLANE_PROPERTY, stringValue = "true", enableIfMissing = true)
public class FeedbackServiceRs {

    @Inject
    HistoryEventPublisher history;

    @Inject
    SecurityIdentity identity;

    @Inject
    JsonMapper mapper;

    @POST
    public Response create(FeedbackRequest request) {
        if (request == null) {
            return Response.status(Response.Status.BAD_REQUEST).entity("Missing body").build();
        }
        String vote = normalizeVote(request.vote());
        if (vote == null) {
            return Response.status(Response.Status.BAD_REQUEST).entity("vote must be up or down").build();
        }
        String sessionId = blankToNull(request.sessionId());
        if (sessionId == null) {
            return Response.status(Response.Status.BAD_REQUEST).entity("sessionId is required").build();
        }
        String assistantBody = request.assistantBody() == null ? "" : request.assistantBody();
        if (assistantBody.isBlank()) {
            return Response.status(Response.Status.BAD_REQUEST).entity("assistantBody is required").build();
        }

        Principal principal = identity.getPrincipal();
        String userId = subject(principal);
        String username = readableName(principal);
        String body;
        try {
            body = toJsonBody(vote, request, assistantBody);
        } catch (Exception failure) {
            Log.warn("Could not serialize feedback body", failure);
            return Response.serverError().entity("Could not serialize feedback").build();
        }

        history.feedback(HistoryEvent.Channel.AGUI, userId, username, body, sessionId, blankToNull(request.model()));
        return Response.accepted().build();
    }

    private String toJsonBody(String vote, FeedbackRequest request, String assistantBody) throws Exception {
        ObjectNode node = mapper.createObjectNode();
        node.put("vote", vote);
        node.put("uuid", request.sessionId().trim());
        if (blankToNull(request.messageId()) != null) {
            node.put("messageId", request.messageId().trim());
        }
        if (blankToNull(request.threadId()) != null) {
            node.put("threadId", request.threadId().trim());
        }
        if (blankToNull(request.model()) != null) {
            node.put("model", request.model().trim());
        }
        node.put("assistantBody", assistantBody);
        return mapper.writeValueAsString(node);
    }

    private static String normalizeVote(String vote) {
        if (vote == null) {
            return null;
        }
        String normalized = vote.trim().toLowerCase();
        if ("up".equals(normalized) || "down".equals(normalized)) {
            return normalized;
        }
        return null;
    }

    private static String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private static String subject(Principal principal) {
        return principal instanceof JsonWebToken token ? token.getSubject() : principal.getName();
    }

    private static String readableName(Principal principal) {
        if (principal instanceof JsonWebToken token) {
            String preferred = token.getClaim("preferred_username");
            if (preferred != null && !preferred.isBlank()) {
                return preferred;
            }
        }
        return principal.getName();
    }
}
