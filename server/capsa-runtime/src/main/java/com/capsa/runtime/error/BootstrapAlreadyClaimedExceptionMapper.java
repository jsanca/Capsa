package com.capsa.runtime.error;

import com.capsa.bootstrap.api.BootstrapAlreadyClaimedException;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

@Provider
public class BootstrapAlreadyClaimedExceptionMapper implements ExceptionMapper<BootstrapAlreadyClaimedException> {

    @Override
    public Response toResponse(BootstrapAlreadyClaimedException e) {
        return Response.status(409)
            .entity(new ErrorResponse("CAPSA_BOOTSTRAP_ALREADY_CLAIMED", e.getMessage()))
            .build();
    }
}
