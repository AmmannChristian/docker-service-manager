/* (C)2026 */
package com.ammann.servicemanager.config;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;
import io.smallrye.config.WithName;
import java.util.Optional;
import java.util.Set;

@ConfigMapping(prefix = "service")
public interface ServiceBlacklistConfig {

    @WithName("blacklist")
    @WithDefault("")
    Optional<Set<String>> blacklist();

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
