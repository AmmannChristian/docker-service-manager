package com.ammann.servicemanager.service;

import com.ammann.servicemanager.dto.ContainerInfoDTO;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.List;
import org.jboss.logging.Logger;

/**
 * Scheduled background service that monitors Docker containers.
 *
 * <p>Performs two periodic tasks:
 * <ul>
 *   <li>Checks for available image updates for remote containers (every 5 minutes).</li>
 *   <li>Verifies that all containers are in the "running" state (every 30 seconds).</li>
 * </ul>
 *
 * <p>Local images (those without a registry prefix) are excluded from update checks
 * because they cannot be pulled from a remote registry.
 */
@ApplicationScoped
public class ContainerMonitorService {

    @Inject ContainerService containerService;

    @Inject Logger logger;

    /**
     * Iterates over all running containers and checks whether a newer image version
     * is available in the remote registry. Local images are skipped.
     */
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
     * Determines whether a Docker image originates from a remote registry.
     *
     * <p>An image is considered remote if its first path segment contains a dot
     * (e.g. {@code ghcr.io}, {@code docker.io}) or a colon (e.g. {@code localhost:5000}).
     * Single-segment images without a registry prefix are treated as local builds.
     *
     * @param image the image reference (name and optional tag)
     * @return {@code true} if the image appears to originate from a remote registry
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

    /**
     * Logs a warning for each container that is not in the "running" state.
     */
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
