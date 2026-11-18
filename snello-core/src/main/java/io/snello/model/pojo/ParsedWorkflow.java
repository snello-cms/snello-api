package io.snello.model.pojo;

import java.util.Map;

/**
 * A workflow YAML that passed {@link io.snello.service.flow.WorkflowYamlValidator}: its document block and the whole
 * workflow as a map (the admin draws the diagram from it while the YAML is being edited).
 */
public record ParsedWorkflow(WorkflowDocument document, Map<String, Object> workflow) {
}
