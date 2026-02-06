/* (C)2026 */
package com.ammann.servicemanager.config;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;
import io.smallrye.config.WithName;
import java.util.Optional;
import java.util.Set;

/**
 * Configuration mapping for the container blacklist.
 *
 * <p>Blacklisted containers are protected from lifecycle operations (stop, restart, update).
 * Entries may refer to a container identifier, a container name, or an image name.
 * The blacklist is sourced from the {@code service.blacklist} configuration property.
 */
@ConfigMapping(prefix = "service")
public interface ServiceBlacklistConfig {

    /**
     * Returns the set of blacklisted identifiers, names, or image references.
     *
     * @return an optional set of blacklisted values, empty if none are configured
     */
    @WithName("blacklist")
    @WithDefault("")
    Optional<Set<String>> blacklist();

    /**
     * Determines whether a container is blacklisted by checking its identifier, name,
     * and image name against the configured blacklist entries.
     *
     * @param containerId   the Docker container identifier
     * @param containerName the container name
     * @param imageName     the image reference (including tag)
     * @return {@code true} if any of the provided values appear in the blacklist
     */
    default boolean isBlacklisted(String containerId, String containerName, String imageName) {
        return blacklist()
                .map(
                        set ->
                                set.contains(containerId)
                                        || set.contains(containerName)
                                        || set.contains(imageName))
                .orElse(false);
    }
}
