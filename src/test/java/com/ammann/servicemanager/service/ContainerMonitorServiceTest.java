/* (C)2026 */
package com.ammann.servicemanager.service;

import static org.mockito.Mockito.*;

import com.ammann.servicemanager.dto.ContainerInfoDTO;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.jboss.logging.Logger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("ContainerMonitorService")
class ContainerMonitorServiceTest {

    ContainerMonitorService monitorService;
    ContainerService containerService;
    Logger logger;

    @BeforeEach
    void setUp() throws Exception {
        monitorService = new ContainerMonitorService();
        containerService = mock(ContainerService.class);
        logger = mock(Logger.class);

        java.lang.reflect.Field containerServiceField =
                ContainerMonitorService.class.getDeclaredField("containerService");
        containerServiceField.setAccessible(true);
        containerServiceField.set(monitorService, containerService);

        java.lang.reflect.Field loggerField =
                ContainerMonitorService.class.getDeclaredField("logger");
        loggerField.setAccessible(true);
        loggerField.set(monitorService, logger);
    }

    @Nested
    @DisplayName("checkForUpdates")
    class CheckForUpdates {

        @Test
        @DisplayName("should check all running containers for updates")
        void shouldCheckAllRunningContainersForUpdates() {
            List<ContainerInfoDTO> containers =
                    Arrays.asList(
                            new ContainerInfoDTO(
                                    "id1",
                                    "container1",
                                    "docker.io/library/nginx:latest",
                                    "running",
                                    "Up 5 hours"),
                            new ContainerInfoDTO(
                                    "id2",
                                    "container2",
                                    "ghcr.io/myorg/redis:7",
                                    "running",
                                    "Up 3 hours"));

            when(containerService.listContainers(false)).thenReturn(containers);
            when(containerService.checkForUpdate("docker.io/library/nginx:latest"))
                    .thenReturn(false);
            when(containerService.checkForUpdate("ghcr.io/myorg/redis:7")).thenReturn(true);

            monitorService.checkForUpdates();

            verify(containerService).listContainers(false);
            verify(containerService).checkForUpdate("docker.io/library/nginx:latest");
            verify(containerService).checkForUpdate("ghcr.io/myorg/redis:7");
        }

        @Test
        @DisplayName("should handle empty container list")
        void shouldHandleEmptyContainerList() {
            when(containerService.listContainers(false)).thenReturn(Collections.emptyList());

            monitorService.checkForUpdates();

            verify(containerService).listContainers(false);
            verify(containerService, never()).checkForUpdate(anyString());
        }

        @Test
        @DisplayName("should continue checking other containers when one fails")
        void shouldContinueWhenOneContainerFails() {
            List<ContainerInfoDTO> containers =
                    Arrays.asList(
                            new ContainerInfoDTO(
                                    "id1",
                                    "container1",
                                    "docker.io/library/nginx:latest",
                                    "running",
                                    "Up 5 hours"),
                            new ContainerInfoDTO(
                                    "id2",
                                    "container2",
                                    "ghcr.io/myorg/redis:7",
                                    "running",
                                    "Up 3 hours"),
                            new ContainerInfoDTO(
                                    "id3",
                                    "container3",
                                    "quay.io/myorg/postgres:15",
                                    "running",
                                    "Up 1 hour"));

            when(containerService.listContainers(false)).thenReturn(containers);
            when(containerService.checkForUpdate("docker.io/library/nginx:latest"))
                    .thenReturn(false);
            when(containerService.checkForUpdate("ghcr.io/myorg/redis:7"))
                    .thenThrow(new RuntimeException("Connection error"));
            when(containerService.checkForUpdate("quay.io/myorg/postgres:15")).thenReturn(true);

            monitorService.checkForUpdates();

            verify(containerService).checkForUpdate("docker.io/library/nginx:latest");
            verify(containerService).checkForUpdate("ghcr.io/myorg/redis:7");
            verify(containerService).checkForUpdate("quay.io/myorg/postgres:15");
        }

        @Test
        @DisplayName("should log when update is available")
        void shouldLogWhenUpdateAvailable() {
            List<ContainerInfoDTO> containers =
                    Collections.singletonList(
                            new ContainerInfoDTO(
                                    "id1",
                                    "my-app",
                                    "ghcr.io/myorg/myapp:v1",
                                    "running",
                                    "Up 5 hours"));

            when(containerService.listContainers(false)).thenReturn(containers);
            when(containerService.checkForUpdate("ghcr.io/myorg/myapp:v1")).thenReturn(true);

            monitorService.checkForUpdates();

            verify(containerService).checkForUpdate("ghcr.io/myorg/myapp:v1");
        }
    }

    @Nested
    @DisplayName("healthCheck")
    class HealthCheck {

        @Test
        @DisplayName("should check health of all running containers")
        void shouldCheckHealthOfAllContainers() {
            List<ContainerInfoDTO> containers =
                    Arrays.asList(
                            new ContainerInfoDTO(
                                    "id1", "container1", "nginx:latest", "running", "Up 5 hours"),
                            new ContainerInfoDTO(
                                    "id2", "container2", "redis:7", "running", "Up 3 hours"));

            when(containerService.listContainers(false)).thenReturn(containers);

            monitorService.healthCheck();

            verify(containerService).listContainers(false);
        }

        @Test
        @DisplayName("should warn when container is not running")
        void shouldWarnWhenContainerNotRunning() {
            List<ContainerInfoDTO> containers =
                    Arrays.asList(
                            new ContainerInfoDTO(
                                    "id1", "container1", "nginx:latest", "running", "Up 5 hours"),
                            new ContainerInfoDTO(
                                    "id2",
                                    "container2",
                                    "redis:7",
                                    "exited",
                                    "Exited (0) 1 hour ago"),
                            new ContainerInfoDTO(
                                    "id3", "container3", "postgres:15", "paused", "Paused"));

            when(containerService.listContainers(false)).thenReturn(containers);

            monitorService.healthCheck();

            verify(containerService).listContainers(false);
        }

        @Test
        @DisplayName("should handle empty container list")
        void shouldHandleEmptyContainerList() {
            when(containerService.listContainers(false)).thenReturn(Collections.emptyList());

            monitorService.healthCheck();

            verify(containerService).listContainers(false);
        }

        @Test
        @DisplayName("should handle case-insensitive running state")
        void shouldHandleCaseInsensitiveRunningState() {
            List<ContainerInfoDTO> containers =
                    Arrays.asList(
                            new ContainerInfoDTO(
                                    "id1", "container1", "nginx:latest", "Running", "Up 5 hours"),
                            new ContainerInfoDTO(
                                    "id2", "container2", "redis:7", "RUNNING", "Up 3 hours"));

            when(containerService.listContainers(false)).thenReturn(containers);

            monitorService.healthCheck();

            verify(containerService).listContainers(false);
        }
    }
}
