/* (C)2026 */
package com.ammann.servicemanager.exception;

/**
 * Runtime exception thrown when a lifecycle operation is attempted on a blacklisted container.
 *
 * <p>Containers may be blacklisted by their identifier, name, or image name via
 * {@link com.ammann.servicemanager.config.ServiceBlacklistConfig}. This exception is
 * mapped to an HTTP 403 Forbidden response by
 * {@link ServiceBlacklistedExceptionMapper}.
 */
public class ServiceBlacklistedException extends RuntimeException {

    private final String containerId;

    /**
     * Constructs a new exception for the given container.
     *
     * @param containerId the identifier of the blacklisted container
     */
    public ServiceBlacklistedException(String containerId) {
        super("Container is blacklisted and cannot be modified: " + containerId);
        this.containerId = containerId;
    }

    /**
     * Returns the identifier of the blacklisted container.
     *
     * @return the container identifier
     */
    public String getContainerId() {
        return containerId;
    }
}
