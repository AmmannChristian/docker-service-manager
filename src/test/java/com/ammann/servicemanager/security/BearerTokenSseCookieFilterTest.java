/* (C)2026 */
package com.ammann.servicemanager.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.Cookie;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MultivaluedHashMap;
import jakarta.ws.rs.core.MultivaluedMap;
import jakarta.ws.rs.core.UriInfo;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("BearerTokenSseCookieFilter")
class BearerTokenSseCookieFilterTest {

    BearerTokenSseCookieFilter filter;
    SseTokenStore sseTokenStore;
    ContainerRequestContext requestContext;
    UriInfo uriInfo;
    MultivaluedMap<String, String> headers;
    Map<String, Cookie> cookies;

    @BeforeEach
    void setUp() {
        sseTokenStore = mock(SseTokenStore.class);
        filter = new BearerTokenSseCookieFilter();
        filter.sseTokenStore = sseTokenStore;

        requestContext = mock(ContainerRequestContext.class);
        uriInfo = mock(UriInfo.class);
        headers = new MultivaluedHashMap<>();
        cookies = new HashMap<>();

        when(requestContext.getUriInfo()).thenReturn(uriInfo);
        when(requestContext.getHeaders()).thenReturn(headers);
        when(requestContext.getCookies()).thenReturn(cookies);
    }

    @Nested
    @DisplayName("SSE Endpoint Detection")
    class SseEndpointDetection {

        @Test
        @DisplayName("should detect SSE endpoint with /logs/stream suffix")
        void shouldDetectSseEndpoint() {
            assertThat(filter.isSseEndpoint("/api/v1/containers/abc123/logs/stream")).isTrue();
        }

        @Test
        @DisplayName("should detect SSE endpoint with only suffix")
        void shouldDetectSseEndpointWithOnlySuffix() {
            assertThat(filter.isSseEndpoint("/logs/stream")).isTrue();
        }

        @Test
        @DisplayName("should not detect non-SSE endpoints")
        void shouldNotDetectNonSseEndpoints() {
            assertThat(filter.isSseEndpoint("/api/v1/containers")).isFalse();
            assertThat(filter.isSseEndpoint("/api/v1/containers/abc123")).isFalse();
            assertThat(filter.isSseEndpoint("/api/v1/containers/abc123/logs")).isFalse();
        }

        @Test
        @DisplayName("should handle null path")
        void shouldHandleNullPath() {
            assertThat(filter.isSseEndpoint(null)).isFalse();
        }

        @Test
        @DisplayName("should not detect partial suffix matches")
        void shouldNotDetectPartialMatches() {
            assertThat(filter.isSseEndpoint("/logs/streaming")).isFalse();
            assertThat(filter.isSseEndpoint("/logs/stream/more")).isFalse();
        }
    }

    @Nested
    @DisplayName("Filter Behavior")
    class FilterBehavior {

        @Test
        @DisplayName("should set Authorization header when valid sse-token cookie present")
        void shouldSetAuthorizationHeaderForValidCookie() {
            when(uriInfo.getPath()).thenReturn("/api/v1/containers/abc123/logs/stream");
            when(requestContext.getHeaderString(HttpHeaders.AUTHORIZATION)).thenReturn(null);
            cookies.put("sse-token", new Cookie("sse-token", "valid-uuid"));
            when(sseTokenStore.getToken("valid-uuid")).thenReturn(Optional.of("raw.jwt.token"));

            filter.filter(requestContext);

            assertThat(headers.getFirst(HttpHeaders.AUTHORIZATION))
                    .isEqualTo("Bearer raw.jwt.token");
        }

        @Test
        @DisplayName("should remain usable across multiple calls within TTL (reconnect support)")
        void shouldRemainUsableOnReconnect() {
            when(uriInfo.getPath()).thenReturn("/api/v1/containers/abc123/logs/stream");
            when(requestContext.getHeaderString(HttpHeaders.AUTHORIZATION)).thenReturn(null);
            cookies.put("sse-token", new Cookie("sse-token", "valid-uuid"));
            when(sseTokenStore.getToken("valid-uuid")).thenReturn(Optional.of("raw.jwt.token"));

            filter.filter(requestContext);
            headers.clear();
            filter.filter(requestContext);

            // getToken called twice (once per connection attempt)
            verify(sseTokenStore, times(2)).getToken("valid-uuid");
            assertThat(headers.getFirst(HttpHeaders.AUTHORIZATION))
                    .isEqualTo("Bearer raw.jwt.token");
        }

        @Test
        @DisplayName("should skip when sse-token cookie is absent")
        void shouldSkipWhenCookieAbsent() {
            when(uriInfo.getPath()).thenReturn("/api/v1/containers/abc123/logs/stream");
            when(requestContext.getHeaderString(HttpHeaders.AUTHORIZATION)).thenReturn(null);
            // No cookie in map

            filter.filter(requestContext);

            assertThat(headers.get(HttpHeaders.AUTHORIZATION)).isNull();
            verifyNoInteractions(sseTokenStore);
        }

        @Test
        @DisplayName("should skip when getToken returns empty (expired or unknown)")
        void shouldSkipWhenTokenLookupFails() {
            when(uriInfo.getPath()).thenReturn("/api/v1/containers/abc123/logs/stream");
            when(requestContext.getHeaderString(HttpHeaders.AUTHORIZATION)).thenReturn(null);
            cookies.put("sse-token", new Cookie("sse-token", "unknown-uuid"));
            when(sseTokenStore.getToken("unknown-uuid")).thenReturn(Optional.empty());

            filter.filter(requestContext);

            assertThat(headers.get(HttpHeaders.AUTHORIZATION)).isNull();
        }

        @Test
        @DisplayName("should not override existing Authorization header")
        void shouldNotOverrideExistingAuthHeader() {
            when(uriInfo.getPath()).thenReturn("/api/v1/containers/abc123/logs/stream");
            when(requestContext.getHeaderString(HttpHeaders.AUTHORIZATION))
                    .thenReturn("Bearer existing.token");
            cookies.put("sse-token", new Cookie("sse-token", "valid-uuid"));

            filter.filter(requestContext);

            assertThat(headers.get(HttpHeaders.AUTHORIZATION)).isNull();
            verifyNoInteractions(sseTokenStore);
        }

        @Test
        @DisplayName("should skip non-SSE endpoints entirely")
        void shouldSkipNonSseEndpoints() {
            when(uriInfo.getPath()).thenReturn("/api/v1/containers");
            cookies.put("sse-token", new Cookie("sse-token", "valid-uuid"));

            filter.filter(requestContext);

            assertThat(headers.get(HttpHeaders.AUTHORIZATION)).isNull();
            verifyNoInteractions(sseTokenStore);
        }

        @Test
        @DisplayName("should treat blank Authorization header as absent")
        void shouldTreatBlankAuthHeaderAsAbsent() {
            when(uriInfo.getPath()).thenReturn("/api/v1/containers/abc123/logs/stream");
            when(requestContext.getHeaderString(HttpHeaders.AUTHORIZATION)).thenReturn("   ");
            cookies.put("sse-token", new Cookie("sse-token", "valid-uuid"));
            when(sseTokenStore.getToken("valid-uuid")).thenReturn(Optional.of("raw.jwt.token"));

            filter.filter(requestContext);

            assertThat(headers.getFirst(HttpHeaders.AUTHORIZATION))
                    .isEqualTo("Bearer raw.jwt.token");
        }
    }
}