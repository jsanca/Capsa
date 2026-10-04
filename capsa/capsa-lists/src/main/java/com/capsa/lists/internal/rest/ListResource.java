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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;

@Path("/capsa/api/lists")
@RequestScoped
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class ListResource {

    private static final Logger LOG = LoggerFactory.getLogger(ListResource.class);

    @Inject
    CurrentUser currentUser;

    @Inject
    ListService listService;

    @POST
    public Response create(final CreateListCommand command) {
        if (command == null || command.name() == null || command.name().isBlank()) {
            LOG.warn("Rejecting list creation: missing or blank name (userId={})", currentUser.userId().value());
            return Response.status(422).entity("{\"code\":\"CAPSA_VALIDATION_ERROR\",\"message\":\"name is required\"}").build();
        }
        LOG.info("POST /capsa/api/lists userId={} nameLength={}", currentUser.userId().value(), command.name().length());
        ListView list = listService.create(currentUser.userId(), command);
        LOG.debug("Created list listId={}", list.listId().value());
        return Response.status(201).entity(list).build();
    }

    @GET
    @Path("/{id}")
    public ListView getById(@PathParam("id") final UUID id) {
        LOG.info("GET /capsa/api/lists/{} userId={}", id, currentUser.userId().value());
        return listService.getById(currentUser.userId(), new ListId(id));
    }
}