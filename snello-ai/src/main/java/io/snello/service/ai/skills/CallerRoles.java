package io.snello.service.ai.skills;

import jakarta.enterprise.context.RequestScoped;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

@RequestScoped
public class CallerRoles {
    private Set<String> roles = Set.of();

    public void set(Set<String> roles) {
        this.roles = roles == null ? Set.of() : Collections.unmodifiableSet(new LinkedHashSet<>(roles));
    }

    public Set<String> get() {
        return roles;
    }
}
