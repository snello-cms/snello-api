package io.snello.test.flow;

import io.snello.model.pojo.WorkflowDocument;
import io.snello.service.flow.RunnerWorkflowYaml;
import io.snello.service.flow.WorkflowYamlValidator;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Runner JSON (as api.sne.lol's runner served contracts:contract-evaluation) -> YAML the admin shows. */
class RunnerWorkflowYamlTest {

    static String fixture() throws Exception {
        try (InputStream in = RunnerWorkflowYamlTest.class.getResourceAsStream("/flow/runner-contract-evaluation.json")) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    @Test
    void realRunnerWorkflowBecomesValidYaml() throws Exception {
        String yaml = RunnerWorkflowYaml.fromRunnerJson(fixture());

        WorkflowDocument doc = WorkflowYamlValidator.validate(yaml);
        assertEquals("contracts", doc.namespace());
        assertEquals("contract-evaluation", doc.name());
        assertEquals("1.0.0", doc.version());
        assertTrue(yaml.startsWith("document:\n"), yaml.substring(0, 40));
        assertFalse(yaml.contains(": null"), "nulls of the runner model are dropped");
        assertTrue(yaml.contains("$secret"), "runtime expressions stay as written");
    }

    @Test
    void nullsDroppedEmptyMapsKept() throws Exception {
        String yaml = RunnerWorkflowYaml.fromRunnerJson(
                "{\"document\":{\"name\":\"x\",\"title\":null},\"use\":{\"retries\":{\"r\":{\"backoff\":{\"exponential\":{}}}}},"
                        + "\"do\":[{\"a\":{\"set\":{\"k\":\"${ .v }\"}}}, null]}");
        assertFalse(yaml.contains("title"));
        assertTrue(yaml.contains("exponential: {}"), yaml);
        assertTrue(yaml.contains("- a:"), yaml);
    }
}
