package io.snello.security;

import io.quarkus.security.AuthenticationFailedException;
import io.quarkus.security.identity.AuthenticationRequestContext;
import io.quarkus.security.identity.SecurityIdentity;
import io.quarkus.security.runtime.QuarkusSecurityIdentity;
import io.smallrye.mutiny.Uni;
import io.snello.service.AuthService;
import org.eclipse.microprofile.jwt.JsonWebToken;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.security.Principal;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;

class UserEnabledAugmentorTest {

    private static final String ISSUER = "http://kc/realms/snello";

    private final AtomicInteger lookups = new AtomicInteger();
    private boolean keycloakEnabled = true;
    private boolean keycloakDown = false;
    private UserEnabledAugmentor augmentor;

    /** Runs the blocking supplier inline, like a worker thread would. */
    private final AuthenticationRequestContext context = new AuthenticationRequestContext() {
        @Override
        public Uni<SecurityIdentity> runBlocking(Supplier<SecurityIdentity> function) {
            return Uni.createFrom().item(function);
        }
    };

    @BeforeEach
    void setUp() {
        AuthService fake = new AuthService() {
            @Override
            public boolean isUserEnabled(String userId) {
                lookups.incrementAndGet();
                if (keycloakDown) {
                    throw new IllegalStateException("keycloak down");
                }
                return keycloakEnabled;
            }
        };
        UserEnabledService service = new UserEnabledService();
        service.authService = fake;
        service.enabled = true;
        service.cacheSeconds = 60;
        service.targetRealm = "snello";
        augmentor = new UserEnabledAugmentor();
        augmentor.userEnabledService = service;
    }

    private SecurityIdentity identity(String sub, String issuer) {
        Principal jwt = new FakeJwt(sub, issuer);
        return QuarkusSecurityIdentity.builder().setPrincipal(jwt).addRole("User").build();
    }

    private SecurityIdentity run(SecurityIdentity identity) {
        return augmentor.augment(identity, context).await().indefinitely();
    }

    @Test
    void enabledUserPassesAndIsCached() {
        SecurityIdentity id = identity("u1", ISSUER);
        assertSame(id, run(id));
        assertSame(id, run(id));
        assertEquals(1, lookups.get(), "second request must come from the cache");
    }

    @Test
    void disabledUserIsRejected() {
        keycloakEnabled = false;
        assertThrows(AuthenticationFailedException.class, () -> run(identity("u2", ISSUER)));
        // and stays rejected from the cache, without asking Keycloak again
        assertThrows(AuthenticationFailedException.class, () -> run(identity("u2", ISSUER)));
        assertEquals(1, lookups.get());
    }

    @Test
    void keycloakOutageFailsOpen() {
        keycloakDown = true;
        SecurityIdentity id = identity("u3", ISSUER);
        assertSame(id, run(id));
    }

    @Test
    void otherRealmAndAnonymousAreNotChecked() {
        keycloakEnabled = false;
        SecurityIdentity other = identity("u4", "http://kc/realms/lovers");
        assertSame(other, run(other));
        SecurityIdentity anonymous = QuarkusSecurityIdentity.builder().setAnonymous(true).build();
        assertSame(anonymous, run(anonymous));
        assertEquals(0, lookups.get());
    }

    @Test
    void switchedOffByConfig() {
        keycloakEnabled = false;
        augmentor.userEnabledService.enabled = false;
        SecurityIdentity id = identity("u5", ISSUER);
        assertSame(id, run(id));
        assertEquals(0, lookups.get());
    }

    private record FakeJwt(String sub, String iss) implements JsonWebToken {
        @Override
        public String getName() {
            return "user-" + sub;
        }

        @Override
        public Set<String> getClaimNames() {
            return Set.of("sub", "iss");
        }

        @Override
        public <T> T getClaim(String claimName) {
            return null;
        }

        @Override
        public String getSubject() {
            return sub;
        }

        @Override
        public String getIssuer() {
            return iss;
        }
    }
}
