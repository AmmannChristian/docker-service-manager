package com.ammann.servicemanager.service;

import com.ammann.servicemanager.dto.ContainerInfoDTO;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.List;
import org.jboss.logging.Logger;

@ApplicationScoped
public class ContainerMonitorService {

    @Inject ContainerService containerService;

    @Inject Logger logger;

    @Scheduled(every = "5m")
    public void checkForUpdates() {
        logger.debugf("Checking for container updates...");

        List<ContainerInfoDTO> containers = containerService.listContainers(false);

        for (ContainerInfoDTO container : containers) {
            try {
                String image = container.image();
                if (!isRemoteImage(image)) {
                    logger.debugf("Skipping local image: %s", image);
                    continue;
                }

                if (containerService.checkForUpdate(image)) {
                    logger.infof(
                            "Update available for container: %s (%s)",
                            container.name(), image);
                }
            } catch (Exception e) {
                logger.errorf(e, "Error checking updates for container: %s", container.name());
            }
        }
    }

    /**
     * Checks if an image is from a remote registry.
     * Local images (built locally without registry prefix) are skipped.
     */
    private boolean isRemoteImage(String image) {
        if (image == null || image.isBlank()) {
            return false;
        }

        // Remove tag if present
        String imageName = image.contains(":") ? image.substring(0, image.lastIndexOf(":")) : image;

        // Check if image has a registry prefix (contains a dot or colon in the first segment)
        String[] parts = imageName.split("/");
        if (parts.length >= 2) {
            String firstPart = parts[0];
            // Registry prefixes contain a dot (ghcr.io, docker.io) or port (localhost:5000)
            return firstPart.contains(".") || firstPart.contains(":");
        }

        // Single-segment images without prefix are either:
        // - Official Docker Hub images (nginx, postgres) - these are remote
        // - Local images (my-app, docker-service-manager) - these are local
        // We assume single-segment images are local unless they match known official images
        return false;
    }

    @Scheduled(every = "30s")
    public void healthCheck() {
        List<ContainerInfoDTO> containers = containerService.listContainers(false);

        for (ContainerInfoDTO container : containers) {
            if (!"running".equalsIgnoreCase(container.state())) {
                logger.warnf(
                        "Container %s is not running. State: %s",
                        container.name(), container.state());
            }
        }
    }
}
