package io.snello.service.flow;

import io.quarkus.logging.Log;
import io.snello.model.pojo.ParsedWorkflow;
import io.snello.model.pojo.WorkflowDocument;
import jakarta.ws.rs.BadRequestException;
import org.snakeyaml.engine.v2.api.Load;
import org.snakeyaml.engine.v2.api.LoadSettings;
import org.snakeyaml.engine.v2.api.lowlevel.Parse;
import org.snakeyaml.engine.v2.events.AliasEvent;
import org.snakeyaml.engine.v2.events.Event;
import org.snakeyaml.engine.v2.events.NodeEvent;
import org.snakeyaml.engine.v2.exceptions.YamlEngineException;

import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * The checks snello can make on a workflow YAML before it reaches the runner, with messages a person can act on.
 * The full Serverless Workflow schema is checked by the runner itself at deploy (flow-bridge returns its error):
 * here only what keys the row and what the runner 1.3.0 is known to get wrong without a clear message.
 * <ul>
 *   <li>valid YAML, one document, a mapping, no duplicate keys</li>
 *   <li>no anchors / aliases (the runner does not resolve them)</li>
 *   <li>{@code document.dsl / namespace / name / version} present; namespace and name follow the runner's rule
 *       {@code ^[a-zA-Z0-9](?:[a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?$} (no {@code _})</li>
 *   <li>a {@code do} list</li>
 * </ul>
 */
public final class WorkflowYamlValidator {

    public static final int MAX_CHARS = 256 * 1024;
    /** The runner's (JSON schema's) pattern for document.namespace and document.name. */
    public static final Pattern DSL_NAME = Pattern.compile("^[a-zA-Z0-9](?:[a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?$");
    public static final Pattern VERSION = Pattern.compile("[A-Za-z0-9.+-]{1,50}");

    private WorkflowYamlValidator() {
    }

    public static WorkflowDocument validate(String yaml) {
        return parse(yaml).document();
    }

    @SuppressWarnings("unchecked")
    public static ParsedWorkflow parse(String yaml) {
        if (yaml == null || yaml.isBlank()) {
            throw bad("yaml: the workflow is empty");
        }
        if (yaml.length() > MAX_CHARS) {
            throw bad("yaml: at most " + MAX_CHARS + " characters");
        }
        LoadSettings settings = LoadSettings.builder()
                .setAllowDuplicateKeys(false)
                .setMaxAliasesForCollections(0)
                .setCodePointLimit(MAX_CHARS * 2)
                .build();
        rejectAnchors(settings, yaml);
        Object root;
        try {
            Iterable<Object> docs = new Load(settings).loadAllFromString(yaml);
            List<Object> all = new java.util.ArrayList<>();
            docs.forEach(all::add);
            if (all.size() != 1) {
                throw bad("yaml: exactly one YAML document expected, found " + all.size());
            }
            root = all.get(0);
        } catch (YamlEngineException e) {
            throw bad("yaml: " + oneLine(e.getMessage()));
        }
        if (!(root instanceof Map<?, ?> map)) {
            throw bad("yaml: the workflow must be a mapping with 'document' and 'do'");
        }
        if (!(map.get("document") instanceof Map<?, ?> doc)) {
            throw bad("document: missing (dsl, namespace, name, version)");
        }
        String dsl = text(doc, "dsl");
        String namespace = text(doc, "namespace");
        String name = text(doc, "name");
        String version = text(doc, "version");
        if (dsl == null) {
            throw bad("document.dsl: missing (e.g. \"1.0.0\")");
        }
        requireName("document.namespace", namespace);
        requireName("document.name", name);
        if (version == null || !VERSION.matcher(version).matches()) {
            throw bad("document.version: missing or invalid (e.g. \"1.0.0\"): letters, digits, '.', '+', '-'");
        }
        if (!(map.get("do") instanceof List<?> steps) || steps.isEmpty()) {
            throw bad("do: the workflow needs a non-empty 'do' list of tasks");
        }
        WorkflowDocument wd = new WorkflowDocument(dsl, namespace, name, version, text(doc, "title"), text(doc, "summary"));
        Log.infof("workflow yaml ok: %s dsl=%s tasks=%d chars=%d", wd.id(), dsl, steps.size(), yaml.length());
        return new ParsedWorkflow(wd, (Map<String, Object>) map);
    }

    /** Anchors (&x) and aliases (*x) seen by the low-level parser: exact, unlike a text search (jq uses '*'). */
    private static void rejectAnchors(LoadSettings settings, String yaml) {
        try {
            for (Event ev : new Parse(settings).parseString(yaml)) {
                if (ev instanceof AliasEvent || (ev instanceof NodeEvent ne && ne.getAnchor().isPresent())) {
                    String at = ev.getStartMark().map(m -> " (line " + (m.getLine() + 1) + ")").orElse("");
                    throw bad("yaml: anchors and aliases (&name / *name) are not supported by the runner" + at
                            + "; for retry policies use use.retries + retry: <name>");
                }
            }
        } catch (YamlEngineException e) {
            throw bad("yaml: " + oneLine(e.getMessage()));
        }
    }

    private static void requireName(String field, String value) {
        if (value == null || !DSL_NAME.matcher(value).matches()) {
            throw bad(field + ": missing or invalid '" + value + "': letters, digits and '-' (not first or last), "
                    + "max 63; no '_'");
        }
    }

    private static String text(Map<?, ?> map, String key) {
        Object v = map.get(key);
        return v == null ? null : v.toString().trim();
    }

    private static String oneLine(String s) {
        return s == null ? "invalid YAML" : s.replaceAll("\\s+", " ").trim();
    }

    private static BadRequestException bad(String msg) {
        Log.infof("workflow yaml refused: %s", msg);
        return new BadRequestException(msg);
    }
}
