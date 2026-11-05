package io.snello.model.facet;

import io.quarkus.runtime.annotations.RegisterForReflection;

/**
 * Widget / form / list presentation for a field definition.
 */
@RegisterForReflection
public class FieldUiFacet {

    public String label;
    public String type;
    public String input_type;
    public String options;
    public String group_name;
    public String tab_name;
    public String validations;
    public int view_index;
    public boolean show_in_list;
    public boolean input_disabled;
    public String function_def;
    public String pattern;
    public boolean mandatory;
    public boolean required;
    public int order_num;

    public FieldUiFacet() {
    }
}
