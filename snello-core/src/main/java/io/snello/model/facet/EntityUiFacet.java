package io.snello.model.facet;

import io.quarkus.runtime.annotations.RegisterForReflection;

/**
 * SPA / shell presentation hints for a metadata entity.
 */
@RegisterForReflection
public record EntityUiFacet(
        String icon,
        String tab_groups,
        String search_fields,
        Boolean calendar_enabled,
        String calendar_field,
        String calendar_label) {

    public EntityUiFacet {
        if (calendar_enabled == null) {
            calendar_enabled = false;
        }
    }

    public static EntityUiFacet empty() {
        return new EntityUiFacet(null, null, null, false, null, null);
    }

    public Builder builder() {
        return new Builder(this);
    }

    public static Builder builder(EntityUiFacet base) {
        return new Builder(base == null ? empty() : base);
    }

    public static final class Builder {
        public String icon;
        public String tab_groups;
        public String search_fields;
        public Boolean calendar_enabled;
        public String calendar_field;
        public String calendar_label;

        private Builder(EntityUiFacet base) {
            this.icon = base.icon;
            this.tab_groups = base.tab_groups;
            this.search_fields = base.search_fields;
            this.calendar_enabled = base.calendar_enabled;
            this.calendar_field = base.calendar_field;
            this.calendar_label = base.calendar_label;
        }

        public EntityUiFacet build() {
            return new EntityUiFacet(icon, tab_groups, search_fields, calendar_enabled, calendar_field, calendar_label);
        }
    }
}
