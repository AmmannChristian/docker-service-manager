/* (C)2026 */
package com.ammann.servicemanager.security;

import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import java.time.Instant;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.jboss.logging.Logger;

/**
 * In-memory {@link SseTokenStore} with TTL-based expiry and per-token read-rate limiting.
 *
 * <p>Tokens are <em>not</em> removed on read because the native {@code EventSource} API
 * auto-reconnects and re-sends the same cookie; the entry must stay valid for the entire TTL
 * window. Entries are cleaned up by {@link #cleanup()} which runs on a fixed schedule.
 *
 * <p><strong>TODO (multi-instance)</strong>: This implementation relies on JVM-local state. In
 * multi-pod deployments it requires sticky sessions or a shared store (e.g., Redis). Swap the
 * implementation by providing an alternative {@link SseTokenStore} bean with a higher {@link
 * jakarta.annotation.Priority}.
 */
@ApplicationScoped
public class InMemorySseTokenStore implements SseTokenStore {

    private static final Logger LOG = Logger.getLogger(InMemorySseTokenStore.class);

    /** Time-to-live in seconds for each SSE token. */
    public static final int TTL_SECONDS = 30;

    /**
     * Maximum number of reads allowed per token within its TTL. Limits token reuse in case of
     * cookie theft or aggressive reconnect loops.
     */
    static final int MAX_READS = 50;

    private record SseTokenEntry(String rawToken, Instant expiresAt, AtomicInteger useCount) {}

    private final ConcurrentHashMap<String, SseTokenEntry> store = new ConcurrentHashMap<>();

    @Override
    public String createToken(String rawToken) {
        String uuid = UUID.randomUUID().toString();
        store.put(
                uuid,
                new SseTokenEntry(
                        rawToken, Instant.now().plusSeconds(TTL_SECONDS), new AtomicInteger(0)));
        LOG.debugf("Created SSE token (uuid prefix: %.8s)", uuid);
        return uuid;
    }

    @Override
    public Optional<String> getToken(String uuid) {
        if (uuid == null || uuid.isBlank()) {
            return Optional.empty();
        }

        SseTokenEntry entry = store.get(uuid);
        if (entry == null) {
            LOG.debugf("SSE token not found");
            return Optional.empty();
        }

        if (Instant.now().isAfter(entry.expiresAt())) {
            store.remove(uuid);
            LOG.debugf("SSE token expired, removed from store");
            return Optional.empty();
        }

        int count = entry.useCount().incrementAndGet();
        if (count > MAX_READS) {
            LOG.warnf("SSE token exceeded max reads (%d), rejecting", MAX_READS);
            return Optional.empty();
        }

        LOG.debugf("SSE token valid (read %d/%d)", count, MAX_READS);
        return Optional.of(entry.rawToken());
    }

    @Override
    @Scheduled(every = "60s")
    public void cleanup() {
        Instant now = Instant.now();
        Iterator<Map.Entry<String, SseTokenEntry>> it = store.entrySet().iterator();
        int removed = 0;
        while (it.hasNext()) {
            if (now.isAfter(it.next().getValue().expiresAt())) {
                it.remove();
                removed++;
            }
        }
        if (removed > 0) {
            LOG.debugf("SSE token cleanup: removed %d expired entries", removed);
        }
    }

    /** Package-private: inserts a token entry with a past expiry timestamp for use in tests. */
    void putExpiredToken(String uuid, String rawToken) {
        store.put(
                uuid,
                new SseTokenEntry(rawToken, Instant.now().minusSeconds(1), new AtomicInteger(0)));
    }
}