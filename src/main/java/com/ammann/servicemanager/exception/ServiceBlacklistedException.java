/* (C)2026 */
package com.ammann.servicemanager.exception;

public class ServiceBlacklistedException extends RuntimeException {

    private final String containerId;

    public ServiceBlacklistedException(String containerId) {
        super("Container is blacklisted and cannot be modified: " + containerId);
        this.containerId = containerId;
    }

    public String getContainerId() {
        return containerId;
    }
}
