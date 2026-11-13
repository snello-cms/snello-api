package io.snello.service.ai.agent;

import dev.langchain4j.exception.InvalidRequestException;
import dev.langchain4j.model.output.TokenUsage;
import dev.langchain4j.service.Result;
import io.quarkus.logging.Log;
import io.snello.model.ai.pojo.AguiRequest;
import io.snello.model.ai.pojo.AguiResponse;
import io.snello.service.ai.skills.CallerProject;
import io.snello.service.ai.skills.CallerRoles;
import io.snello.service.ai.wss.ActiveAguiRun;
import io.snello.service.ai.wss.AguiServiceWss;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.control.ActivateRequestContext;
import jakarta.enterprise.event.Event;
import jakarta.enterprise.event.ObservesAsync;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;

@ApplicationScoped
public class AguiAgentWorker {

    @Inject
    AgentService quickAgent;

    @Inject
    ProAgentService proAgent;

    @Inject
    CallerRoles caller;

    @Inject
    CallerProject project;

    @Inject
    ActiveAguiRun activeRun;

    @Inject
    Event<AguiResponse> responses;

    @ConfigProperty(name = "quarkus.langchain4j.ai.gemini.quick.chat-model.model-id", defaultValue = "gemini-2.0-flash")
    String quickModelId;

    @ConfigProperty(name = "quarkus.langchain4j.ai.gemini.pro.chat-model.model-id", defaultValue = "gemini-2.0-pro")
    String proModelId;

    @ActivateRequestContext
    void onRequest(@ObservesAsync AguiRequest request) {
        AguiResponse response = process(request);
        responses.fireAsync(response);
    }

    private AguiResponse process(AguiRequest request) {
        long dequeuedAt = System.currentTimeMillis();
        long dispatchWaitMs = Math.max(0L, dequeuedAt - request.enqueuedAtMs());
        caller.set(request.roles());
        project.set(request.projectUuid());
        activeRun.bind(request);
        String memoryId = AguiServiceWss.memoryId(request.userId(), request.threadId());
        boolean pro = request.usePro();
        String mode = pro ? "pro" : "quick";
        String modelId = pro ? proModelId : quickModelId;
        Log.infof("AG-UI run mode=%s modelId=%s runId=%s user=%s dispatchWaitMs=%d",
                mode, modelId, request.runId(), request.username(), dispatchWaitMs);
        long aiStarted = System.currentTimeMillis();
        try {
            Result<String> result = pro
                    ? proAgent.respond(memoryId, request.message())
                    : quickAgent.respond(memoryId, request.message());
            long aiMs = Math.max(0L, System.currentTimeMillis() - aiStarted);
            TokenUsage tokenUsage = result.tokenUsage();
            Integer inputTokens = tokenUsage == null ? null : tokenUsage.inputTokenCount();
            Integer outputTokens = tokenUsage == null ? null : tokenUsage.outputTokenCount();
            Integer totalTokens = tokenUsage == null ? null : tokenUsage.totalTokenCount();
            return AguiResponse.success(
                    request,
                    result.content(),
                    modelId,
                    inputTokens,
                    outputTokens,
                    totalTokens,
                    dispatchWaitMs,
                    aiMs);
        } catch (InvalidRequestException failure) {
            long aiMs = Math.max(0L, System.currentTimeMillis() - aiStarted);
            if (isApiKeyRejected(failure)) {
                // a server configuration problem: telling the user to start a new chat would only send them in circles
                Log.errorf("AI provider rejected the API key for mode=%s model=%s (run %s): check the GEMINI / OPENAI key",
                        mode, modelId, request.runId());
                return AguiResponse.error(request, "MODEL_AUTH",
                        request.useEnglish()
                                ? "The AI service is not configured correctly (API key rejected). Please contact the administrator."
                                : "Il servizio AI non è configurato correttamente (chiave API rifiutata). Contatta l'amministratore.",
                        dispatchWaitMs, aiMs);
            }
            Log.warnf(failure, "AI rejected AG-UI run %s", request.runId());
            return AguiResponse.error(request, "MODEL_INVALID_REQUEST",
                    request.useEnglish()
                            ? "The AI service rejected the conversation state. Start a new conversation and try again."
                            : "Il servizio AI ha rifiutato lo stato della conversazione. Avvia una nuova chat e riprova.",
                    dispatchWaitMs, aiMs);
        } catch (RuntimeException failure) {
            long aiMs = Math.max(0L, System.currentTimeMillis() - aiStarted);
            Log.errorf(failure, "AG-UI run failed %s", request.runId());
            return AguiResponse.error(request, "AGENT_ERROR",
                    request.useEnglish()
                            ? "The AI service could not complete the request. Please try again."
                            : "Il servizio AI non ha completato la richiesta. Riprova.",
                    dispatchWaitMs, aiMs);
        } finally {
            activeRun.clear();
        }
    }

    /** Gemini reports a bad key as a 400 INVALID_ARGUMENT, not as an authentication error. */
    public static boolean isApiKeyRejected(InvalidRequestException failure) {
        String message = failure.getMessage();
        return message != null && (message.contains("API_KEY_INVALID") || message.contains("API key not valid")
                || message.contains("Incorrect API key"));
    }
}
