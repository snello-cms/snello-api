package io.snello.service.ai.wss;

import io.snello.model.ai.pojo.AguiRequest;
import jakarta.enterprise.context.RequestScoped;

@RequestScoped
public class ActiveAguiRun {
    private String connectionId;
    private String threadId;
    private String runId;
    private String userId;
    private String username;
    private String model;
    private String language;
    private String projectUuid;

    public void bind(AguiRequest request) {
        this.connectionId = request.connectionId();
        this.threadId = request.threadId();
        this.runId = request.runId();
        this.userId = request.userId();
        this.username = request.username();
        this.model = request.model();
        this.language = request.language();
        this.projectUuid = request.projectUuid();
    }

    public void clear() {
        connectionId = threadId = runId = userId = username = model = language = projectUuid = null;
    }

    public boolean isBound() { return connectionId != null && !connectionId.isBlank(); }
    public String connectionId() { return connectionId; }
    public String threadId() { return threadId; }
    public String runId() { return runId; }
    public String userId() { return userId; }
    public String username() { return username; }
    public String model() { return model; }
    public String language() { return language; }
    public String projectUuid() { return projectUuid; }
}
