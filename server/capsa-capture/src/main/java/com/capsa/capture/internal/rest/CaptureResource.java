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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;

@Path("/capsa/api/captures")
@RequestScoped
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class CaptureResource {

    private static final Logger LOG = LoggerFactory.getLogger(CaptureResource.class);

    @Inject
    CurrentUser currentUser;

    @Inject
    CaptureService captureService;

    @POST
    public Response submit(final SubmitCaptureCommand command) {
        if (command == null || command.content() == null || command.content().isBlank()) {
            LOG.warn("Rejecting capture submit: missing or blank content (userId={})", currentUser.userId().value());
            return Response.status(422).entity("{\"code\":\"CAPSA_VALIDATION_ERROR\",\"message\":\"content is required\"}").build();
        }

        // Note: content length is logged, not the content itself (sensitive per architecture §14).
        LOG.info("POST /capsa/api/captures userId={} contentLength={}",
            currentUser.userId().value(), command.content().length());
        final CaptureResult result = captureService.submit(currentUser.userId(), command.content());
        return switch (result) {
            case CaptureResult.Classified classified -> {
                LOG.debug("Capture auto-classified itemId={} listId={}",
                    classified.item().itemId().value(), classified.item().listId());
                yield Response.status(201).entity(classified.item()).build();
            }
            case CaptureResult.NeedsResolution needs -> {
                LOG.info("Capture awaiting resolution captureId={} candidateCount={}",
                    needs.captureId().value(), needs.candidates().size());
                yield Response.status(200).entity(needs).build();
            }
        };
    }

    @POST
    @Path("/{id}/resolution")
    public Response resolve(
            @PathParam("id") final UUID id,
            final ResolveRequest request) {
        if (request == null || request.listId() == null) {
            LOG.warn("Rejecting capture resolve: missing listId (userId={} captureId={})",
                currentUser.userId().value(), id);
            return Response.status(422).entity("{\"code\":\"CAPSA_VALIDATION_ERROR\",\"message\":\"listId is required\"}").build();
        }

        LOG.info("POST /capsa/api/captures/{}/resolution userId={} listId={}",
            id, currentUser.userId().value(), request.listId());
        final ItemView item = captureService.resolve(
            currentUser.userId(),
            new CaptureId(id),
            new ListId(request.listId())
        );
        return Response.status(201).entity(item).build();
    }

    public record ResolveRequest(UUID listId) {}
}