/* (C)2026 */
package com.ammann.servicemanager.security;

import jakarta.annotation.Priority;
import jakarta.inject.Inject;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.container.PreMatching;
import jakarta.ws.rs.core.Cookie;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.UriInfo;
import jakarta.ws.rs.ext.Provider;
import java.util.Map;
import java.util.Optional;
import org.jboss.logging.Logger;

/**
 * JAX-RS filter that extracts bearer tokens from the {@code sse-token} cookie for SSE endpoints.
 *
 * <p>The browser's native {@code EventSource} API cannot send custom HTTP headers. To authenticate
 * SSE streams, the frontend first calls {@code POST /api/v1/containers/{id}/logs/stream/token}
 * (with a standard {@code Authorization: Bearer} header) to obtain a short-lived opaque token
 * delivered as an {@code HttpOnly; Secure; SameSite} cookie. This filter reads that cookie,
 * exchanges the UUID for the associated bearer token via {@link SseTokenStore}, and injects the
 * {@code Authorization: Bearer} header before OIDC authentication runs.
 *
 * <p>The token is <em>not</em> removed on read because {@code EventSource} auto-reconnects and
 * re-sends the same cookie within the TTL window.
 */
@Provider
@PreMatching
@Priority(Priorities.AUTHENTICATION - 100)
public class BearerTokenSseCookieFilter implements ContainerRequestFilter {

    private static final Logger LOG = Logger.getLogger(BearerTokenSseCookieFilter.class);

    /** Cookie name used to carry the short-lived SSE token. */
    static final String COOKIE_NAME = "sse-token";

    /** SSE endpoint path suffix that this filter applies to. */
    static final String SSE_PATH_SUFFIX = "/logs/stream";

    @Inject SseTokenStore sseTokenStore;

    @Override
    public void filter(ContainerRequestContext requestContext) {
        UriInfo uriInfo = requestContext.getUriInfo();
        String path = uriInfo.getPath();

        if (!isSseEndpoint(path)) {
            return;
        }

        String existingAuth = requestContext.getHeaderString(HttpHeaders.AUTHORIZATION);
        if (existingAuth != null && !existingAuth.isBlank()) {
            LOG.debugf("SSE request already has Authorization header, skipping cookie extraction");
            return;
        }

        Map<String, Cookie> cookies = requestContext.getCookies();
        Cookie sseCookie = cookies.get(COOKIE_NAME);
        if (sseCookie == null) {
            LOG.debugf("No sse-token cookie found for SSE endpoint: %s", path);
            return;
        }

        Optional<String> rawToken = sseTokenStore.getToken(sseCookie.getValue());
        if (rawToken.isEmpty()) {
            LOG.warnf("SSE token lookup failed (expired or unknown) for endpoint: %s", path);
            return;
        }

        requestContext.getHeaders().putSingle(HttpHeaders.AUTHORIZATION, "Bearer " + rawToken.get());
        LOG.debugf("Set Authorization header from sse-token cookie for endpoint: %s", path);
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