package com.capsa.bootstrap.api;

public class BootstrapAlreadyClaimedException extends RuntimeException {
    public BootstrapAlreadyClaimedException() {
        super("Bootstrap has already been claimed");
    }
}
