package io.snello.model.facet;

import io.quarkus.runtime.annotations.RegisterForReflection;

/**
 * SPA / shell presentation hints for a metadata entity.
 */
@RegisterForReflection
public class EntityUiFacet {

    public String icon;
    public String tab_groups;
    public String search_fields;
    public boolean calendar_enabled;
    public String calendar_field;
    public String calendar_label;

    public EntityUiFacet() {
    }

    public EntityUiFacet(String icon, String tab_groups, String search_fields,
                         boolean calendar_enabled, String calendar_field, String calendar_label) {
        this.icon = icon;
        this.tab_groups = tab_groups;
        this.search_fields = search_fields;
        this.calendar_enabled = calendar_enabled;
        this.calendar_field = calendar_field;
        this.calendar_label = calendar_label;
    }
}
