package io.snello.util;

import io.snello.model.FieldDefinition;
import io.snello.model.entity.FieldModel;
import io.snello.model.enums.InputType;
import io.snello.model.enums.WidgetType;
import io.snello.model.facet.FieldUiFacet;

public class FieldDefinitionUtils {

    private FieldDefinitionUtils() {
        // Utility class
    }

    public static boolean isMultiJoinType(String type) {
        WidgetType widget = WidgetType.from(type);
        return widget != null && widget.isMultiJoin();
    }

    public static boolean isMultiJoin(FieldDefinition fd) {
        return fd != null && isMultiJoinType(fd.type);
    }

    public static boolean isMultiJoin(FieldModel model) {
        return model != null && model.ui != null && isMultiJoinType(model.ui.type());
    }

    /**
     * A field with no column: {@code button} (a form action, e.g. "scan document" that pre-fills other fields).
     * CREATE TABLE / ADD COLUMN skip it, and writes drop any value sent for it.
     */
    public static boolean isVirtualType(String type) {
        return WidgetType.BUTTON == WidgetType.from(type);
    }

    public static boolean isVirtual(FieldDefinition fd) {
        return fd != null && isVirtualType(fd.type);
    }

    /**
     * Removes the values of virtual fields from a row about to be written (an admin form may send its button's
     * control): they have no column, the INSERT / UPDATE would fail on them.
     *
     * @return the names removed
     */
    public static java.util.List<String> stripVirtual(java.util.List<FieldDefinition> fields, java.util.Map<String, Object> row) {
        java.util.List<String> removed = new java.util.ArrayList<>();
        if (fields == null || row == null) {
            return removed;
        }
        for (FieldDefinition fd : fields) {
            if (isVirtual(fd) && fd.name != null && row.containsKey(fd.name)) {
                row.remove(fd.name);
                removed.add(fd.name);
            }
        }
        return removed;
    }

    public static boolean isPassivation(FieldDefinition fd) {
        return fd != null && InputType.PASSIVATION == InputType.from(fd.input_type);
    }

    public static boolean isPassivation(FieldUiFacet ui) {
        return ui != null && InputType.PASSIVATION == InputType.from(ui.input_type());
    }

    public static String multijoin(String table, String joinTable, String tableUuid, String joinTableUuid) {
        return "";
    }
}
