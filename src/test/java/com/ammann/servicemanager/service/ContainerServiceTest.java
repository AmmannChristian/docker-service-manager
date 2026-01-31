/* (C)2026 */
package com.ammann.servicemanager.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.ammann.servicemanager.config.ServiceBlacklistConfig;
import com.ammann.servicemanager.dto.ContainerInfoDTO;
import com.ammann.servicemanager.exception.ServiceBlacklistedException;
import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.async.ResultCallback;
import com.github.dockerjava.api.command.*;
import com.github.dockerjava.api.model.Container;
import com.github.dockerjava.api.model.ContainerConfig;
import com.github.dockerjava.api.model.Frame;
import com.github.dockerjava.api.model.Image;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.helpers.test.AssertSubscriber;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import org.jboss.logging.Logger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("ContainerService")
class ContainerServiceTest {

    ContainerService containerService;
    DockerClient dockerClient;
    Logger logger;
    ServiceBlacklistConfig blacklistConfig;

    @BeforeEach
    void setUp() throws Exception {
        containerService = new ContainerService();
        dockerClient = mock(DockerClient.class);
        logger = mock(Logger.class);
        blacklistConfig = mock(ServiceBlacklistConfig.class);

        // Default: empty blacklist
        when(blacklistConfig.blacklist()).thenReturn(Optional.empty());
        when(blacklistConfig.isBlacklisted(anyString(), anyString(), anyString()))
                .thenReturn(false);

        // Inject mocks using reflection
        java.lang.reflect.Field dockerClientField =
                ContainerService.class.getDeclaredField("dockerClient");
        dockerClientField.setAccessible(true);
        dockerClientField.set(containerService, dockerClient);

        java.lang.reflect.Field loggerField = ContainerService.class.getDeclaredField("logger");
        loggerField.setAccessible(true);
        loggerField.set(containerService, logger);

        java.lang.reflect.Field blacklistField =
                ContainerService.class.getDeclaredField("blacklistConfig");
        blacklistField.setAccessible(true);
        blacklistField.set(containerService, blacklistConfig);
    }

    private void setupInspectForBlacklistCheck(
            String containerId, String containerName, String imageName) {
        InspectContainerCmd inspectCmd = mock(InspectContainerCmd.class);
        InspectContainerResponse inspectResponse = mock(InspectContainerResponse.class);
        ContainerConfig config = mock(ContainerConfig.class);
        when(dockerClient.inspectContainerCmd(containerId)).thenReturn(inspectCmd);
        when(inspectCmd.exec()).thenReturn(inspectResponse);
        when(inspectResponse.getName()).thenReturn("/" + containerName);
        when(inspectResponse.getConfig()).thenReturn(config);
        when(config.getImage()).thenReturn(imageName);
    }

    @Nested
    @DisplayName("listContainers")
    class ListContainers {

        @Test
        @DisplayName("should return empty list when no containers exist")
        void shouldReturnEmptyListWhenNoContainers() {
            ListContainersCmd listCmd = mock(ListContainersCmd.class);
            when(dockerClient.listContainersCmd()).thenReturn(listCmd);
            when(listCmd.withShowAll(anyBoolean())).thenReturn(listCmd);
            when(listCmd.exec()).thenReturn(Collections.emptyList());

            List<ContainerInfoDTO> result = containerService.listContainers(false);

            assertThat(result).isEmpty();
            verify(listCmd).withShowAll(false);
        }

        @Test
        @DisplayName("should return mapped containers")
        void shouldReturnMappedContainers() {
            Container container1 =
                    createMockContainer(
                            "id1", "/container1", "nginx:latest", "running", "Up 5 hours");
            Container container2 =
                    createMockContainer(
                            "id2", "/container2", "redis:7", "exited", "Exited (0) 1 hour ago");

            ListContainersCmd listCmd = mock(ListContainersCmd.class);
            when(dockerClient.listContainersCmd()).thenReturn(listCmd);
            when(listCmd.withShowAll(anyBoolean())).thenReturn(listCmd);
            when(listCmd.exec()).thenReturn(Arrays.asList(container1, container2));

            List<ContainerInfoDTO> result = containerService.listContainers(true);

            assertThat(result).hasSize(2);
            assertThat(result.getFirst().id()).isEqualTo("id1");
            assertThat(result.getFirst().name()).isEqualTo("/container1");
            assertThat(result.getFirst().image()).isEqualTo("nginx:latest");
            assertThat(result.getFirst().state()).isEqualTo("running");
            assertThat(result.getFirst().status()).isEqualTo("Up 5 hours");

            assertThat(result.get(1).id()).isEqualTo("id2");
            assertThat(result.get(1).state()).isEqualTo("exited");

            verify(listCmd).withShowAll(true);
        }
    }

    @Nested
    @DisplayName("restartContainer")
    class RestartContainer {

        @Test
        @DisplayName("should restart container with timeout")
        void shouldRestartContainerWithTimeout() {
            setupInspectForBlacklistCheck("test-container-id", "test-container", "nginx:latest");
            RestartContainerCmd restartCmd = mock(RestartContainerCmd.class);
            when(dockerClient.restartContainerCmd(anyString())).thenReturn(restartCmd);
            when(restartCmd.withTimeout(anyInt())).thenReturn(restartCmd);

            containerService.restartContainer("test-container-id");

            verify(dockerClient).restartContainerCmd("test-container-id");
            verify(restartCmd).withTimeout(10);
            verify(restartCmd).exec();
        }

        @Test
        @DisplayName("should throw exception when container is blacklisted")
        void shouldThrowExceptionWhenBlacklisted() {
            String containerId = "protected-container-id";
            setupInspectForBlacklistCheck(containerId, "traefik", "traefik:v3.1");
            when(blacklistConfig.isBlacklisted(containerId, "traefik", "traefik:v3.1"))
                    .thenReturn(true);

            assertThatThrownBy(() -> containerService.restartContainer(containerId))
                    .isInstanceOf(ServiceBlacklistedException.class)
                    .hasMessageContaining(containerId);

            verify(dockerClient, never()).restartContainerCmd(anyString());
        }
    }

    @Nested
    @DisplayName("stopContainer")
    class StopContainer {

        @Test
        @DisplayName("should stop container with timeout")
        void shouldStopContainerWithTimeout() {
            setupInspectForBlacklistCheck("test-container-id", "test-container", "nginx:latest");
            StopContainerCmd stopCmd = mock(StopContainerCmd.class);
            when(dockerClient.stopContainerCmd(anyString())).thenReturn(stopCmd);
            when(stopCmd.withTimeout(anyInt())).thenReturn(stopCmd);

            containerService.stopContainer("test-container-id");

            verify(dockerClient).stopContainerCmd("test-container-id");
            verify(stopCmd).withTimeout(10);
            verify(stopCmd).exec();
        }

        @Test
        @DisplayName("should throw exception when container is blacklisted")
        void shouldThrowExceptionWhenBlacklisted() {
            String containerId = "protected-container-id";
            setupInspectForBlacklistCheck(
                    containerId, "docker-proxy", "tecnativa/docker-socket-proxy:latest");
            when(blacklistConfig.isBlacklisted(
                            containerId, "docker-proxy", "tecnativa/docker-socket-proxy:latest"))
                    .thenReturn(true);

            assertThatThrownBy(() -> containerService.stopContainer(containerId))
                    .isInstanceOf(ServiceBlacklistedException.class)
                    .hasMessageContaining(containerId);

            verify(dockerClient, never()).stopContainerCmd(anyString());
        }
    }

    @Nested
    @DisplayName("startContainer")
    class StartContainer {

        @Test
        @DisplayName("should start container")
        void shouldStartContainer() {
            StartContainerCmd startCmd = mock(StartContainerCmd.class);
            when(dockerClient.startContainerCmd(anyString())).thenReturn(startCmd);

            containerService.startContainer("test-container-id");

            verify(dockerClient).startContainerCmd("test-container-id");
            verify(startCmd).exec();
        }
    }

    @Nested
    @DisplayName("checkForUpdate")
    class CheckForUpdate {

        @Test
        @DisplayName("should return false when no local image exists")
        void shouldReturnFalseWhenNoLocalImage() {
            ListImagesCmd listImagesCmd = mock(ListImagesCmd.class);
            when(dockerClient.listImagesCmd()).thenReturn(listImagesCmd);
            when(listImagesCmd.withImageNameFilter(anyString())).thenReturn(listImagesCmd);
            when(listImagesCmd.exec()).thenReturn(Collections.emptyList());

            boolean result = containerService.checkForUpdate("nginx:latest");

            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("should return false when image is up to date")
        void shouldReturnFalseWhenImageUpToDate() throws InterruptedException {
            Image localImage = mock(Image.class);
            when(localImage.getId()).thenReturn("sha256:abc123");

            ListImagesCmd listImagesCmd = mock(ListImagesCmd.class);
            when(dockerClient.listImagesCmd()).thenReturn(listImagesCmd);
            when(listImagesCmd.withImageNameFilter(anyString())).thenReturn(listImagesCmd);
            when(listImagesCmd.exec()).thenReturn(Collections.singletonList(localImage));

            PullImageCmd pullCmd = mock(PullImageCmd.class);
            PullImageResultCallback callback = mock(PullImageResultCallback.class);
            when(dockerClient.pullImageCmd(anyString())).thenReturn(pullCmd);
            when(pullCmd.exec(any(PullImageResultCallback.class))).thenReturn(callback);
            when(callback.awaitCompletion()).thenReturn(callback);

            boolean result = containerService.checkForUpdate("nginx:latest");

            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("should return true when update is available")
        void shouldReturnTrueWhenUpdateAvailable() throws InterruptedException {
            Image localImage = mock(Image.class);
            when(localImage.getId()).thenReturn("sha256:abc123");

            Image updatedImage = mock(Image.class);
            when(updatedImage.getId()).thenReturn("sha256:def456");

            ListImagesCmd listImagesCmd = mock(ListImagesCmd.class);
            when(dockerClient.listImagesCmd()).thenReturn(listImagesCmd);
            when(listImagesCmd.withImageNameFilter(anyString())).thenReturn(listImagesCmd);
            when(listImagesCmd.exec())
                    .thenReturn(Collections.singletonList(localImage))
                    .thenReturn(Collections.singletonList(updatedImage));

            PullImageCmd pullCmd = mock(PullImageCmd.class);
            PullImageResultCallback callback = mock(PullImageResultCallback.class);
            when(dockerClient.pullImageCmd(anyString())).thenReturn(pullCmd);
            when(pullCmd.exec(any(PullImageResultCallback.class))).thenReturn(callback);
            when(callback.awaitCompletion()).thenReturn(callback);

            boolean result = containerService.checkForUpdate("nginx:latest");

            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("should return false on interrupted exception")
        void shouldReturnFalseOnInterruptedException() throws InterruptedException {
            Image localImage = mock(Image.class);
            when(localImage.getId()).thenReturn("sha256:abc123");

            ListImagesCmd listImagesCmd = mock(ListImagesCmd.class);
            when(dockerClient.listImagesCmd()).thenReturn(listImagesCmd);
            when(listImagesCmd.withImageNameFilter(anyString())).thenReturn(listImagesCmd);
            when(listImagesCmd.exec()).thenReturn(Collections.singletonList(localImage));

            PullImageCmd pullCmd = mock(PullImageCmd.class);
            PullImageResultCallback callback = mock(PullImageResultCallback.class);
            when(dockerClient.pullImageCmd(anyString())).thenReturn(pullCmd);
            when(pullCmd.exec(any(PullImageResultCallback.class))).thenReturn(callback);
            when(callback.awaitCompletion())
                    .thenThrow(new InterruptedException("Test interruption"));

            boolean result = containerService.checkForUpdate("nginx:latest");

            assertThat(result).isFalse();
            assertThat(Thread.currentThread().isInterrupted()).isTrue();
            // Clear interrupt status for other tests
            boolean isInterrupted = Thread.interrupted();
            assertThat(isInterrupted).isTrue();
        }
    }

    @Nested
    @DisplayName("updateContainer")
    class UpdateContainer {

        @Test
        @DisplayName("should throw exception when container is blacklisted")
        void shouldThrowExceptionWhenBlacklisted() {
            String containerId = "protected-container-id";
            setupInspectForBlacklistCheck(containerId, "critical-service", "myapp:latest");
            when(blacklistConfig.isBlacklisted(containerId, "critical-service", "myapp:latest"))
                    .thenReturn(true);

            assertThatThrownBy(() -> containerService.updateContainer(containerId))
                    .isInstanceOf(ServiceBlacklistedException.class)
                    .hasMessageContaining(containerId);

            verify(dockerClient, never()).pullImageCmd(anyString());
        }

        @Test
        @DisplayName("should update container successfully")
        void shouldUpdateContainerSuccessfully() throws InterruptedException {
            String containerId = "old-container-id";
            String newContainerId = "new-container-id";
            String imageName = "nginx:latest";

            // Mock inspect (called twice: once for blacklist check, once for update)
            InspectContainerCmd inspectCmd = mock(InspectContainerCmd.class);
            InspectContainerResponse inspectResponse = mock(InspectContainerResponse.class);
            ContainerConfig config = mock(ContainerConfig.class);
            when(dockerClient.inspectContainerCmd(containerId)).thenReturn(inspectCmd);
            when(inspectCmd.exec()).thenReturn(inspectResponse);
            when(inspectResponse.getConfig()).thenReturn(config);
            when(config.getImage()).thenReturn(imageName);
            when(inspectResponse.getName()).thenReturn("/test-container");
            when(config.getEnv()).thenReturn(new String[] {"ENV=test"});

            // Mock pull
            PullImageCmd pullCmd = mock(PullImageCmd.class);
            PullImageResultCallback pullCallback = mock(PullImageResultCallback.class);
            when(dockerClient.pullImageCmd(imageName)).thenReturn(pullCmd);
            when(pullCmd.exec(any(PullImageResultCallback.class))).thenReturn(pullCallback);
            when(pullCallback.awaitCompletion()).thenReturn(pullCallback);

            // Mock stop
            StopContainerCmd stopCmd = mock(StopContainerCmd.class);
            when(dockerClient.stopContainerCmd(containerId)).thenReturn(stopCmd);
            when(stopCmd.withTimeout(anyInt())).thenReturn(stopCmd);

            // Mock remove
            RemoveContainerCmd removeCmd = mock(RemoveContainerCmd.class);
            when(dockerClient.removeContainerCmd(containerId)).thenReturn(removeCmd);

            // Mock create
            CreateContainerCmd createCmd = mock(CreateContainerCmd.class);
            CreateContainerResponse createResponse = mock(CreateContainerResponse.class);
            when(dockerClient.createContainerCmd(imageName)).thenReturn(createCmd);
            when(createCmd.withName(anyString())).thenReturn(createCmd);
            when(createCmd.withEnv(any(String[].class))).thenReturn(createCmd);
            when(createCmd.exec()).thenReturn(createResponse);
            when(createResponse.getId()).thenReturn(newContainerId);

            // Mock start
            StartContainerCmd startCmd = mock(StartContainerCmd.class);
            when(dockerClient.startContainerCmd(newContainerId)).thenReturn(startCmd);

            containerService.updateContainer(containerId);

            verify(dockerClient, atLeast(1)).inspectContainerCmd(containerId);
            verify(dockerClient).pullImageCmd(imageName);
            verify(dockerClient).stopContainerCmd(containerId);
            verify(dockerClient).removeContainerCmd(containerId);
            verify(dockerClient).createContainerCmd(imageName);
            verify(dockerClient).startContainerCmd(newContainerId);
        }

        @Test
        @DisplayName("should handle interrupted exception during update")
        void shouldHandleInterruptedExceptionDuringUpdate() throws InterruptedException {
            String containerId = "test-container-id";
            String imageName = "nginx:latest";

            InspectContainerCmd inspectCmd = mock(InspectContainerCmd.class);
            InspectContainerResponse inspectResponse = mock(InspectContainerResponse.class);
            ContainerConfig config = mock(ContainerConfig.class);
            when(dockerClient.inspectContainerCmd(containerId)).thenReturn(inspectCmd);
            when(inspectCmd.exec()).thenReturn(inspectResponse);
            when(inspectResponse.getConfig()).thenReturn(config);
            when(config.getImage()).thenReturn(imageName);
            when(inspectResponse.getName()).thenReturn("/test-container");

            PullImageCmd pullCmd = mock(PullImageCmd.class);
            PullImageResultCallback pullCallback = mock(PullImageResultCallback.class);
            when(dockerClient.pullImageCmd(imageName)).thenReturn(pullCmd);
            when(pullCmd.exec(any(PullImageResultCallback.class))).thenReturn(pullCallback);
            when(pullCallback.awaitCompletion()).thenThrow(new InterruptedException("Test"));

            containerService.updateContainer(containerId);

            assertThat(Thread.currentThread().isInterrupted()).isTrue();
            // Clear interrupt status
            boolean isInterrupted = Thread.interrupted();
            assertThat(isInterrupted).isTrue();
        }
    }

    @Nested
    @DisplayName("getContainerLogs")
    class GetContainerLogs {

        @Test
        @DisplayName("should return container logs")
        void shouldReturnContainerLogs() {
            String containerId = "test-container-id";
            String expectedLogs = "Log line 1\nLog line 2\n";

            LogContainerCmd logCmd = mock(LogContainerCmd.class);
            when(dockerClient.logContainerCmd(containerId)).thenReturn(logCmd);
            when(logCmd.withStdOut(true)).thenReturn(logCmd);
            when(logCmd.withStdErr(true)).thenReturn(logCmd);
            when(logCmd.withTail(anyInt())).thenReturn(logCmd);

            // Simulate callback behavior
            doAnswer(
                            invocation -> {
                                ResultCallback.Adapter<Frame> callback = invocation.getArgument(0);
                                Frame frame = mock(Frame.class);
                                when(frame.getPayload()).thenReturn(expectedLogs.getBytes());
                                callback.onNext(frame);
                                callback.onComplete();
                                return callback;
                            })
                    .when(logCmd)
                    .exec(any());

            String result = containerService.getContainerLogs(containerId, 100);

            assertThat(result).isEqualTo(expectedLogs);
            verify(logCmd).withTail(100);
        }

        @Test
        @DisplayName("should return error message on interruption")
        void shouldReturnErrorOnInterruption() {
            String containerId = "test-container-id";

            LogContainerCmd logCmd = mock(LogContainerCmd.class);
            when(dockerClient.logContainerCmd(containerId)).thenReturn(logCmd);
            when(logCmd.withStdOut(true)).thenReturn(logCmd);
            when(logCmd.withStdErr(true)).thenReturn(logCmd);
            when(logCmd.withTail(anyInt())).thenReturn(logCmd);

            doAnswer(
                            invocation -> {
                                ResultCallback.Adapter<Frame> callback = invocation.getArgument(0);
                                Thread.currentThread().interrupt();
                                throw new InterruptedException("Test");
                            })
                    .when(logCmd)
                    .exec(any());

            String result = containerService.getContainerLogs(containerId, 100);

            assertThat(result).isEqualTo("Error fetching logs");
            // Clear interrupt status
            boolean isInterrupted = Thread.interrupted();
            assertThat(isInterrupted).isTrue();
        }
    }

    @Nested
    @DisplayName("streamContainerLogs")
    class StreamContainerLogs {

        @Test
        @DisplayName("should stream container logs")
        void shouldStreamContainerLogs() {
            String containerId = "test-container-id";

            LogContainerCmd logCmd = mock(LogContainerCmd.class);
            when(dockerClient.logContainerCmd(containerId)).thenReturn(logCmd);
            when(logCmd.withStdOut(true)).thenReturn(logCmd);
            when(logCmd.withStdErr(true)).thenReturn(logCmd);
            when(logCmd.withFollowStream(anyBoolean())).thenReturn(logCmd);
            when(logCmd.withTimestamps(true)).thenReturn(logCmd);

            // Use a holder to capture the callback and invoke it after subscription
            final ResultCallback.Adapter<Frame>[] callbackHolder = new ResultCallback.Adapter[1];
            doAnswer(
                            invocation -> {
                                ResultCallback.Adapter<Frame> callback = invocation.getArgument(0);
                                callbackHolder[0] = callback;
                                // Simulate async: spawn thread to send data after small delay
                                new Thread(
                                                () -> {
                                                    try {
                                                        Thread.sleep(50);
                                                        Frame frame1 = mock(Frame.class);
                                                        when(frame1.getPayload())
                                                                .thenReturn("Log 1\n".getBytes());
                                                        Frame frame2 = mock(Frame.class);
                                                        when(frame2.getPayload())
                                                                .thenReturn("Log 2\n".getBytes());
                                                        callback.onNext(frame1);
                                                        callback.onNext(frame2);
                                                        callback.onComplete();
                                                    } catch (InterruptedException e) {
                                                        Thread.currentThread().interrupt();
                                                    }
                                                })
                                        .start();
                                return callback;
                            })
                    .when(logCmd)
                    .exec(any());

            Multi<String> result = containerService.streamContainerLogs(containerId, true);

            AssertSubscriber<String> subscriber =
                    result.subscribe().withSubscriber(AssertSubscriber.create(10));

            subscriber.awaitCompletion(java.time.Duration.ofSeconds(5));
            assertThat(subscriber.getItems()).containsExactly("Log 1\n", "Log 2\n");
            verify(logCmd).withFollowStream(true);
        }

        @Test
        @DisplayName("should handle errors in log stream")
        void shouldHandleErrorsInLogStream() {
            String containerId = "test-container-id";

            LogContainerCmd logCmd = mock(LogContainerCmd.class);
            when(dockerClient.logContainerCmd(containerId)).thenReturn(logCmd);
            when(logCmd.withStdOut(true)).thenReturn(logCmd);
            when(logCmd.withStdErr(true)).thenReturn(logCmd);
            when(logCmd.withFollowStream(anyBoolean())).thenReturn(logCmd);
            when(logCmd.withTimestamps(true)).thenReturn(logCmd);

            RuntimeException testException = new RuntimeException("Docker error");
            doAnswer(
                            invocation -> {
                                ResultCallback.Adapter<Frame> callback = invocation.getArgument(0);
                                callback.onError(testException);
                                return callback;
                            })
                    .when(logCmd)
                    .exec(any());

            Multi<String> result = containerService.streamContainerLogs(containerId, true);

            AssertSubscriber<String> subscriber =
                    result.subscribe().withSubscriber(AssertSubscriber.create(10));

            subscriber.awaitFailure();
            assertThat(subscriber.getFailure()).isEqualTo(testException);
        }

        @Test
        @DisplayName("should return failure multi on exception")
        void shouldReturnFailureOnException() {
            String containerId = "test-container-id";

            when(dockerClient.logContainerCmd(containerId))
                    .thenThrow(new RuntimeException("Connection failed"));

            Multi<String> result = containerService.streamContainerLogs(containerId, true);

            AssertSubscriber<String> subscriber =
                    result.subscribe().withSubscriber(AssertSubscriber.create(10));

            subscriber.awaitFailure();
            assertThat(subscriber.getFailure()).hasMessage("Connection failed");
        }
    }

    private Container createMockContainer(
            String id, String name, String image, String state, String status) {
        Container container = mock(Container.class);
        when(container.getId()).thenReturn(id);
        when(container.getNames()).thenReturn(new String[] {name});
        when(container.getImage()).thenReturn(image);
        when(container.getState()).thenReturn(state);
        when(container.getStatus()).thenReturn(status);
        return container;
    }
}
