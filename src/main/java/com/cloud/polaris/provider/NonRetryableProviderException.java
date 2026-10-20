package com.cloud.polaris.provider;

public class NonRetryableProviderException extends RuntimeException {

    public NonRetryableProviderException(String message, Throwable cause) {
        super(message, cause);
    }
}
