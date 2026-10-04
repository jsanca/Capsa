package com.capsa.runtime.error;

import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Provider
public class FallbackExceptionMapper implements ExceptionMapper<Exception> {

    private static final Logger LOG = LoggerFactory.getLogger(FallbackExceptionMapper.class);

    @Override
    public Response toResponse(Exception e) {
        if (e instanceof WebApplicationException wae) {
            return wae.getResponse();
        }
        LOG.error("Unhandled exception in request pipeline", e);
        return Response.status(500)
            .entity(new ErrorResponse("CAPSA_INTERNAL_ERROR", "An unexpected error occurred."))
            .build();
    }
}