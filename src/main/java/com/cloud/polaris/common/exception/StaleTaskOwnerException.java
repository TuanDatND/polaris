package com.cloud.polaris.common.exception;

public class StaleTaskOwnerException extends RuntimeException {
    public StaleTaskOwnerException(String message) {
        super(message);
    }
}
