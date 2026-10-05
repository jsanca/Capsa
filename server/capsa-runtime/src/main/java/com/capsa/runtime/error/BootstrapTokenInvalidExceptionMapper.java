package com.capsa.runtime.error;

import com.capsa.bootstrap.api.BootstrapTokenInvalidException;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

@Provider
public class BootstrapTokenInvalidExceptionMapper implements ExceptionMapper<BootstrapTokenInvalidException> {

    @Override
    public Response toResponse(BootstrapTokenInvalidException e) {
        return Response.status(403)
            .entity(new ErrorResponse("CAPSA_BOOTSTRAP_INVALID_TOKEN", e.getMessage()))
            .build();
    }
}
