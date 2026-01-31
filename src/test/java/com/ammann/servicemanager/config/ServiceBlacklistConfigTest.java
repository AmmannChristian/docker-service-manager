/* (C)2026 */
package com.ammann.servicemanager.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("ServiceBlacklistConfig")
class ServiceBlacklistConfigTest {

    @Nested
    @DisplayName("isBlacklisted")
    class IsBlacklisted {

        @Test
        @DisplayName("should return false when blacklist is empty")
        void shouldReturnFalseWhenBlacklistIsEmpty() {
            ServiceBlacklistConfig config = createConfig(Optional.empty());

            boolean result = config.isBlacklisted("container-id", "container-name", "nginx:latest");

            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("should return false when blacklist has empty set")
        void shouldReturnFalseWhenBlacklistHasEmptySet() {
            ServiceBlacklistConfig config = createConfig(Optional.of(Set.of()));

            boolean result = config.isBlacklisted("container-id", "container-name", "nginx:latest");

            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("should return true when container ID is blacklisted")
        void shouldReturnTrueWhenContainerIdIsBlacklisted() {
            ServiceBlacklistConfig config =
                    createConfig(Optional.of(Set.of("container-id", "other-container")));

            boolean result = config.isBlacklisted("container-id", "container-name", "nginx:latest");

            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("should return true when container name is blacklisted")
        void shouldReturnTrueWhenContainerNameIsBlacklisted() {
            ServiceBlacklistConfig config =
                    createConfig(Optional.of(Set.of("traefik", "other-container")));

            boolean result = config.isBlacklisted("abc123", "traefik", "traefik:v3.1");

            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("should return true when image name is blacklisted")
        void shouldReturnTrueWhenImageNameIsBlacklisted() {
            ServiceBlacklistConfig config =
                    createConfig(Optional.of(Set.of("nginx:latest", "redis:7")));

            boolean result = config.isBlacklisted("abc123", "my-nginx", "nginx:latest");

            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("should return false when nothing matches")
        void shouldReturnFalseWhenNothingMatches() {
            ServiceBlacklistConfig config = createConfig(Optional.of(Set.of("traefik", "redis:7")));

            boolean result =
                    config.isBlacklisted("container-id", "my-app", "ghcr.io/my/app:latest");

            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("should match any of id, name, or image")
        void shouldMatchAnyOfIdNameOrImage() {
            ServiceBlacklistConfig config = createConfig(Optional.of(Set.of("protected-service")));

            // Match by ID
            assertThat(config.isBlacklisted("protected-service", "other", "nginx:latest")).isTrue();
            // Match by name
            assertThat(config.isBlacklisted("id", "protected-service", "nginx:latest")).isTrue();
            // Match by image
            assertThat(config.isBlacklisted("id", "name", "protected-service")).isTrue();
            // No match
            assertThat(config.isBlacklisted("id", "name", "image")).isFalse();
        }
    }

    private ServiceBlacklistConfig createConfig(Optional<Set<String>> blacklist) {
        return new ServiceBlacklistConfig() {
            @Override
            public Optional<Set<String>> blacklist() {
                return blacklist;
            }
        };
    }
}
