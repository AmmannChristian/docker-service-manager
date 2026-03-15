/* (C)2026 */
package com.ammann.servicemanager.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import io.quarkus.security.identity.IdentityProviderManager;
import io.quarkus.security.identity.SecurityIdentity;
import io.quarkus.security.identity.request.TokenAuthenticationRequest;
import io.smallrye.mutiny.Uni;
import io.vertx.core.MultiMap;
import io.vertx.core.http.Cookie;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.ext.web.RoutingContext;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

@DisplayName("BearerTokenSseCookieFilter")
class BearerTokenSseCookieFilterTest {

    BearerTokenSseCookieFilter filter;
    SseTokenStore sseTokenStore;
    RoutingContext rc;
    HttpServerRequest request;
    MultiMap headers;
    IdentityProviderManager identityProviderManager;
    SecurityIdentity mockIdentity;

    @BeforeEach
    void setUp() {
        sseTokenStore = mock(SseTokenStore.class);
        filter = new BearerTokenSseCookieFilter(sseTokenStore);

        rc = mock(RoutingContext.class);
        request = mock(HttpServerRequest.class);
        headers = MultiMap.caseInsensitiveMultiMap();
        identityProviderManager = mock(IdentityProviderManager.class);
        mockIdentity = mock(SecurityIdentity.class);

        when(rc.request()).thenReturn(request);
        when(request.headers()).thenReturn(headers);

        when(identityProviderManager.authenticate(any(TokenAuthenticationRequest.class)))
                .thenReturn(Uni.createFrom().item(mockIdentity));
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
    @DisplayName("Authentication Mechanism Behavior")
    class AuthMechanismBehavior {

        @Test
        @DisplayName(
                "should authenticate via IdentityProviderManager when valid sse-token cookie"
                        + " present")
        void shouldAuthenticateForValidCookie() {
            when(request.path()).thenReturn("/api/v1/containers/abc123/logs/stream");
            when(request.getHeader("Authorization")).thenReturn(null);
            when(request.getCookie("sse-token"))
                    .thenReturn(Cookie.cookie("sse-token", "valid-uuid"));
            when(sseTokenStore.getToken("valid-uuid")).thenReturn(Optional.of("raw.jwt.token"));

            SecurityIdentity result =
                    filter.authenticate(rc, identityProviderManager).await().indefinitely();

            assertThat(result).isSameAs(mockIdentity);

            ArgumentCaptor<TokenAuthenticationRequest> captor =
                    ArgumentCaptor.forClass(TokenAuthenticationRequest.class);
            verify(identityProviderManager).authenticate(captor.capture());
            assertThat(captor.getValue().getToken().getToken()).isEqualTo("raw.jwt.token");
        }

        @Test
        @DisplayName("should remain usable across multiple calls within TTL (reconnect support)")
        void shouldRemainUsableOnReconnect() {
            when(request.path()).thenReturn("/api/v1/containers/abc123/logs/stream");
            when(request.getHeader("Authorization")).thenReturn(null);
            when(request.getCookie("sse-token"))
                    .thenReturn(Cookie.cookie("sse-token", "valid-uuid"));
            when(sseTokenStore.getToken("valid-uuid")).thenReturn(Optional.of("raw.jwt.token"));

            filter.authenticate(rc, identityProviderManager).await().indefinitely();
            filter.authenticate(rc, identityProviderManager).await().indefinitely();

            verify(sseTokenStore, times(2)).getToken("valid-uuid");
            verify(identityProviderManager, times(2))
                    .authenticate(any(TokenAuthenticationRequest.class));
        }

        @Test
        @DisplayName("should return null identity when sse-token cookie is absent")
        void shouldReturnNullWhenCookieAbsent() {
            when(request.path()).thenReturn("/api/v1/containers/abc123/logs/stream");
            when(request.getHeader("Authorization")).thenReturn(null);
            when(request.getCookie("sse-token")).thenReturn(null);

            SecurityIdentity result =
                    filter.authenticate(rc, identityProviderManager).await().indefinitely();

            assertThat(result).isNull();
            verifyNoInteractions(sseTokenStore);
            verifyNoInteractions(identityProviderManager);
        }

        @Test
        @DisplayName("should return null identity when getToken returns empty (expired or unknown)")
        void shouldReturnNullWhenTokenLookupFails() {
            when(request.path()).thenReturn("/api/v1/containers/abc123/logs/stream");
            when(request.getHeader("Authorization")).thenReturn(null);
            when(request.getCookie("sse-token"))
                    .thenReturn(Cookie.cookie("sse-token", "unknown-uuid"));
            when(sseTokenStore.getToken("unknown-uuid")).thenReturn(Optional.empty());

            SecurityIdentity result =
                    filter.authenticate(rc, identityProviderManager).await().indefinitely();

            assertThat(result).isNull();
            verifyNoInteractions(identityProviderManager);
        }

        @Test
        @DisplayName("should not override existing Authorization header")
        void shouldNotOverrideExistingAuthHeader() {
            when(request.path()).thenReturn("/api/v1/containers/abc123/logs/stream");
            when(request.getHeader("Authorization")).thenReturn("Bearer existing.token");
            when(request.getCookie("sse-token"))
                    .thenReturn(Cookie.cookie("sse-token", "valid-uuid"));

            SecurityIdentity result =
                    filter.authenticate(rc, identityProviderManager).await().indefinitely();

            assertThat(result).isNull();
            verifyNoInteractions(sseTokenStore);
            verifyNoInteractions(identityProviderManager);
        }

        @Test
        @DisplayName("should return null identity for non-SSE endpoints")
        void shouldReturnNullForNonSseEndpoints() {
            when(request.path()).thenReturn("/api/v1/containers");

            SecurityIdentity result =
                    filter.authenticate(rc, identityProviderManager).await().indefinitely();

            assertThat(result).isNull();
            verifyNoInteractions(sseTokenStore);
            verifyNoInteractions(identityProviderManager);
        }

        @Test
        @DisplayName("should treat blank Authorization header as absent")
        void shouldTreatBlankAuthHeaderAsAbsent() {
            when(request.path()).thenReturn("/api/v1/containers/abc123/logs/stream");
            when(request.getHeader("Authorization")).thenReturn("   ");
            when(request.getCookie("sse-token"))
                    .thenReturn(Cookie.cookie("sse-token", "valid-uuid"));
            when(sseTokenStore.getToken("valid-uuid")).thenReturn(Optional.of("raw.jwt.token"));

            SecurityIdentity result =
                    filter.authenticate(rc, identityProviderManager).await().indefinitely();

            assertThat(result).isSameAs(mockIdentity);
        }

        @Test
        @DisplayName("should include TokenAuthenticationRequest in credential types")
        void shouldIncludeTokenAuthRequestInCredentialTypes() {
            assertThat(filter.getCredentialTypes())
                    .containsExactly(TokenAuthenticationRequest.class);
        }

        @Test
        @DisplayName("should return null credential transport")
        void shouldReturnNullCredentialTransport() {
            assertThat(filter.getCredentialTransport(rc).await().indefinitely()).isNull();
        }
    }
}
