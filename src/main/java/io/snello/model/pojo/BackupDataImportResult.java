package io.snello.model.pojo;

import io.quarkus.runtime.annotations.RegisterForReflection;

import java.util.ArrayList;
import java.util.List;

@RegisterForReflection
public class BackupDataImportResult {

    public int imported;
    public int skipped;
    public List<String> errors = new ArrayList<>();

    public BackupDataImportResult() {
    }

    public BackupDataImportResult(int imported, int skipped, List<String> errors) {
        this.imported = imported;
        this.skipped = skipped;
        this.errors = errors != null ? errors : new ArrayList<>();
    }
}
