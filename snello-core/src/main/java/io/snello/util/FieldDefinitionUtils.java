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
