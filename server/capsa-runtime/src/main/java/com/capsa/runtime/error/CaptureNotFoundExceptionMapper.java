package com.capsa.runtime.error;

import com.capsa.capture.api.CaptureNotFoundException;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

@Provider
public class CaptureNotFoundExceptionMapper implements ExceptionMapper<CaptureNotFoundException> {

    @Override
    public Response toResponse(CaptureNotFoundException e) {
        return Response.status(404)
            .entity(new ErrorResponse("CAPSA_CAPTURE_NOT_FOUND", e.getMessage()))
            .build();
    }
}
