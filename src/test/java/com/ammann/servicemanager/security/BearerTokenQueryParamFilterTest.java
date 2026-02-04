/* (C)2026 */
package com.ammann.servicemanager.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MultivaluedHashMap;
import jakarta.ws.rs.core.MultivaluedMap;
import jakarta.ws.rs.core.UriInfo;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("BearerTokenQueryParamFilter")
class BearerTokenQueryParamFilterTest {

    BearerTokenQueryParamFilter filter;
    ContainerRequestContext requestContext;
    UriInfo uriInfo;
    MultivaluedMap<String, String> queryParams;
    MultivaluedMap<String, String> headers;

    @BeforeEach
    void setUp() {
        filter = new BearerTokenQueryParamFilter();
        requestContext = mock(ContainerRequestContext.class);
        uriInfo = mock(UriInfo.class);
        queryParams = new MultivaluedHashMap<>();
        headers = new MultivaluedHashMap<>();

        when(requestContext.getUriInfo()).thenReturn(uriInfo);
        when(uriInfo.getQueryParameters()).thenReturn(queryParams);
        when(requestContext.getHeaders()).thenReturn(headers);
    }

    @Nested
    @DisplayName("SSE Endpoint Detection")
    class SseEndpointDetection {

        @Test
        @DisplayName("should detect SSE endpoint with /logs/stream suffix")
        void shouldDetectSseEndpoint() {
            assertThat(filter.isSseEndpoint("/api/containers/abc123/logs/stream")).isTrue();
        }

        @Test
        @DisplayName("should detect SSE endpoint with only suffix")
        void shouldDetectSseEndpointWithOnlySuffix() {
            assertThat(filter.isSseEndpoint("/logs/stream")).isTrue();
        }

        @Test
        @DisplayName("should not detect non-SSE endpoints")
        void shouldNotDetectNonSseEndpoints() {
            assertThat(filter.isSseEndpoint("/api/containers")).isFalse();
            assertThat(filter.isSseEndpoint("/api/containers/abc123")).isFalse();
            assertThat(filter.isSseEndpoint("/api/containers/abc123/logs")).isFalse();
        }

        @Test
        @DisplayName("should handle null path")
        void shouldHandleNullPath() {
            assertThat(filter.isSseEndpoint(null)).isFalse();
        }

        @Test
        @DisplayName("should not detect partial matches")
        void shouldNotDetectPartialMatches() {
            assertThat(filter.isSseEndpoint("/logs/streaming")).isFalse();
            assertThat(filter.isSseEndpoint("/logs/stream/more")).isFalse();
        }
    }

    @Nested
    @DisplayName("Token Validation")
    class TokenValidation {

        @Test
        @DisplayName("should accept valid JWT-like token")
        void shouldAcceptValidJwtToken() {
            String token =
                    "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxMjM0NTY3ODkwIn0.signature";
            assertThat(filter.isValidToken(token)).isTrue();
        }

        @Test
        @DisplayName("should accept token with base64url characters")
        void shouldAcceptTokenWithBase64UrlChars() {
            assertThat(filter.isValidToken("abc123_-")).isTrue();
            assertThat(filter.isValidToken("ABC.xyz.123")).isTrue();
        }

        @Test
        @DisplayName("should reject null token")
        void shouldRejectNullToken() {
            assertThat(filter.isValidToken(null)).isFalse();
        }

        @Test
        @DisplayName("should reject blank token")
        void shouldRejectBlankToken() {
            assertThat(filter.isValidToken("")).isFalse();
            assertThat(filter.isValidToken("   ")).isFalse();
        }

        @Test
        @DisplayName("should reject token with invalid characters")
        void shouldRejectTokenWithInvalidChars() {
            assertThat(filter.isValidToken("token with spaces")).isFalse();
            assertThat(filter.isValidToken("token+plus")).isFalse();
            assertThat(filter.isValidToken("token/slash")).isFalse();
            assertThat(filter.isValidToken("token=equals")).isFalse();
            assertThat(filter.isValidToken("token@at")).isFalse();
            assertThat(filter.isValidToken("token\nwith\nnewlines")).isFalse();
        }

        @Test
        @DisplayName("should reject oversized token")
        void shouldRejectOversizedToken() {
            String oversizedToken = "a".repeat(BearerTokenQueryParamFilter.MAX_TOKEN_LENGTH + 1);
            assertThat(filter.isValidToken(oversizedToken)).isFalse();
        }

        @Test
        @DisplayName("should accept token at maximum length")
        void shouldAcceptTokenAtMaxLength() {
            String maxToken = "a".repeat(BearerTokenQueryParamFilter.MAX_TOKEN_LENGTH);
            assertThat(filter.isValidToken(maxToken)).isTrue();
        }
    }

    @Nested
    @DisplayName("Filter Behavior")
    class FilterBehavior {

        @Test
        @DisplayName("should set Authorization header for SSE endpoint with token")
        void shouldSetAuthorizationHeaderForSseEndpoint() {
            when(uriInfo.getPath()).thenReturn("/api/containers/abc123/logs/stream");
            when(requestContext.getHeaderString(HttpHeaders.AUTHORIZATION)).thenReturn(null);
            queryParams.put("token", List.of("valid.jwt.token"));

            filter.filter(requestContext);

            assertThat(headers.getFirst(HttpHeaders.AUTHORIZATION)).isEqualTo("Bearer valid.jwt.token");
        }

        @Test
        @DisplayName("should skip non-SSE endpoints")
        void shouldSkipNonSseEndpoints() {
            when(uriInfo.getPath()).thenReturn("/api/containers");
            queryParams.put("token", List.of("valid.jwt.token"));

            filter.filter(requestContext);

            assertThat(headers.get(HttpHeaders.AUTHORIZATION)).isNull();
        }

        @Test
        @DisplayName("should not override existing Authorization header")
        void shouldNotOverrideExistingAuthHeader() {
            when(uriInfo.getPath()).thenReturn("/api/containers/abc123/logs/stream");
            when(requestContext.getHeaderString(HttpHeaders.AUTHORIZATION))
                    .thenReturn("Bearer existing.token");
            queryParams.put("token", List.of("new.token"));

            filter.filter(requestContext);

            // Headers map should remain empty (existing header is not in our mock headers map)
            assertThat(headers.get(HttpHeaders.AUTHORIZATION)).isNull();
        }

        @Test
        @DisplayName("should skip when no token parameter present")
        void shouldSkipWhenNoTokenPresent() {
            when(uriInfo.getPath()).thenReturn("/api/containers/abc123/logs/stream");
            when(requestContext.getHeaderString(HttpHeaders.AUTHORIZATION)).thenReturn(null);
            // No token in query params

            filter.filter(requestContext);

            assertThat(headers.get(HttpHeaders.AUTHORIZATION)).isNull();
        }

        @Test
        @DisplayName("should skip when token parameter is empty list")
        void shouldSkipWhenTokenParamEmpty() {
            when(uriInfo.getPath()).thenReturn("/api/containers/abc123/logs/stream");
            when(requestContext.getHeaderString(HttpHeaders.AUTHORIZATION)).thenReturn(null);
            queryParams.put("token", List.of());

            filter.filter(requestContext);

            assertThat(headers.get(HttpHeaders.AUTHORIZATION)).isNull();
        }

        @Test
        @DisplayName("should skip when token is invalid")
        void shouldSkipWhenTokenInvalid() {
            when(uriInfo.getPath()).thenReturn("/api/containers/abc123/logs/stream");
            when(requestContext.getHeaderString(HttpHeaders.AUTHORIZATION)).thenReturn(null);
            queryParams.put("token", List.of("invalid token with spaces"));

            filter.filter(requestContext);

            assertThat(headers.get(HttpHeaders.AUTHORIZATION)).isNull();
        }

        @Test
        @DisplayName("should use first token when multiple provided")
        void shouldUseFirstTokenWhenMultiple() {
            when(uriInfo.getPath()).thenReturn("/api/containers/abc123/logs/stream");
            when(requestContext.getHeaderString(HttpHeaders.AUTHORIZATION)).thenReturn(null);
            queryParams.put("token", List.of("first.token", "second.token"));

            filter.filter(requestContext);

            assertThat(headers.getFirst(HttpHeaders.AUTHORIZATION)).isEqualTo("Bearer first.token");
        }

        @Test
        @DisplayName("should not override blank existing Authorization header")
        void shouldOverrideBlankAuthHeader() {
            when(uriInfo.getPath()).thenReturn("/api/containers/abc123/logs/stream");
            when(requestContext.getHeaderString(HttpHeaders.AUTHORIZATION)).thenReturn("   ");
            queryParams.put("token", List.of("valid.token"));

            filter.filter(requestContext);

            // Blank header is treated as not present, so token should be set
            assertThat(headers.getFirst(HttpHeaders.AUTHORIZATION)).isEqualTo("Bearer valid.token");
        }
    }
}
