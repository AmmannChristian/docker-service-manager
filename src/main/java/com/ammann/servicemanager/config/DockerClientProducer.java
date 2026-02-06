package com.ammann.servicemanager.config;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.core.DefaultDockerClientConfig;
import com.github.dockerjava.core.DockerClientConfig;
import com.github.dockerjava.core.DockerClientImpl;
import com.github.dockerjava.httpclient5.ApacheDockerHttpClient;
import com.github.dockerjava.transport.DockerHttpClient;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import java.time.Duration;
import java.util.Optional;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/**
 * CDI producer for the Docker API client.
 *
 * <p>Creates an application-scoped {@link DockerClient} configured with an Apache HTTP
 * transport. The Docker host URI defaults to the system default (typically the local Unix
 * socket) but can be overridden via the {@code docker.host} configuration property.
 */
@ApplicationScoped
public class DockerClientProducer {

    @ConfigProperty(name = "docker.host")
    Optional<String> dockerHostOverride;

    /**
     * Produces an application-scoped {@link DockerClient} instance.
     *
     * @return a fully configured Docker client ready for API calls
     */
    @Produces
    @ApplicationScoped
    public DockerClient dockerClient() {
        DefaultDockerClientConfig.Builder configBuilder =
                DefaultDockerClientConfig.createDefaultConfigBuilder();
        dockerHostOverride.ifPresent(configBuilder::withDockerHost);
        DockerClientConfig config = configBuilder.build();

        DockerHttpClient httpClient =
                new ApacheDockerHttpClient.Builder()
                        .dockerHost(config.getDockerHost())
                        .sslConfig(config.getSSLConfig())
                        .maxConnections(100)
                        .connectionTimeout(Duration.ofSeconds(30))
                        .responseTimeout(Duration.ofSeconds(45))
                        .build();

        return DockerClientImpl.getInstance(config, httpClient);
    }
}
