package io.snello.service.rs;

import io.smallrye.common.annotation.RunOnVirtualThread;
import io.snello.model.pojo.AuthUserRequest;
import io.snello.service.AuthService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import static io.snello.management.AppConstants.AUTH_PATH;
import static jakarta.ws.rs.core.Response.Status.CREATED;
import static jakarta.ws.rs.core.Response.Status.OK;
import static jakarta.ws.rs.core.Response.ok;

@Path(AUTH_PATH)
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@ApplicationScoped
@RunOnVirtualThread
public class AuthServiceRs {

    @Inject
    AuthService authService;

    @GET
    @Path("/users")
    public Response users() {
        return ok(authService.listUsers()).build();
    }

    @GET
    @Path("/users/{id}")
    public Response getUser(@PathParam("id") @NotNull String id) {
        return ok(authService.getUser(id)).build();
    }

    @GET
    @Path("/users/{id}/groups")
    public Response userGroups(@PathParam("id") @NotNull String id) {
        return ok(authService.listUserGroups(id)).build();
    }

    @GET
    @Path("/groups")
    public Response groups() {
        return ok(authService.listGroups()).build();
    }

    @GET
    @Path("/groups/{id}/users")
    public Response groupUsers(@PathParam("id") @NotNull String id) {
        return ok(authService.listGroupUsers(id)).build();
    }

    @POST
    @Path("/users")
    public Response createUser(AuthUserRequest request) {
        return Response.status(CREATED)
                .entity(authService.createUser(request))
                .build();
    }

    @POST
    @Path("/createGroups")
    public Response createGroups() {
        return Response.status(OK)
                .entity(authService.createGroups())
                .build();
    }

    @PUT
    @Path("/users/{id}")
    public Response updateUser(@PathParam("id") @NotNull String id,
            AuthUserRequest request) {
        return ok(authService.updateUser(id, request)).build();
    }
}
