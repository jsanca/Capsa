package com.capsa.runtime.error;

import com.capsa.users.api.UserNotFoundException;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

@Provider
public class UserNotFoundExceptionMapper implements ExceptionMapper<UserNotFoundException> {

    @Override
    public Response toResponse(UserNotFoundException e) {
        return Response.status(404)
            .entity(new ErrorResponse("CAPSA_USER_NOT_FOUND", e.getMessage()))
            .build();
    }
}
