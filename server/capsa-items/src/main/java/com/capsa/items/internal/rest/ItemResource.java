package com.capsa.items.internal.rest;

import com.capsa.items.api.CreateItemCommand;
import com.capsa.items.api.ItemService;
import com.capsa.items.api.ItemStatusFilter;
import com.capsa.items.api.ItemView;
import com.capsa.lists.api.ListId;
import com.capsa.users.api.CurrentUser;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;

@Path("/capsa/api/items")
@RequestScoped
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class ItemResource {

    private static final Logger LOG = LoggerFactory.getLogger(ItemResource.class);

    @Inject
    CurrentUser currentUser;

    @Inject
    ItemService itemService;

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    public Response create(final CreateItemCommand command) {
        if (command == null || command.listId() == null) {
            LOG.warn("Rejecting item creation: missing listId (userId={})", currentUser.userId().value());
            return Response.status(422).entity("{\"code\":\"CAPSA_VALIDATION_ERROR\",\"message\":\"listId is required\"}").build();
        }

        if (command.name() == null || command.name().isBlank()) {
            LOG.warn("Rejecting item creation: missing or blank name (userId={} listId={})",
                currentUser.userId().value(), command.listId());
            return Response.status(422).entity("{\"code\":\"CAPSA_VALIDATION_ERROR\",\"message\":\"name is required\"}").build();
        }

        LOG.info("POST /capsa/api/items userId={} listId={} nameLength={}",
            currentUser.userId().value(), command.listId(), command.name().length());
        final ItemView item = itemService.create(currentUser.userId(), command);
        LOG.debug("Created item itemId={} listId={}", item.itemId().value(), item.listId());
        return Response.status(201).entity(item).build();
    }

    @GET
    public Response getByList(
            @QueryParam("listId") final UUID listId,
            @QueryParam("status") @DefaultValue("active") final String statusParam
    ) {
        if (listId == null) {
            LOG.warn("Rejecting items query: missing listId (userId={})", currentUser.userId().value());
            return Response.status(422).entity("{\"code\":\"CAPSA_VALIDATION_ERROR\",\"message\":\"listId is required\"}").build();
        }

        LOG.info("GET /capsa/api/items userId={} listId={} status={}",
            currentUser.userId().value(), listId, statusParam);
        final ItemStatusFilter filter = switch (statusParam.toLowerCase()) {
            case "history" -> ItemStatusFilter.HISTORY;
            case "all"     -> ItemStatusFilter.ALL;
            default        -> ItemStatusFilter.ACTIVE;
        };
        return Response.ok(itemService.getByList(currentUser.userId(), new ListId(listId), filter)).build();
    }

    @PUT
    @Path("/{id}/completion")
    public ItemView complete(@PathParam("id") final UUID id) {
        LOG.info("PUT /capsa/api/items/{}/completion userId={}", id, currentUser.userId().value());
        return itemService.complete(currentUser.userId(), new com.capsa.items.api.ItemId(id));
    }
}