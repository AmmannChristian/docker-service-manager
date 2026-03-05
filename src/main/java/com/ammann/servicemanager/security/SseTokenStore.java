/* (C)2026 */
package com.ammann.servicemanager.security;

import java.util.Optional;

/**
 * Store for short-lived SSE authentication tokens.
 *
 * <p>Tokens are issued via {@link #createToken(String)} and validated via {@link #getToken(String)}
 * without being consumed on read. This is required because the browser's native {@code EventSource}
 * API auto-reconnects and re-sends the same cookie; the entry must remain valid for the full TTL
 * window.
 *
 * <p>The default implementation is {@link InMemorySseTokenStore}. For multi-pod deployments a
 * shared-store implementation (e.g., Redis) can be provided as a higher-priority CDI bean.
 */
public interface SseTokenStore {

    /**
     * Creates and stores a new short-lived token associated with the given raw bearer token.
     *
     * @param rawToken the bearer token (JWT or opaque) to associate with the new UUID
     * @return a UUID string that acts as the opaque SSE token
     */
    String createToken(String rawToken);

    /**
     * Looks up the bearer token associated with the given UUID.
     *
     * <p>The entry is <em>not</em> removed on read to support {@code EventSource} reconnects.
     * Returns empty if the UUID is unknown, expired, or has exceeded the read-rate limit.
     *
     * @param uuid the SSE token UUID from the {@code sse-token} cookie
     * @return the raw bearer token, or empty if invalid/expired
     */
    Optional<String> getToken(String uuid);

    /** Removes all expired entries from the store. Intended to be called on a schedule. */
    void cleanup();
}