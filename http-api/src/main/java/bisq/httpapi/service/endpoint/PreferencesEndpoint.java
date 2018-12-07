package bisq.httpapi.service.endpoint;

import bisq.common.UserThread;

import bisq.httpapi.facade.PreferencesFacade;
import bisq.httpapi.model.Preferences;
import bisq.httpapi.model.PreferencesAvailableValues;
import bisq.httpapi.service.ExperimentalFeature;

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

    private final ExperimentalFeature experimentalFeature;
    private final PreferencesFacade preferencesFacade;

    @Inject
    public PreferencesEndpoint(ExperimentalFeature experimentalFeature, PreferencesFacade preferencesFacade) {
        this.experimentalFeature = experimentalFeature;
        this.preferencesFacade = preferencesFacade;
    }

    @ApiOperation(value = "Get preferences", response = Preferences.class, notes = ExperimentalFeature.NOTE)
    @GET
    public void getPreferences(@Suspended final AsyncResponse asyncResponse) {
        UserThread.execute(() -> {
            try {
                experimentalFeature.assertEnabled();
                asyncResponse.resume(preferencesFacade.getPreferences());
            } catch (Throwable e) {
                asyncResponse.resume(e);
            }
        });
    }

    @ApiOperation(value = "Set preferences", notes = ExperimentalFeature.NOTE + "\nSupports partial update", response = Preferences.class)
    @PUT
    public void setPreferences(@Suspended final AsyncResponse asyncResponse, @Valid Preferences preferences) {
        UserThread.execute(() -> {
            try {
                experimentalFeature.assertEnabled();
                asyncResponse.resume(preferencesFacade.setPreferences(preferences));
            } catch (Throwable e) {
                asyncResponse.resume(e);
            }
        });
    }

    @ApiOperation(value = "Get available preferences values", response = PreferencesAvailableValues.class, notes = ExperimentalFeature.NOTE)
    @GET
    @Path("/available-values")
    public void getPreferencesAvailableValues(@Suspended final AsyncResponse asyncResponse) {
        UserThread.execute(() -> {
            try {
                experimentalFeature.assertEnabled();
                asyncResponse.resume(preferencesFacade.getPreferencesAvailableValues());
            } catch (Throwable e) {
                asyncResponse.resume(e);
            }
        });
    }
}
