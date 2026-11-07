package io.snello.service.ai.skills;

import jakarta.enterprise.context.RequestScoped;

@RequestScoped
public class CallerProject {
    private String projectUuid;

    public void set(String projectUuid) {
        this.projectUuid = projectUuid;
    }

    public String get() {
        return projectUuid;
    }
}
