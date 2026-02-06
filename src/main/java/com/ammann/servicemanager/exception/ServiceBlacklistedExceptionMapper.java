/* (C)2026 */
package com.ammann.servicemanager.exception;

import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import java.util.Map;

/**
 * JAX-RS exception mapper that converts {@link ServiceBlacklistedException} into an
 * HTTP 403 Forbidden response with a JSON error body containing the error label,
 * message, and the offending container identifier.
 */
@Provider
public class ServiceBlacklistedExceptionMapper
        implements ExceptionMapper<ServiceBlacklistedException> {

    @Override
    public Response toResponse(ServiceBlacklistedException exception) {
        return Response.status(Response.Status.FORBIDDEN)
                .entity(
                        Map.of(
                                "error", "Forbidden",
                                "message", exception.getMessage(),
                                "containerId", exception.getContainerId()))
                .build();
    }
}
