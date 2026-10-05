package com.capsa.bootstrap.internal.rest;

import com.capsa.bootstrap.api.ClaimBootstrapCommand;
import com.capsa.bootstrap.api.BootstrapService;
import com.capsa.users.api.CurrentUser;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

// GET /capsa/api/bootstrap/status is public (permit policy in application.properties).
// POST /capsa/api/bootstrap requires authentication (default OIDC policy).
@Path("/capsa/api/bootstrap")
@RequestScoped
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class BootstrapResource {

    private static final Logger LOG = LoggerFactory.getLogger(BootstrapResource.class);

    @Inject
    BootstrapService bootstrapService;

    @Inject
    CurrentUser currentUser;

    @GET
    @Path("/status")
    public Response status() {
        LOG.info("GET /capsa/api/bootstrap/status");
        return Response.ok(bootstrapService.getStatus()).build();
    }

    @POST
    public Response claim(ClaimBootstrapCommand command) {
        if (command == null || command.token() == null || command.token().isBlank()) {
            LOG.warn("POST /capsa/api/bootstrap rejected: missing token");
            return Response.status(422)
                .entity("{\"code\":\"CAPSA_VALIDATION_ERROR\",\"message\":\"token is required\"}")
                .build();
        }
        LOG.info("POST /capsa/api/bootstrap");
        var status = bootstrapService.claim(currentUser.identity(), command);
        return Response.status(201).entity(status).build();
    }
}
