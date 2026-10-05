package com.capsa.bootstrap.api;

public class BootstrapTokenInvalidException extends RuntimeException {
    public BootstrapTokenInvalidException() {
        super("Invalid or missing bootstrap token");
    }
}
