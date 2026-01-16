package com.ammann.servicemanager.resource;

import com.ammann.servicemanager.dto.ContainerInfoDTO;
import com.ammann.servicemanager.properties.ApiProperties;
import com.ammann.servicemanager.service.ContainerService;
import io.smallrye.mutiny.Multi;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.List;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.enums.SchemaType;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponses;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import org.jboss.logging.Logger;
import org.jboss.resteasy.reactive.RestPath;
import org.jboss.resteasy.reactive.RestQuery;
import org.jboss.resteasy.reactive.RestStreamElementType;

/**
 * REST Resource for Docker container management operations.
 *
 * <p>Provides endpoints for listing, starting, stopping, restarting,
 * updating containers and streaming logs.</p>
 */
@Path(ApiProperties.BASE_URL_V1 + ApiProperties.Container.BASE)
@Tag(name = "Container Management", description = "Docker container lifecycle and log management")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@RolesAllowed("ADMIN_ROLE")
public class ContainerResource {

    private static final String CONTAINER_ID_PATTERN = "^[a-fA-F0-9]{12,64}$";
    private static final String CONTAINER_ID_DESCRIPTION =
            "Docker container ID (12-64 hex characters)";

    @Inject Logger logger;

    @Inject ContainerService containerService;

    @GET
    @Operation(summary = "List containers", description = "Returns a list of all Docker containers")
    @APIResponses({
        @APIResponse(
                responseCode = "200",
                description = "List of containers",
                content =
                        @Content(
                                mediaType = MediaType.APPLICATION_JSON,
                                schema =
                                        @Schema(
                                                type = SchemaType.ARRAY,
                                                implementation = ContainerInfoDTO.class))),
        @APIResponse(responseCode = "401", description = "Unauthorized"),
        @APIResponse(responseCode = "403", description = "Forbidden - requires ADMIN_ROLE"),
        @APIResponse(responseCode = "500", description = "Docker daemon error")
    })
    public List<ContainerInfoDTO> listContainers(
            @Parameter(description = "Include stopped containers")
                    @RestQuery("all")
                    @DefaultValue("false")
                    boolean showAll) {

        logger.debugf("Listing containers (showAll=%s)", showAll);
        return containerService.listContainers(showAll);
    }

    @POST
    @Path("/{id}/restart")
    @Operation(
            summary = "Restart container",
            description = "Restarts a running or stopped container")
    @APIResponses({
        @APIResponse(responseCode = "204", description = "Container restarted successfully"),
        @APIResponse(responseCode = "400", description = "Invalid container ID format"),
        @APIResponse(responseCode = "401", description = "Unauthorized"),
        @APIResponse(responseCode = "403", description = "Forbidden - requires ADMIN_ROLE"),
        @APIResponse(responseCode = "404", description = "Container not found"),
        @APIResponse(responseCode = "500", description = "Docker daemon error")
    })
    public Response restartContainer(
            @Parameter(description = CONTAINER_ID_DESCRIPTION, required = true)
                    @RestPath("id")
                    @NotBlank
                    @Pattern(regexp = CONTAINER_ID_PATTERN, message = "Invalid container ID format")
                    String containerId) {

        logger.infof("Restarting container: %s", containerId);
        containerService.restartContainer(containerId);
        return Response.noContent().build();
    }

    @POST
    @Path("/{id}/start")
    @Operation(summary = "Start container", description = "Starts a stopped container")
    @APIResponses({
        @APIResponse(responseCode = "204", description = "Container started successfully"),
        @APIResponse(responseCode = "400", description = "Invalid container ID format"),
        @APIResponse(responseCode = "401", description = "Unauthorized"),
        @APIResponse(responseCode = "403", description = "Forbidden - requires ADMIN_ROLE"),
        @APIResponse(responseCode = "404", description = "Container not found"),
        @APIResponse(responseCode = "500", description = "Docker daemon error")
    })
    public Response startContainer(
            @Parameter(description = CONTAINER_ID_DESCRIPTION, required = true)
                    @RestPath("id")
                    @NotBlank
                    @Pattern(regexp = CONTAINER_ID_PATTERN, message = "Invalid container ID format")
                    String containerId) {

        logger.infof("Starting container: %s", containerId);
        containerService.startContainer(containerId);
        return Response.noContent().build();
    }

    @POST
    @Path("/{id}/stop")
    @Operation(summary = "Stop container", description = "Stops a running container")
    @APIResponses({
        @APIResponse(responseCode = "204", description = "Container stopped successfully"),
        @APIResponse(responseCode = "400", description = "Invalid container ID format"),
        @APIResponse(responseCode = "401", description = "Unauthorized"),
        @APIResponse(responseCode = "403", description = "Forbidden - requires ADMIN_ROLE"),
        @APIResponse(responseCode = "404", description = "Container not found"),
        @APIResponse(responseCode = "500", description = "Docker daemon error")
    })
    public Response stopContainer(
            @Parameter(description = CONTAINER_ID_DESCRIPTION, required = true)
                    @RestPath("id")
                    @NotBlank
                    @Pattern(regexp = CONTAINER_ID_PATTERN, message = "Invalid container ID format")
                    String containerId) {

        logger.infof("Stopping container: %s", containerId);
        containerService.stopContainer(containerId);
        return Response.noContent().build();
    }

    @POST
    @Path("/{id}/update")
    @Operation(
            summary = "Update container",
            description = "Pulls the latest image and recreates the container with updated image")
    @APIResponses({
        @APIResponse(responseCode = "204", description = "Container updated successfully"),
        @APIResponse(responseCode = "400", description = "Invalid container ID format"),
        @APIResponse(responseCode = "401", description = "Unauthorized"),
        @APIResponse(responseCode = "403", description = "Forbidden - requires ADMIN_ROLE"),
        @APIResponse(responseCode = "404", description = "Container not found"),
        @APIResponse(responseCode = "500", description = "Docker daemon error or image pull failed")
    })
    public Response updateContainer(
            @Parameter(description = CONTAINER_ID_DESCRIPTION, required = true)
                    @RestPath("id")
                    @NotBlank
                    @Pattern(regexp = CONTAINER_ID_PATTERN, message = "Invalid container ID format")
                    String containerId) {

        logger.infof("Updating container: %s", containerId);
        containerService.updateContainer(containerId);
        return Response.noContent().build();
    }

    @GET
    @Path("/{id}/logs")
    @Produces(MediaType.TEXT_PLAIN)
    @Operation(
            summary = "Get container logs",
            description = "Returns the last N lines of container logs")
    @APIResponses({
        @APIResponse(
                responseCode = "200",
                description = "Container logs",
                content = @Content(mediaType = MediaType.TEXT_PLAIN)),
        @APIResponse(
                responseCode = "400",
                description = "Invalid container ID format or tail parameter"),
        @APIResponse(responseCode = "401", description = "Unauthorized"),
        @APIResponse(responseCode = "403", description = "Forbidden - requires ADMIN_ROLE"),
        @APIResponse(responseCode = "404", description = "Container not found"),
        @APIResponse(responseCode = "500", description = "Docker daemon error")
    })
    public String getContainerLogs(
            @Parameter(description = CONTAINER_ID_DESCRIPTION, required = true)
                    @RestPath("id")
                    @NotBlank
                    @Pattern(regexp = CONTAINER_ID_PATTERN, message = "Invalid container ID format")
                    String containerId,
            @Parameter(description = "Number of log lines to return (1-10000)")
                    @RestQuery("tail")
                    @DefaultValue("100")
                    @Min(value = 1, message = "Tail must be at least 1")
                    @Max(value = 10000, message = "Tail must not exceed 10000")
                    int tail) {

        logger.debugf("Fetching logs for container %s (tail=%d)", containerId, tail);
        return containerService.getContainerLogs(containerId, tail);
    }

    @GET
    @Path("/{id}/logs/stream")
    @Produces(MediaType.SERVER_SENT_EVENTS)
    @RestStreamElementType(MediaType.TEXT_PLAIN)
    @Operation(
            summary = "Stream container logs",
            description = "Streams container logs via Server-Sent Events")
    @APIResponses({
        @APIResponse(
                responseCode = "200",
                description = "Log stream started",
                content = @Content(mediaType = MediaType.SERVER_SENT_EVENTS)),
        @APIResponse(responseCode = "400", description = "Invalid container ID format"),
        @APIResponse(responseCode = "401", description = "Unauthorized"),
        @APIResponse(responseCode = "403", description = "Forbidden - requires ADMIN_ROLE"),
        @APIResponse(responseCode = "404", description = "Container not found"),
        @APIResponse(responseCode = "500", description = "Docker daemon error")
    })
    public Multi<String> streamContainerLogs(
            @Parameter(description = CONTAINER_ID_DESCRIPTION, required = true)
                    @RestPath("id")
                    @NotBlank
                    @Pattern(regexp = CONTAINER_ID_PATTERN, message = "Invalid container ID format")
                    String containerId,
            @Parameter(description = "Follow log output (like tail -f)")
                    @RestQuery("follow")
                    @DefaultValue("true")
                    boolean follow) {

        logger.infof("Starting log stream for container %s (follow=%s)", containerId, follow);
        return containerService.streamContainerLogs(containerId, follow);
    }
}
