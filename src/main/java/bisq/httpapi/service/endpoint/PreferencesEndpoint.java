package bisq.httpapi.service.endpoint;

import bisq.httpapi.facade.PreferencesFacade;
import bisq.httpapi.model.Preferences;
import bisq.httpapi.model.PreferencesAvailableValues;

import bisq.common.UserThread;

import javax.inject.Inject;



import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.Authorization;
import javax.validation.Valid;
import javax.ws.rs.GET;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.container.AsyncResponse;
import javax.ws.rs.container.Suspended;
import javax.ws.rs.core.MediaType;

@Api(value = "preferences", authorizations = @Authorization(value = "accessToken"))
@Produces(MediaType.APPLICATION_JSON)
public class PreferencesEndpoint {

    private final PreferencesFacade preferencesFacade;

    @Inject
    public PreferencesEndpoint(PreferencesFacade preferencesFacade) {
        this.preferencesFacade = preferencesFacade;
    }

    @ApiOperation(value = "Get preferences", response = Preferences.class)
    @GET
    public void getPreferences(@Suspended final AsyncResponse asyncResponse) {
        UserThread.execute(() -> {
            try {
                asyncResponse.resume(preferencesFacade.getPreferences());
            } catch (Throwable e) {
                asyncResponse.resume(e);
            }
        });
    }

    @ApiOperation(value = "Set preferences", notes = "Supports partial update", response = Preferences.class)
    @PUT
    public void setPreferences(@Suspended final AsyncResponse asyncResponse, @Valid Preferences preferences) {
        UserThread.execute(() -> {
            try {
                asyncResponse.resume(preferencesFacade.setPreferences(preferences));
            } catch (Throwable e) {
                asyncResponse.resume(e);
            }
        });
    }

    @ApiOperation(value = "Get available preferences values", response = PreferencesAvailableValues.class)
    @GET
    @Path("/available-values")
    public void getPreferencesAvailableValues(@Suspended final AsyncResponse asyncResponse) {
        UserThread.execute(() -> {
            try {
                asyncResponse.resume(preferencesFacade.getPreferencesAvailableValues());
            } catch (Throwable e) {
                asyncResponse.resume(e);
            }
        });
    }
}
