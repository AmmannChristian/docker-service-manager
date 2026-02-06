package com.ammann.servicemanager.properties;

import io.quarkus.runtime.annotations.RegisterForReflection;

/**
 * Centralised path constants for the REST API.
 *
 * <p>All resource classes reference these constants to ensure consistent URL
 * construction across the application.
 */
@RegisterForReflection
public final class ApiProperties {

    private ApiProperties() {}

    /** Base path for API version 1 endpoints. */
    public static final String BASE_URL_V1 = "/api/v1";

    /** Base path for API version 2 endpoints. */
    public static final String BASE_URL_V2 = "/api/v2";

    /** Path constants for container management endpoints. */
    public static final class Container {
        private Container() {}

        public static final String BASE = "/containers";
    }
}
