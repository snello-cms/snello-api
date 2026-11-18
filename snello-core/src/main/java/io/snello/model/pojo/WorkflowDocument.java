package io.snello.model.pojo;

/**
 * The {@code document} block of a Serverless Workflow 1.0 YAML, as read by
 * {@link io.snello.service.flow.WorkflowYamlValidator}: what a {@code workflowdefinitions} row is keyed and listed by.
 */
public record WorkflowDocument(String dsl, String namespace, String name, String version, String title,
                               String summary) {

    public String id() {
        return namespace + ":" + name + ":" + version;
    }
}
