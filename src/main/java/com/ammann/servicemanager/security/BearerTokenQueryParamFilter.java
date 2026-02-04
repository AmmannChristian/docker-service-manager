/* (C)2026 */
package com.ammann.servicemanager.security;

import jakarta.annotation.Priority;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.container.PreMatching;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MultivaluedMap;
import jakarta.ws.rs.core.UriInfo;
import jakarta.ws.rs.ext.Provider;
import java.util.List;
import java.util.regex.Pattern;
import org.jboss.logging.Logger;

/**
 * JAX-RS filter that extracts bearer tokens from query parameters for SSE endpoints.
 *
 * <p>The browser's native {@code EventSource} API cannot send custom HTTP headers, so frontends
 * pass the access token via query parameter ({@code ?token=...}). This filter extracts the token
 * and sets the {@code Authorization: Bearer} header before OIDC authentication runs.
 *
 * <p>Security measures:
 * <ul>
 *   <li>Only applies to SSE log streaming endpoints ({@code /logs/stream})</li>
 *   <li>Does not override existing Authorization headers</li>
 *   <li>Validates token format (base64url charset)</li>
 *   <li>Enforces maximum token length (8KB)</li>
 *   <li>Never logs token values</li>
 * </ul>
 */
@Provider
@PreMatching
@Priority(Priorities.AUTHENTICATION - 100)
public class BearerTokenQueryParamFilter implements ContainerRequestFilter {

    private static final Logger LOG = Logger.getLogger(BearerTokenQueryParamFilter.class);

    /** Query parameter name for the bearer token. */
    static final String TOKEN_PARAM = "token";

    /** SSE endpoint path suffix that this filter applies to. */
    static final String SSE_PATH_SUFFIX = "/logs/stream";

    /** Maximum allowed token length (8KB). */
    static final int MAX_TOKEN_LENGTH = 8192;

    /**
     * Valid token characters: base64url charset (RFC 4648) plus JWT segment separator. Matches:
     * A-Z, a-z, 0-9, period, underscore, hyphen.
     */
    static final Pattern TOKEN_PATTERN = Pattern.compile("^[A-Za-z0-9._-]+$");

    @Override
    public void filter(ContainerRequestContext requestContext) {
        UriInfo uriInfo = requestContext.getUriInfo();
        String path = uriInfo.getPath();

        // Only apply to SSE log streaming endpoints
        if (!isSseEndpoint(path)) {
            return;
        }

        // Don't override existing Authorization header
        String existingAuth = requestContext.getHeaderString(HttpHeaders.AUTHORIZATION);
        if (existingAuth != null && !existingAuth.isBlank()) {
            LOG.debugf("SSE request already has Authorization header, skipping token extraction");
            return;
        }

        // Extract token from query parameters
        MultivaluedMap<String, String> queryParams = uriInfo.getQueryParameters();
        List<String> tokenValues = queryParams.get(TOKEN_PARAM);

        if (tokenValues == null || tokenValues.isEmpty()) {
            LOG.debugf("No token query parameter found for SSE endpoint: %s", path);
            return;
        }

        String token = tokenValues.get(0);

        // Validate token
        if (!isValidToken(token)) {
            LOG.warnf("Invalid token format in query parameter for SSE endpoint: %s", path);
            return;
        }

        // Set Authorization header
        requestContext.getHeaders().putSingle(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        LOG.debugf("Set Authorization header from query parameter for SSE endpoint: %s", path);
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

    /**
     * Validates the token format and length.
     *
     * @param token the token to validate
     * @return true if the token is valid
     */
    boolean isValidToken(String token) {
        if (token == null || token.isBlank()) {
            return false;
        }

        if (token.length() > MAX_TOKEN_LENGTH) {
            LOG.warnf("Token exceeds maximum length of %d characters", MAX_TOKEN_LENGTH);
            return false;
        }

        if (!TOKEN_PATTERN.matcher(token).matches()) {
            LOG.warn("Token contains invalid characters");
            return false;
        }

        return true;
    }
}
