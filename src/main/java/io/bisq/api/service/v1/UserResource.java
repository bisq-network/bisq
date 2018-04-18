package io.bisq.api.service.v1;

import io.bisq.api.BisqProxy;
import io.bisq.api.model.AuthForm;
import io.bisq.api.model.AuthResult;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;

import javax.validation.Valid;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;


@Api(value = "user")
@Produces(MediaType.APPLICATION_JSON)
public class UserResource {

    private final BisqProxy bisqProxy;

    UserResource(BisqProxy bisqProxy) {
        this.bisqProxy = bisqProxy;
    }

    @ApiOperation("Authenticate")
    @POST
    @Path("/auth")
    public AuthResult authenticate(@Valid AuthForm authForm) {
        return bisqProxy.authenticate(authForm.password);
    }

}
