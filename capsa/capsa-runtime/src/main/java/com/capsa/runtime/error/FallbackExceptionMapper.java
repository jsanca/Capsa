package com.capsa.runtime.error;

import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

@Provider
public class FallbackExceptionMapper implements ExceptionMapper<Exception> {

    private static final System.Logger LOG = System.getLogger(FallbackExceptionMapper.class.getName());

    @Override
    public Response toResponse(Exception e) {
        if (e instanceof WebApplicationException wae) {
            return wae.getResponse();
        }
        LOG.log(System.Logger.Level.ERROR, "Unhandled exception", e);
        return Response.status(500)
            .entity(new ErrorResponse("CAPSA_INTERNAL_ERROR", "An unexpected error occurred."))
            .build();
    }
}
