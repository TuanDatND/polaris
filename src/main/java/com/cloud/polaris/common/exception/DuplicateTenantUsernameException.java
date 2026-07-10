package com.cloud.polaris.common.exception;

public class DuplicateTenantUsernameException extends RuntimeException {
    public DuplicateTenantUsernameException(String username) {
        super("Tenant username already exists: " + username);
    }
}
