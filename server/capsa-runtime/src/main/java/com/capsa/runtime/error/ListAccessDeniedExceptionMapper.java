package com.capsa.runtime.error;

import com.capsa.lists.api.ListAccessDeniedException;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

@Provider
public class ListAccessDeniedExceptionMapper implements ExceptionMapper<ListAccessDeniedException> {

    @Override
    public Response toResponse(ListAccessDeniedException e) {
        return Response.status(403)
            .entity(new ErrorResponse("CAPSA_ACCESS_DENIED", e.getMessage()))
            .build();
    }
}
