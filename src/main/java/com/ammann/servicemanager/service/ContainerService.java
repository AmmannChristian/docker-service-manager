/* (C)2026 */
package com.ammann.servicemanager.service;

import com.ammann.servicemanager.config.ServiceBlacklistConfig;
import com.ammann.servicemanager.dto.ContainerInfoDTO;
import com.ammann.servicemanager.exception.ServiceBlacklistedException;
import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.async.ResultCallback;
import com.github.dockerjava.api.command.InspectContainerResponse;
import com.github.dockerjava.api.command.PullImageResultCallback;
import com.github.dockerjava.api.model.Container;
import com.github.dockerjava.api.model.Frame;
import com.github.dockerjava.api.model.Image;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.operators.multi.processors.BroadcastProcessor;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.io.IOException;
import java.util.List;
import org.jboss.logging.Logger;

@ApplicationScoped
public class ContainerService {

    @Inject DockerClient dockerClient;

    @Inject Logger logger;

    @Inject ServiceBlacklistConfig blacklistConfig;

    public List<ContainerInfoDTO> listContainers(boolean showAll) {
        List<Container> containers = dockerClient.listContainersCmd().withShowAll(showAll).exec();

        return containers.stream().map(this::mapToContainerInfo).toList();
    }

    public void restartContainer(String containerId) {
        checkBlacklist(containerId);
        logger.infof("Restarting container: %s", containerId);
        dockerClient.restartContainerCmd(containerId).withTimeout(10).exec();
    }

    public void stopContainer(String containerId) {
        checkBlacklist(containerId);
        logger.infof("Stopping container: %s", containerId);
        dockerClient.stopContainerCmd(containerId).withTimeout(10).exec();
    }

    public void startContainer(String containerId) {
        logger.infof("Starting container: %s", containerId);
        dockerClient.startContainerCmd(containerId).exec();
    }

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

    public void updateContainer(String containerId) {
        checkBlacklist(containerId);
        InspectContainerResponse containerInfo =
                dockerClient.inspectContainerCmd(containerId).exec();
        String imageName = containerInfo.getConfig().getImage();

        logger.infof("Updating container %s with image %s", containerId, imageName);

        try {
            dockerClient
                    .pullImageCmd(imageName)
                    .exec(new PullImageResultCallback())
                    .awaitCompletion();

            stopContainer(containerId);

            dockerClient.removeContainerCmd(containerId).exec();

            String newContainerId =
                    dockerClient
                            .createContainerCmd(imageName)
                            .withName(containerInfo.getName().substring(1))
                            .withEnv(containerInfo.getConfig().getEnv())
                            .exec()
                            .getId();

            startContainer(newContainerId);
            logger.infof(
                    "Container %s successfully updated and started as %s",
                    containerId, newContainerId);

        } catch (InterruptedException e) {
            logger.errorf(e, "Error updating container: %s", containerId);
            Thread.currentThread().interrupt();
        }
    }

    private ContainerInfoDTO mapToContainerInfo(Container container) {
        return new ContainerInfoDTO(
                container.getId(),
                container.getNames()[0],
                container.getImage(),
                container.getState(),
                container.getStatus());
    }

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
     * Holt historische Logs (nicht live)
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
