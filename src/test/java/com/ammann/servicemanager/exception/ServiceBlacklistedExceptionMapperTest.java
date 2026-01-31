/* (C)2026 */
package com.ammann.servicemanager.exception;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.ws.rs.core.Response;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("ServiceBlacklistedExceptionMapper")
class ServiceBlacklistedExceptionMapperTest {

    private ServiceBlacklistedExceptionMapper mapper;

    @BeforeEach
    void setUp() {
        mapper = new ServiceBlacklistedExceptionMapper();
    }

    @Test
    @DisplayName("should return 403 Forbidden status")
    void shouldReturn403ForbiddenStatus() {
        ServiceBlacklistedException exception = new ServiceBlacklistedException("container-123");

        Response response = mapper.toResponse(exception);

        assertThat(response.getStatus()).isEqualTo(Response.Status.FORBIDDEN.getStatusCode());
    }

    @Test
    @DisplayName("should return JSON error body with correct fields")
    @SuppressWarnings("unchecked")
    void shouldReturnJsonErrorBody() {
        String containerId = "my-protected-container";
        ServiceBlacklistedException exception = new ServiceBlacklistedException(containerId);

        Response response = mapper.toResponse(exception);

        Map<String, String> entity = (Map<String, String>) response.getEntity();
        assertThat(entity).containsEntry("error", "Forbidden");
        assertThat(entity).containsEntry("containerId", containerId);
        assertThat(entity)
                .containsEntry(
                        "message",
                        "Container is blacklisted and cannot be modified: my-protected-container");
    }
}
