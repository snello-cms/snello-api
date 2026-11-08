package io.snello.model.facet;

import io.quarkus.runtime.annotations.RegisterForReflection;

/**
 * Widget / form / list presentation for a field definition.
 */
@RegisterForReflection
public record FieldUiFacet(
        String label,
        String type,
        String input_type,
        String options,
        String group_name,
        String tab_name,
        String validations,
        int view_index,
        Boolean show_in_list,
        Boolean input_disabled,
        String function_def,
        String pattern,
        Boolean mandatory,
        Boolean required,
        int order_num) {

    public FieldUiFacet {
        if (show_in_list == null) {
            show_in_list = false;
        }
        if (input_disabled == null) {
            input_disabled = false;
        }
        if (mandatory == null) {
            mandatory = false;
        }
        if (required == null) {
            required = false;
        }
    }

    public static FieldUiFacet empty() {
        return new FieldUiFacet(null, null, null, null, null, null, null, 0, false, false, null, null, false, false, 0);
    }

    public Builder builder() {
        return new Builder(this);
    }

    public static Builder builder(FieldUiFacet base) {
        return new Builder(base == null ? empty() : base);
    }

    public static final class Builder {
        public String label;
        public String type;
        public String input_type;
        public String options;
        public String group_name;
        public String tab_name;
        public String validations;
        public int view_index;
        public Boolean show_in_list;
        public Boolean input_disabled;
        public String function_def;
        public String pattern;
        public Boolean mandatory;
        public Boolean required;
        public int order_num;

        private Builder(FieldUiFacet base) {
            this.label = base.label;
            this.type = base.type;
            this.input_type = base.input_type;
            this.options = base.options;
            this.group_name = base.group_name;
            this.tab_name = base.tab_name;
            this.validations = base.validations;
            this.view_index = base.view_index;
            this.show_in_list = base.show_in_list;
            this.input_disabled = base.input_disabled;
            this.function_def = base.function_def;
            this.pattern = base.pattern;
            this.mandatory = base.mandatory;
            this.required = base.required;
            this.order_num = base.order_num;
        }

        public FieldUiFacet build() {
            return new FieldUiFacet(label, type, input_type, options, group_name, tab_name, validations, view_index,
                    show_in_list, input_disabled, function_def, pattern, mandatory, required, order_num);
        }
    }
}
