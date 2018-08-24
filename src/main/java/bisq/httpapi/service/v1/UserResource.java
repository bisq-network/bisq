package bisq.httpapi.service.v1;

import bisq.httpapi.BisqProxy;
import bisq.httpapi.model.AuthForm;
import bisq.httpapi.model.AuthResult;
import bisq.httpapi.model.ChangePassword;
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

    public UserResource(BisqProxy bisqProxy) {
        this.bisqProxy = bisqProxy;
    }

    @ApiOperation("Exchange password for access token")
    @POST
    @Path("/authenticate")
    public AuthResult authenticate(@Valid AuthForm authForm) {
        return bisqProxy.authenticate(authForm.password);
    }


    @ApiOperation("Change password")
    @POST
    @Path("/password")
    public AuthResult changePassword(@Valid ChangePassword data) {
        return bisqProxy.changePassword(data.oldPassword, data.newPassword);
    }

}
