/* (C)2026 */
package com.ammann.servicemanager.exception;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("ServiceBlacklistedException")
class ServiceBlacklistedExceptionTest {

    @Test
    @DisplayName("should create exception with container ID")
    void shouldCreateExceptionWithContainerId() {
        String containerId = "abc123def456";

        ServiceBlacklistedException exception = new ServiceBlacklistedException(containerId);

        assertThat(exception.getContainerId()).isEqualTo(containerId);
        assertThat(exception.getMessage())
                .isEqualTo("Container is blacklisted and cannot be modified: abc123def456");
    }

    @Test
    @DisplayName("should be a RuntimeException")
    void shouldBeRuntimeException() {
        ServiceBlacklistedException exception = new ServiceBlacklistedException("test");

        assertThat(exception).isInstanceOf(RuntimeException.class);
    }
}
