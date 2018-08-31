package bisq.httpapi.service.endpoint;

import bisq.httpapi.facade.UserFacade;
import bisq.httpapi.model.AuthForm;
import bisq.httpapi.model.AuthResult;
import bisq.httpapi.model.ChangePassword;

import javax.inject.Inject;



import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import javax.validation.Valid;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;


@Api(value = "user")
@Produces(MediaType.APPLICATION_JSON)
public class UserEndpoint {

    private final UserFacade userFacade;

    @Inject
    public UserEndpoint(UserFacade userFacade) {
        this.userFacade = userFacade;
    }

    @ApiOperation("Exchange password for access token")
    @POST
    @Path("/authenticate")
    public AuthResult authenticate(@Valid AuthForm authForm) {
        return userFacade.authenticate(authForm.password);
    }


    @ApiOperation("Change password")
    @POST
    @Path("/password")
    public AuthResult changePassword(@Valid ChangePassword data) {
        return userFacade.changePassword(data.oldPassword, data.newPassword);
    }

}
