/* (C)2026 */
package com.ammann.servicemanager.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.github.dockerjava.api.DockerClient;
import java.lang.reflect.Field;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("DockerClientProducer")
class DockerClientProducerTest {

    @Test
    @DisplayName("should create DockerClient with default configuration")
    void shouldCreateDockerClientWithDefaultConfig() throws Exception {
        DockerClientProducer producer = new DockerClientProducer();
        setDockerHostOverride(producer, Optional.empty());

        DockerClient client = producer.dockerClient();

        assertThat(client).isNotNull();
    }

    @Test
    @DisplayName("should create DockerClient with custom docker host")
    void shouldCreateDockerClientWithCustomHost() throws Exception {
        DockerClientProducer producer = new DockerClientProducer();
        setDockerHostOverride(producer, Optional.of("tcp://localhost:2375"));

        DockerClient client = producer.dockerClient();

        assertThat(client).isNotNull();
    }

    @Test
    @DisplayName("should create DockerClient with unix socket")
    void shouldCreateDockerClientWithUnixSocket() throws Exception {
        DockerClientProducer producer = new DockerClientProducer();
        setDockerHostOverride(producer, Optional.of("unix:///var/run/docker.sock"));

        DockerClient client = producer.dockerClient();

        assertThat(client).isNotNull();
    }

    private void setDockerHostOverride(DockerClientProducer producer, Optional<String> value)
            throws Exception {
        Field field = DockerClientProducer.class.getDeclaredField("dockerHostOverride");
        field.setAccessible(true);
        field.set(producer, value);
    }
}
