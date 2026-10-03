package com.capsa.runtime.error;

import com.capsa.items.api.ItemAccessDeniedException;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

@Provider
public class ItemAccessDeniedExceptionMapper implements ExceptionMapper<ItemAccessDeniedException> {

    @Override
    public Response toResponse(ItemAccessDeniedException e) {
        return Response.status(403)
            .entity(new ErrorResponse("CAPSA_ACCESS_DENIED", e.getMessage()))
            .build();
    }
}
