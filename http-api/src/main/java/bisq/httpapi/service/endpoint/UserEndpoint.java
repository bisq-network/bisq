package bisq.httpapi.service.endpoint;

import bisq.common.UserThread;

import bisq.httpapi.facade.UserFacade;
import bisq.httpapi.model.AuthForm;
import bisq.httpapi.model.AuthResult;
import bisq.httpapi.model.ChangePassword;
import bisq.httpapi.service.ExperimentalFeature;

import javax.inject.Inject;



import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import javax.validation.Valid;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.container.AsyncResponse;
import javax.ws.rs.container.Suspended;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;


@Api(value = "user")
@Produces(MediaType.APPLICATION_JSON)
public class UserEndpoint {

    private final ExperimentalFeature experimentalFeature;
    private final UserFacade userFacade;

    @Inject
    public UserEndpoint(ExperimentalFeature experimentalFeature, UserFacade userFacade) {
        this.experimentalFeature = experimentalFeature;
        this.userFacade = userFacade;
    }

    @ApiOperation(value = "Exchange password for access token", response = AuthResult.class, notes = ExperimentalFeature.NOTE)
    @POST
    @Path("/authenticate")
    public void authenticate(@Suspended final AsyncResponse asyncResponse, @Valid AuthForm authForm) {
        UserThread.execute(() -> {
            try {
                experimentalFeature.assertEnabled();
                final AuthResult authResult = userFacade.authenticate(authForm.password);
                asyncResponse.resume(authResult);
            } catch (Throwable e) {
                asyncResponse.resume(e);
            }
        });
    }

    @ApiOperation(value = "Change password", response = AuthResult.class, notes = ExperimentalFeature.NOTE)
    @POST
    @Path("/password")
    public void changePassword(@Suspended final AsyncResponse asyncResponse, @Valid ChangePassword data) {
        UserThread.execute(() -> {
            try {
                experimentalFeature.assertEnabled();
                final AuthResult result = userFacade.changePassword(data.oldPassword, data.newPassword);
                if (null == result) {
                    asyncResponse.resume(Response.noContent().build());
                } else {
                    asyncResponse.resume(result);
                }
            } catch (Throwable e) {
                asyncResponse.resume(e);
            }
        });
    }
}
