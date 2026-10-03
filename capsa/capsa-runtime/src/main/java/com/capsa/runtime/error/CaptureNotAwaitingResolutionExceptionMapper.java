package com.capsa.runtime.error;

import com.capsa.capture.api.CaptureNotAwaitingResolutionException;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

@Provider
public class CaptureNotAwaitingResolutionExceptionMapper implements ExceptionMapper<CaptureNotAwaitingResolutionException> {

    @Override
    public Response toResponse(CaptureNotAwaitingResolutionException e) {
        return Response.status(409)
            .entity(new ErrorResponse("CAPSA_CAPTURE_NOT_AWAITING_RESOLUTION", e.getMessage()))
            .build();
    }
}
