package io.snello.model.pojo;

import io.quarkus.runtime.annotations.RegisterForReflection;

import java.util.ArrayList;
import java.util.List;

/** Outcome of importing one config table (actions, conditions, ...) of a metadata export. */
@RegisterForReflection
public class ConfigImportResult {

    /** Rows that did not exist (by natural key) and were inserted. */
    public int created;
    /** Rows that existed (by natural key) and were overwritten. */
    public int updated;
    public List<String> errors = new ArrayList<>();

    public ConfigImportResult() {
    }
}
