package com.ammann.servicemanager.health;

import org.eclipse.microprofile.health.HealthCheck;
import org.eclipse.microprofile.health.HealthCheckResponse;
import org.eclipse.microprofile.health.Liveness;

/**
 * MicroProfile Health liveness probe for the service manager application.
 *
 * <p>Always reports the application as alive. Container-level health monitoring
 * is performed separately by {@link com.ammann.servicemanager.service.ContainerMonitorService}.
 */
@Liveness
public class LivenessCheck implements HealthCheck {

    @Override
    public HealthCheckResponse call() {
        return HealthCheckResponse.up("alive");
    }
}
