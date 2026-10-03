package com.capsa.capture.internal.rest;

import com.capsa.capture.api.CaptureId;
import com.capsa.capture.api.CaptureResult;
import com.capsa.capture.api.CaptureService;
import com.capsa.capture.api.SubmitCaptureCommand;
import com.capsa.items.api.ItemView;
import com.capsa.lists.api.ListId;
import com.capsa.users.api.CurrentUser;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.UUID;

@Path("/capsa/api/captures")
@RequestScoped
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class CaptureResource {

    @Inject
    CurrentUser currentUser;

    @Inject
    CaptureService captureService;

    @POST
    public Response submit(final SubmitCaptureCommand command) {
        if (command == null || command.content() == null || command.content().isBlank()) {
            return Response.status(422).entity("{\"code\":\"CAPSA_VALIDATION_ERROR\",\"message\":\"content is required\"}").build();
        }

        final CaptureResult result = captureService.submit(currentUser.userId(), command.content());
        return switch (result) {
            case CaptureResult.Classified classified ->
                Response.status(201).entity(classified.item()).build();
            case CaptureResult.NeedsResolution needs ->
                Response.status(200).entity(needs).build();
        };
    }

    @POST
    @Path("/{id}/resolution")
    public Response resolve(
            @PathParam("id") final UUID id,
            final ResolveRequest request) {
        if (request == null || request.listId() == null) {
            return Response.status(422).entity("{\"code\":\"CAPSA_VALIDATION_ERROR\",\"message\":\"listId is required\"}").build();
        }

        final ItemView item = captureService.resolve(
            currentUser.userId(),
            new CaptureId(id),
            new ListId(request.listId())
        );
        return Response.status(201).entity(item).build();
    }

    public record ResolveRequest(UUID listId) {}
}
