package com.capsa.lists.internal.rest;

import com.capsa.lists.api.CreateListCommand;
import com.capsa.lists.api.ListId;
import com.capsa.lists.api.ListService;
import com.capsa.lists.api.ListView;
import com.capsa.users.api.CurrentUser;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.UUID;

@Path("/capsa/api/lists")
@RequestScoped
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class ListResource {

    @Inject
    CurrentUser currentUser;

    @Inject
    ListService listService;

    @POST
    public Response create(final CreateListCommand command) {
        if (command == null || command.name() == null || command.name().isBlank()) {
            return Response.status(422).entity("{\"code\":\"CAPSA_VALIDATION_ERROR\",\"message\":\"name is required\"}").build();
        }
        ListView list = listService.create(currentUser.userId(), command);
        return Response.status(201).entity(list).build();
    }

    @GET
    @Path("/{id}")
    public ListView getById(@PathParam("id") final UUID id) {
        return listService.getById(currentUser.userId(), new ListId(id));
    }
}
