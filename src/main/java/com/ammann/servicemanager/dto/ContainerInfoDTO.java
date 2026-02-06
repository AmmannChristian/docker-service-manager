package com.ammann.servicemanager.dto;

/**
 * Data transfer object representing summary information about a Docker container.
 *
 * @param id     the Docker container identifier (short or full SHA-256 hex string)
 * @param name   the container name as assigned by Docker (may include a leading slash)
 * @param image  the image reference used to create the container, including tag
 * @param state  the current container state (e.g. "running", "exited", "paused")
 * @param status a human-readable status string (e.g. "Up 5 hours")
 */
public record ContainerInfoDTO(String id, String name, String image, String state, String status) {}
