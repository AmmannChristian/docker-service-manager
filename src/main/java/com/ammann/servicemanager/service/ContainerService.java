/* (C)2026 */
package com.ammann.servicemanager.service;

import com.ammann.servicemanager.config.ServiceBlacklistConfig;
import com.ammann.servicemanager.dto.ContainerInfoDTO;
import com.ammann.servicemanager.exception.ServiceBlacklistedException;
import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.async.ResultCallback;
import com.github.dockerjava.api.command.CreateContainerCmd;
import com.github.dockerjava.api.command.InspectContainerResponse;
import com.github.dockerjava.api.command.PullImageResultCallback;
import com.github.dockerjava.api.exception.NotModifiedException;
import com.github.dockerjava.api.model.Container;
import com.github.dockerjava.api.model.ContainerNetwork;
import com.github.dockerjava.api.model.Frame;
import com.github.dockerjava.api.model.HostConfig;
import com.github.dockerjava.api.model.Image;
import com.github.dockerjava.api.model.NetworkSettings;
import com.github.dockerjava.api.model.Volume;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.operators.multi.processors.BroadcastProcessor;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.jboss.logging.Logger;

/**
 * Core service for Docker container lifecycle management.
 *
 * <p>Provides operations to list, start, stop, restart, and update containers as well
 * as to retrieve and stream container logs. All mutating operations (stop, restart,
 * update) enforce the configured container blacklist before proceeding.
 *
 * <p>The update operation performs a full container replacement: it pulls the latest
 * image, removes the existing container, recreates it with the original configuration
 * (environment variables, labels, ports, volumes, networks, health check), and starts
 * the new container.
 */
@ApplicationScoped
public class ContainerService {

    @Inject DockerClient dockerClient;

    @Inject Logger logger;

    @Inject ServiceBlacklistConfig blacklistConfig;

    /**
     * Lists Docker containers, optionally including stopped containers.
     *
     * @param showAll if {@code true}, includes containers in all states; otherwise only running
     * @return a list of container summary DTOs
     */
    public List<ContainerInfoDTO> listContainers(boolean showAll) {
        List<Container> containers = dockerClient.listContainersCmd().withShowAll(showAll).exec();

        return containers.stream().map(this::mapToContainerInfo).toList();
    }

    /**
     * Restarts a container after verifying it is not blacklisted.
     *
     * @param containerId the Docker container identifier
     * @throws ServiceBlacklistedException if the container is blacklisted
     */
    public void restartContainer(String containerId) {
        checkBlacklist(containerId);
        logger.infof("Restarting container: %s", containerId);
        dockerClient.restartContainerCmd(containerId).withTimeout(10).exec();
    }

    /**
     * Stops a container after verifying it is not blacklisted.
     *
     * @param containerId the Docker container identifier
     * @throws ServiceBlacklistedException if the container is blacklisted
     */
    public void stopContainer(String containerId) {
        checkBlacklist(containerId);
        logger.infof("Stopping container: %s", containerId);
        dockerClient.stopContainerCmd(containerId).withTimeout(10).exec();
    }

    /**
     * Starts a stopped container.
     *
     * @param containerId the Docker container identifier
     */
    public void startContainer(String containerId) {
        logger.infof("Starting container: %s", containerId);
        dockerClient.startContainerCmd(containerId).exec();
    }

    /**
     * Checks whether a newer version of the given image is available in the remote registry.
     *
     * <p>Compares the local image identifier with the identifier obtained after pulling
     * the latest tag. Returns {@code true} if the identifiers differ, indicating an
     * update is available.
     *
     * @param imageName the image reference to check (including tag)
     * @return {@code true} if a newer image is available in the registry
     */
    public boolean checkForUpdate(String imageName) {
        try {
            List<Image> localImages =
                    dockerClient.listImagesCmd().withImageNameFilter(imageName).exec();

            if (localImages.isEmpty()) {
                return false;
            }

            String localImageId = localImages.getFirst().getId();

            dockerClient
                    .pullImageCmd(imageName)
                    .exec(new PullImageResultCallback())
                    .awaitCompletion();

            List<Image> updatedImages =
                    dockerClient.listImagesCmd().withImageNameFilter(imageName).exec();

            if (!updatedImages.isEmpty()) {
                String newImageId = updatedImages.getFirst().getId();
                return !localImageId.equals(newImageId);
            }

            return false;
        } catch (InterruptedException e) {
            logger.errorf(e, "Error checking for updates: %s", imageName);
            Thread.currentThread().interrupt();
            return false;
        }
    }

    /**
     * Performs a full container update by pulling the latest image and recreating the container.
     *
     * <p>The update sequence is: pull latest image, stop the existing container, capture
     * its network connections, remove it, create a new container preserving the original
     * configuration (environment, labels, ports, volumes, entrypoint, health check, host
     * config), reconnect additional networks, and start the new container.
     *
     * @param containerId the Docker container identifier
     * @throws ServiceBlacklistedException if the container is blacklisted
     * @throws RuntimeException            if the update is interrupted or fails
     */
    public void updateContainer(String containerId) {
        checkBlacklist(containerId);
        InspectContainerResponse containerInfo =
                dockerClient.inspectContainerCmd(containerId).exec();
        String imageName = containerInfo.getConfig().getImage();
        String containerName = containerInfo.getName().substring(1); // Remove leading "/"

        logger.infof(
                "Updating container %s (%s) with image %s", containerName, containerId, imageName);

        try {
            // Pull latest image
            logger.infof("Pulling latest image: %s", imageName);
            dockerClient
                    .pullImageCmd(imageName)
                    .exec(new PullImageResultCallback())
                    .awaitCompletion();

            // Stop container (ignore if already stopped)
            try {
                dockerClient.stopContainerCmd(containerId).withTimeout(30).exec();
                logger.infof("Container %s stopped", containerName);
            } catch (NotModifiedException e) {
                logger.infof("Container %s was already stopped", containerName);
            }

            // Capture network connections before removal
            List<String> connectedNetworks = new ArrayList<>();
            NetworkSettings networkSettings = containerInfo.getNetworkSettings();
            if (networkSettings != null && networkSettings.getNetworks() != null) {
                connectedNetworks.addAll(networkSettings.getNetworks().keySet());
            }

            // Remove old container
            dockerClient.removeContainerCmd(containerId).withForce(true).exec();
            logger.infof("Container %s removed", containerName);

            // Create new container with preserved configuration
            CreateContainerCmd createCmd =
                    dockerClient.createContainerCmd(imageName).withName(containerName);

            // Preserve container config
            if (containerInfo.getConfig() != null) {
                var config = containerInfo.getConfig();
                if (config.getEnv() != null) {
                    createCmd.withEnv(config.getEnv());
                }
                if (config.getLabels() != null) {
                    createCmd.withLabels(config.getLabels());
                }
                if (config.getExposedPorts() != null) {
                    createCmd.withExposedPorts(config.getExposedPorts());
                }
                if (config.getCmd() != null) {
                    createCmd.withCmd(config.getCmd());
                }
                if (config.getEntrypoint() != null) {
                    createCmd.withEntrypoint(config.getEntrypoint());
                }
                if (config.getWorkingDir() != null) {
                    createCmd.withWorkingDir(config.getWorkingDir());
                }
                if (config.getUser() != null && !config.getUser().isEmpty()) {
                    createCmd.withUser(config.getUser());
                }
                if (config.getVolumes() != null && !config.getVolumes().isEmpty()) {
                    createCmd.withVolumes(config.getVolumes().keySet().toArray(new Volume[0]));
                }
                if (config.getHealthcheck() != null) {
                    createCmd.withHealthcheck(config.getHealthcheck());
                }
            }

            // Preserve host config (volumes, ports, network mode, restart policy, etc.)
            if (containerInfo.getHostConfig() != null) {
                HostConfig hostConfig = containerInfo.getHostConfig();
                createCmd.withHostConfig(hostConfig);
            }

            String newContainerId = createCmd.exec().getId();
            logger.infof("Container %s created with new ID: %s", containerName, newContainerId);

            // Connect to additional networks (HostConfig only handles the primary network)
            for (String networkName : connectedNetworks) {
                // Skip the default bridge network if using a custom network mode
                if ("bridge".equals(networkName) && connectedNetworks.size() > 1) {
                    continue;
                }
                try {
                    // Get network aliases from original container
                    ContainerNetwork originalNetwork =
                            networkSettings.getNetworks().get(networkName);
                    var connectCmd =
                            dockerClient
                                    .connectToNetworkCmd()
                                    .withContainerId(newContainerId)
                                    .withNetworkId(networkName);

                    if (originalNetwork != null && originalNetwork.getAliases() != null) {
                        connectCmd.withContainerNetwork(
                                new ContainerNetwork().withAliases(originalNetwork.getAliases()));
                    }
                    connectCmd.exec();
                    logger.debugf("Connected container to network: %s", networkName);
                } catch (Exception e) {
                    logger.debugf(
                            "Network %s may already be connected or is primary: %s",
                            networkName, e.getMessage());
                }
            }

            // Start the new container
            startContainer(newContainerId);
            logger.infof("Container %s successfully updated and started", containerName);

        } catch (InterruptedException e) {
            logger.errorf(e, "Error updating container: %s", containerName);
            Thread.currentThread().interrupt();
            throw new RuntimeException("Container update interrupted", e);
        } catch (Exception e) {
            logger.errorf(e, "Error updating container: %s", containerName);
            throw new RuntimeException("Failed to update container: " + containerName, e);
        }
    }

    /** Maps a Docker API {@link Container} model to a {@link ContainerInfoDTO}. */
    private ContainerInfoDTO mapToContainerInfo(Container container) {
        return new ContainerInfoDTO(
                container.getId(),
                container.getNames()[0],
                container.getImage(),
                container.getState(),
                container.getStatus());
    }

    /**
     * Inspects the container and throws if it matches a blacklist entry.
     *
     * @param containerId the Docker container identifier
     * @throws ServiceBlacklistedException if the container is blacklisted
     */
    private void checkBlacklist(String containerId) {
        InspectContainerResponse info = dockerClient.inspectContainerCmd(containerId).exec();
        String name = info.getName();
        if (name.startsWith("/")) {
            name = name.substring(1);
        }
        String image = info.getConfig().getImage();

        if (blacklistConfig.isBlacklisted(containerId, name, image)) {
            throw new ServiceBlacklistedException(containerId);
        }
    }

    /**
     * Streams container log output as a reactive {@link Multi}.
     *
     * <p>Attaches to the container's stdout and stderr with timestamps enabled.
     * The underlying Docker log callback is closed automatically when the stream
     * terminates.
     *
     * @param containerId the Docker container identifier
     * @param follow      if {@code true}, the stream follows new output (similar to {@code tail -f})
     * @return a reactive stream of log lines
     */
    public Multi<String> streamContainerLogs(String containerId, boolean follow) {
        BroadcastProcessor<String> processor = BroadcastProcessor.create();

        ResultCallback.Adapter<Frame> callback =
                new ResultCallback.Adapter<>() {
                    @Override
                    public void onNext(Frame frame) {
                        String log = new String(frame.getPayload());
                        if (!log.isEmpty()) {
                            processor.onNext(log);
                        }
                    }

                    @Override
                    public void onError(Throwable throwable) {
                        logger.errorf(
                                throwable, "Error streaming logs for container %s", containerId);
                        processor.onError(throwable);
                    }

                    @Override
                    public void onComplete() {
                        logger.infof("Log stream completed for container %s", containerId);
                        processor.onComplete();
                    }
                };

        try {
            dockerClient
                    .logContainerCmd(containerId)
                    .withStdOut(true)
                    .withStdErr(true)
                    .withFollowStream(follow)
                    .withTimestamps(true)
                    .exec(callback);

            return processor
                    .onTermination()
                    .invoke(
                            () -> {
                                try {
                                    callback.close();
                                } catch (IOException e) {
                                    logger.errorf(
                                            e,
                                            "Error closing log stream for container %s",
                                            containerId);
                                }
                            });

        } catch (Exception e) {
            logger.errorf(e, "Error starting log stream for container %s", containerId);
            return Multi.createFrom().failure(e);
        }
    }

    /**
     * Retrieves historical container logs (non-streaming).
     *
     * @param containerId the Docker container identifier
     * @param tailLines   the maximum number of trailing log lines to return
     * @return the concatenated log output, or an error message if retrieval is interrupted
     */
    public String getContainerLogs(String containerId, int tailLines) {
        try {
            StringBuilder logs = new StringBuilder();

            ResultCallback.Adapter<Frame> cb =
                    new ResultCallback.Adapter<>() {
                        @Override
                        public void onNext(Frame frame) {
                            logs.append(new String(frame.getPayload()));
                        }

                        @Override
                        public void onError(Throwable throwable) {
                            logger.errorf(
                                    throwable, "Error fetching logs for container %s", containerId);
                            super.onError(throwable);
                        }
                    };

            dockerClient
                    .logContainerCmd(containerId)
                    .withStdOut(true)
                    .withStdErr(true)
                    .withTail(tailLines)
                    .exec(cb);

            cb.awaitCompletion();

            return logs.toString();

        } catch (InterruptedException e) {
            logger.errorf(e, "Error fetching logs for container %s", containerId);
            Thread.currentThread().interrupt();
            return "Error fetching logs";
        }
    }
}
