package io.snello.util;

import io.quarkus.logging.Log;
import io.snello.model.FieldDefinition;
import io.snello.model.Metadata;
import jakarta.ws.rs.BadRequestException;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * Server-side checks of a data row against its field definitions. The admin validated these in the browser only,
 * so the API accepted rows without mandatory fields and select values outside the declared options.
 * <ul>
 *     <li>{@code mandatory}: on create the field must be present and not blank; on update it may be omitted
 *         (partial update) but not blanked. The key column is skipped (generated server-side).</li>
 *     <li>{@code select} with {@code options}: a non-blank value must be one of the comma-separated options.</li>
 * </ul>
 */
public final class RowValidator {

    private RowValidator() {
    }

    /** @throws BadRequestException listing every violation */
    public static void validate(Metadata metadata, Map<String, Object> row, boolean create) {
        if (metadata == null || metadata.fields == null || row == null) {
            return;
        }
        List<String> errors = new ArrayList<>();
        for (FieldDefinition field : metadata.fields) {
            if (field == null || field.name == null || field.name.equals(metadata.table_key)) {
                continue;
            }
            boolean present = row.containsKey(field.name);
            Object value = row.get(field.name);
            boolean blank = value == null || value.toString().isBlank();
            if (field.mandatory && blank && (create || present)) {
                errors.add(field.name + " is required");
                continue;
            }
            if ("select".equals(field.type) && !blank && field.options != null && !field.options.isBlank()) {
                List<String> options = Arrays.stream(field.options.split(",")).map(String::trim).toList();
                if (!options.contains(value.toString().trim())) {
                    errors.add(field.name + " must be one of " + options);
                }
            }
        }
        if (!errors.isEmpty()) {
            Log.infof("row validation table=%s create=%s: %s", metadata.table_name, create, errors);
            throw new BadRequestException(String.join("; ", errors));
        }
    }
}
