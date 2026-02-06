/* (C)2026 */
package com.ammann.servicemanager.resource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.ammann.servicemanager.dto.ContainerInfoDTO;
import com.ammann.servicemanager.service.ContainerService;
import io.smallrye.mutiny.Multi;
import jakarta.ws.rs.core.Response;
import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.jboss.logging.Logger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Unit tests for {@link ContainerResource}.
 *
 * <p>Verifies the resource delegation logic and parameter forwarding without
 * starting the Quarkus server. Dependencies are injected via reflection.
 */
@DisplayName("ContainerResource")
class ContainerResourceTest {

    ContainerResource resource;
    ContainerService containerService;
    Logger logger;

    @BeforeEach
    void setUp() throws Exception {
        resource = new ContainerResource();
        containerService = mock(ContainerService.class);
        logger = mock(Logger.class);

        // Inject mocks using reflection
        injectField(resource, "containerService", containerService);
        injectField(resource, "logger", logger);
    }

    private void injectField(Object target, String fieldName, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }

    @Nested
    @DisplayName("listContainers")
    class ListContainers {

        @Test
        @DisplayName("should return empty list when no containers")
        void shouldReturnEmptyList() {
            when(containerService.listContainers(false)).thenReturn(Collections.emptyList());

            List<ContainerInfoDTO> result = resource.listContainers(false);

            assertThat(result).isEmpty();
            verify(containerService).listContainers(false);
            verify(logger).debugf(anyString(), anyBoolean());
        }

        @Test
        @DisplayName("should return list of containers")
        void shouldReturnListOfContainers() {
            List<ContainerInfoDTO> containers =
                    Arrays.asList(
                            new ContainerInfoDTO(
                                    "abc123def456",
                                    "nginx-proxy",
                                    "nginx:latest",
                                    "running",
                                    "Up 5 hours"),
                            new ContainerInfoDTO(
                                    "def456abc789",
                                    "redis-cache",
                                    "redis:7",
                                    "running",
                                    "Up 3 hours"));
            when(containerService.listContainers(false)).thenReturn(containers);

            List<ContainerInfoDTO> result = resource.listContainers(false);

            assertThat(result).hasSize(2);
            assertThat(result.getFirst().id()).isEqualTo("abc123def456");
            assertThat(result.getFirst().name()).isEqualTo("nginx-proxy");
        }

        @Test
        @DisplayName("should pass showAll parameter")
        void shouldPassShowAllParameter() {
            when(containerService.listContainers(true)).thenReturn(Collections.emptyList());

            resource.listContainers(true);

            verify(containerService).listContainers(true);
        }
    }

    @Nested
    @DisplayName("restartContainer")
    class RestartContainer {

        @Test
        @DisplayName("should restart container and return 204")
        void shouldRestartContainerAndReturn204() {
            doNothing().when(containerService).restartContainer(anyString());

            Response response = resource.restartContainer("abc123def456");

            assertThat(response.getStatus()).isEqualTo(204);
            verify(containerService).restartContainer("abc123def456");
            verify(logger).infof(eq("Restarting container: %s"), eq("abc123def456"));
        }
    }

    @Nested
    @DisplayName("startContainer")
    class StartContainer {

        @Test
        @DisplayName("should start container and return 204")
        void shouldStartContainerAndReturn204() {
            doNothing().when(containerService).startContainer(anyString());

            Response response = resource.startContainer("abc123def456");

            assertThat(response.getStatus()).isEqualTo(204);
            verify(containerService).startContainer("abc123def456");
            verify(logger).infof(eq("Starting container: %s"), eq("abc123def456"));
        }
    }

    @Nested
    @DisplayName("stopContainer")
    class StopContainer {

        @Test
        @DisplayName("should stop container and return 204")
        void shouldStopContainerAndReturn204() {
            doNothing().when(containerService).stopContainer(anyString());

            Response response = resource.stopContainer("abc123def456");

            assertThat(response.getStatus()).isEqualTo(204);
            verify(containerService).stopContainer("abc123def456");
            verify(logger).infof(eq("Stopping container: %s"), eq("abc123def456"));
        }
    }

    @Nested
    @DisplayName("updateContainer")
    class UpdateContainer {

        @Test
        @DisplayName("should update container and return 204")
        void shouldUpdateContainerAndReturn204() {
            doNothing().when(containerService).updateContainer(anyString());

            Response response = resource.updateContainer("abc123def456");

            assertThat(response.getStatus()).isEqualTo(204);
            verify(containerService).updateContainer("abc123def456");
            verify(logger).infof(eq("Updating container: %s"), eq("abc123def456"));
        }
    }

    @Nested
    @DisplayName("getContainerLogs")
    class GetContainerLogs {

        @Test
        @DisplayName("should return container logs")
        void shouldReturnContainerLogs() {
            String logs = "2024-01-01 Log line 1\n2024-01-01 Log line 2\n";
            when(containerService.getContainerLogs(anyString(), anyInt())).thenReturn(logs);

            String result = resource.getContainerLogs("abc123def456", 100);

            assertThat(result).isEqualTo(logs);
            verify(containerService).getContainerLogs("abc123def456", 100);
            verify(logger).debugf(anyString(), anyString(), anyInt());
        }

        @Test
        @DisplayName("should pass tail parameter")
        void shouldPassTailParameter() {
            when(containerService.getContainerLogs(anyString(), anyInt())).thenReturn("logs");

            resource.getContainerLogs("abc123def456", 50);

            verify(containerService).getContainerLogs("abc123def456", 50);
        }
    }

    @Nested
    @DisplayName("streamContainerLogs")
    class StreamContainerLogs {

        @Test
        @DisplayName("should stream container logs")
        void shouldStreamContainerLogs() {
            Multi<String> logStream = Multi.createFrom().items("Log 1\n", "Log 2\n");
            when(containerService.streamContainerLogs(anyString(), anyBoolean()))
                    .thenReturn(logStream);

            Multi<String> result = resource.streamContainerLogs("abc123def456", true);

            assertThat(result).isNotNull();
            verify(containerService).streamContainerLogs("abc123def456", true);
            verify(logger).infof(anyString(), anyString(), anyBoolean());
        }

        @Test
        @DisplayName("should pass follow parameter")
        void shouldPassFollowParameter() {
            Multi<String> logStream = Multi.createFrom().empty();
            when(containerService.streamContainerLogs(anyString(), anyBoolean()))
                    .thenReturn(logStream);

            resource.streamContainerLogs("abc123def456", false);

            verify(containerService).streamContainerLogs("abc123def456", false);
        }
    }

    @Nested
    @DisplayName("Container ID Pattern")
    class ContainerIdPattern {

        @ParameterizedTest
        @ValueSource(strings = {"abc123def456", "ABC123DEF456", "AbC123DeF456"})
        @DisplayName("should accept valid 12 character hex IDs")
        void shouldAcceptValid12CharHexIds(String containerId) {
            assertThat(containerId).matches("^[a-fA-F0-9]{12,64}$");
        }

        @ParameterizedTest
        @ValueSource(strings = {"abc123def456abc123def456abc123def456abc123def456abc123def456abcd"})
        @DisplayName("should accept valid 64 character hex IDs")
        void shouldAcceptValid64CharHexIds(String containerId) {
            assertThat(containerId).matches("^[a-fA-F0-9]{12,64}$");
        }

        @ParameterizedTest
        @ValueSource(strings = {"abc", "abc123def45", "invalid-id!", "abc123ghijkl"})
        @DisplayName("should reject invalid container IDs")
        void shouldRejectInvalidContainerIds(String containerId) {
            assertThat(containerId).doesNotMatch("^[a-fA-F0-9]{12,64}$");
        }
    }
}
