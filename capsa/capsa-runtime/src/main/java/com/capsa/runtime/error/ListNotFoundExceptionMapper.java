package com.capsa.runtime.error;

import com.capsa.lists.api.ListNotFoundException;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

@Provider
public class ListNotFoundExceptionMapper implements ExceptionMapper<ListNotFoundException> {

    @Override
    public Response toResponse(ListNotFoundException e) {
        return Response.status(404)
            .entity(new ErrorResponse("CAPSA_LIST_NOT_FOUND", e.getMessage()))
            .build();
    }
}
