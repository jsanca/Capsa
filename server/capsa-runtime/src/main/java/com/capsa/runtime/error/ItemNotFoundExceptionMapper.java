package com.capsa.runtime.error;

import com.capsa.items.api.ItemNotFoundException;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

@Provider
public class ItemNotFoundExceptionMapper implements ExceptionMapper<ItemNotFoundException> {

    @Override
    public Response toResponse(ItemNotFoundException e) {
        return Response.status(404)
            .entity(new ErrorResponse("CAPSA_ITEM_NOT_FOUND", e.getMessage()))
            .build();
    }
}
