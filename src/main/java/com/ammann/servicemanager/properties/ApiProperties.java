package com.ammann.servicemanager.properties;

import io.quarkus.runtime.annotations.RegisterForReflection;

@RegisterForReflection
public final class ApiProperties {

    // Prevent instantiation
    private ApiProperties() {}

    // Base API paths
    public static final String BASE_URL_V1 = "/api/v1";
    public static final String BASE_URL_V2 = "/api/v2";

    /**
     * Container endpoints
     */
    public static final class Container {
        private Container() {}

        public static final String BASE = "/containers";
    }
}
