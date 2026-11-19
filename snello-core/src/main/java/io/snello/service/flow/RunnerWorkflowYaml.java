package io.snello.service.flow;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.json.JsonMapper;
import org.snakeyaml.engine.v2.api.Dump;
import org.snakeyaml.engine.v2.api.DumpSettings;
import org.snakeyaml.engine.v2.common.FlowStyle;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The runner answers {@code GET /q/flow/definitions/{ns}/{name}/{version}} with the loaded workflow as JSON (its
 * model serialized, unset fields as {@code null}). This turns it back into block-style YAML for the admin, so a
 * workflow that reached the runner from git (not from the admin's editor) can be read like the ones written there.
 * Nulls are dropped (the model's unset fields, never written by a person); everything else is kept as it came.
 */
public final class RunnerWorkflowYaml {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final Dump YAML = new Dump(DumpSettings.builder()
            .setDefaultFlowStyle(FlowStyle.BLOCK)
            .setIndent(2)
            .setIndicatorIndent(2)
            .setIndentWithIndicator(true)
            .setWidth(200)
            .build());

    private RunnerWorkflowYaml() {
    }

    public static String fromRunnerJson(String json) throws JsonProcessingException {
        Object tree = JSON.readValue(json, Object.class);
        Object clean = withoutNulls(tree);
        return YAML.dumpToString(clean);  // sizes are logged by the caller (FlowServiceRs)
    }

    static Object withoutNulls(Object node) {
        if (node instanceof Map<?, ?> map) {
            Map<String, Object> out = new LinkedHashMap<>();
            map.forEach((k, v) -> {
                if (v != null) {
                    out.put(String.valueOf(k), withoutNulls(v));
                }
            });
            return out;
        }
        if (node instanceof List<?> list) {
            List<Object> out = new ArrayList<>(list.size());
            for (Object v : list) {
                if (v != null) {
                    out.add(withoutNulls(v));
                }
            }
            return out;
        }
        return node;
    }
}
