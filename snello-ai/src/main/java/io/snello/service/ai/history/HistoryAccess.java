package io.snello.service.ai.history;

import io.quarkus.logging.Log;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.core.MultivaluedMap;
import org.eclipse.microprofile.jwt.JsonWebToken;

import java.util.Map;

/**
 * Who may read which chat history rows.
 * <p>
 * Default: the caller sees only their own rows ({@code user_id} = token {@code sub}), which is what the chat widget needs.
 * {@code ?scope=all} lifts that filter for the {@code Admin} role only, so the admin "Chat history" page can browse
 * every user's sessions (optionally narrowed with {@code ?user_id=}). Non-admins asking for {@code scope=all} stay scoped.
 */
@RequestScoped
public class HistoryAccess {

    public static final String SCOPE_PARAM = "scope";
    public static final String SCOPE_ALL = "all";
    static final String ADMIN_ROLE = "Admin";
    static final String USER_ID = "user_id";

    @Inject
    SecurityIdentity identity;

    public String currentUserId() {
        if (identity.getPrincipal() instanceof JsonWebToken token) {
            return token.getSubject();
        }
        return identity.getPrincipal().getName();
    }

    public boolean isAdmin() {
        return identity.hasRole(ADMIN_ROLE);
    }

    /**
     * Rewrites list query params in place: drops {@code scope} (not a column) and forces {@code user_id} unless an
     * Admin explicitly asked for {@code scope=all}.
     */
    public void applyListScope(String resource, MultivaluedMap<String, String> params) {
        String scope = params.getFirst(SCOPE_PARAM);
        params.remove(SCOPE_PARAM);
        boolean wantsAll = SCOPE_ALL.equalsIgnoreCase(scope);
        boolean admin = isAdmin();
        if (wantsAll && admin) {
            Log.infof("[history] %s list: admin scope=all (user_id filter=%s)", resource, params.getFirst(USER_ID));
            return;
        }
        if (wantsAll) {
            Log.warnf("[history] %s list: scope=all refused for non-admin %s, scoping to own rows", resource, currentUserId());
        }
        String userId = currentUserId();
        params.putSingle(USER_ID, userId);
        Log.debugf("[history] %s list: scoped to user_id=%s", resource, userId);
    }

    /** Owner or Admin may read a single row. */
    public boolean canRead(String resource, Map<String, Object> row) {
        Object owner = row.get(USER_ID);
        if (owner == null || currentUserId().equals(owner.toString())) {
            return true;
        }
        if (isAdmin()) {
            Log.infof("[history] %s fetch %s: admin reading row of user %s", resource, row.get("uuid"), owner);
            return true;
        }
        Log.warnf("[history] %s fetch %s: forbidden for %s (owner %s)", resource, row.get("uuid"), currentUserId(), owner);
        return false;
    }
}
