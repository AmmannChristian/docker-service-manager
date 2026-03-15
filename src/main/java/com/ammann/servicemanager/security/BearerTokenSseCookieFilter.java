/* (C)2026 */
package com.ammann.servicemanager.security;

import io.quarkus.oidc.AccessTokenCredential;
import io.quarkus.security.identity.IdentityProviderManager;
import io.quarkus.security.identity.SecurityIdentity;
import io.quarkus.security.identity.request.AuthenticationRequest;
import io.quarkus.security.identity.request.TokenAuthenticationRequest;
import io.quarkus.vertx.http.runtime.security.ChallengeData;
import io.quarkus.vertx.http.runtime.security.HttpAuthenticationMechanism;
import io.quarkus.vertx.http.runtime.security.HttpCredentialTransport;
import io.quarkus.vertx.http.runtime.security.HttpSecurityUtils;
import io.smallrye.mutiny.Uni;
import io.vertx.core.http.Cookie;
import io.vertx.ext.web.RoutingContext;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.Optional;
import java.util.Set;
import org.jboss.logging.Logger;

/**
 * Quarkus {@link HttpAuthenticationMechanism} that extracts bearer tokens from the {@code
 * sse-token} cookie for SSE endpoints.
 *
 * <p>The browser's native {@code EventSource} API cannot send custom HTTP headers. To authenticate
 * SSE streams, the frontend first calls {@code POST /api/v1/containers/{id}/logs/stream/token}
 * (with a standard {@code Authorization: Bearer} header) to obtain a short-lived opaque token
 * delivered as an {@code HttpOnly; Secure; SameSite} cookie. This mechanism reads that cookie,
 * exchanges the UUID for the associated bearer token via {@link SseTokenStore}, and delegates it to
 * the {@link IdentityProviderManager} as a {@link TokenAuthenticationRequest} so that the OIDC
 * identity provider validates it.
 *
 * <p>This <strong>must</strong> be an {@code HttpAuthenticationMechanism} (not a JAX-RS {@code
 * ContainerRequestFilter} or Vert.x route handler) because Quarkus OIDC authenticates at the HTTP
 * security layer, which runs before JAX-RS filters and application-level route handlers.
 *
 * <p>The token is <em>not</em> removed on read because {@code EventSource} auto-reconnects and
 * re-sends the same cookie within the TTL window.
 */
@ApplicationScoped
public class BearerTokenSseCookieFilter implements HttpAuthenticationMechanism {

    private static final Logger LOG = Logger.getLogger(BearerTokenSseCookieFilter.class);

    /** Cookie name used to carry the short-lived SSE token. */
    static final String COOKIE_NAME = "sse-token";

    /** SSE endpoint path suffix that this filter applies to. */
    static final String SSE_PATH_SUFFIX = "/logs/stream";

    private final SseTokenStore sseTokenStore;

    @Inject
    BearerTokenSseCookieFilter(SseTokenStore sseTokenStore) {
        this.sseTokenStore = sseTokenStore;
    }

    @Override
    public Uni<SecurityIdentity> authenticate(
            RoutingContext context, IdentityProviderManager identityProviderManager) {
        String path = context.request().path();

        if (!isSseEndpoint(path)) {
            return Uni.createFrom().nullItem();
        }

        String existingAuth = context.request().getHeader("Authorization");
        if (existingAuth != null && !existingAuth.isBlank()) {
            LOG.debugf("SSE request already has Authorization header, skipping cookie extraction");
            return Uni.createFrom().nullItem();
        }

        Cookie sseCookie = context.request().getCookie(COOKIE_NAME);
        if (sseCookie == null) {
            LOG.debugf("No sse-token cookie found for SSE endpoint: %s", path);
            return Uni.createFrom().nullItem();
        }

        Optional<String> rawToken = sseTokenStore.getToken(sseCookie.getValue());
        if (rawToken.isEmpty()) {
            LOG.warnf("SSE token lookup failed (expired or unknown) for endpoint: %s", path);
            return Uni.createFrom().nullItem();
        }

        LOG.debugf("Resolved sse-token cookie, delegating to OIDC IdentityProvider for: %s", path);

        // Delegate the bearer token to OIDC's IdentityProvider for validation.
        // The RoutingContext must be attached so OidcIdentityProvider can resolve the tenant.
        TokenAuthenticationRequest tokenRequest =
                new TokenAuthenticationRequest(new AccessTokenCredential(rawToken.get()));
        HttpSecurityUtils.setRoutingContextAttribute(tokenRequest, context);
        return identityProviderManager.authenticate(tokenRequest);
    }

    @Override
    public Uni<ChallengeData> getChallenge(RoutingContext context) {
        return Uni.createFrom().nullItem();
    }

    @Override
    public Set<Class<? extends AuthenticationRequest>> getCredentialTypes() {
        return Set.of(TokenAuthenticationRequest.class);
    }

    @Override
    public Uni<Boolean> sendChallenge(RoutingContext context) {
        return HttpAuthenticationMechanism.super.sendChallenge(context);
    }

    @Override
    public Uni<HttpCredentialTransport> getCredentialTransport(RoutingContext context) {
        return Uni.createFrom().nullItem();
    }

    @Override
    public int getPriority() {
        return HttpAuthenticationMechanism.super.getPriority();
    }

    /**
     * Checks if the request path is an SSE log streaming endpoint.
     *
     * @param path the request path
     * @return true if this is an SSE endpoint
     */
    boolean isSseEndpoint(String path) {
        return path != null && path.endsWith(SSE_PATH_SUFFIX);
    }
}
