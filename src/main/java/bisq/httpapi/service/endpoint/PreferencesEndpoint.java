package bisq.httpapi.service.endpoint;

import bisq.httpapi.facade.PreferencesFacade;
import bisq.httpapi.model.Preferences;
import bisq.httpapi.model.PreferencesAvailableValues;

import javax.inject.Inject;



import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.Authorization;
import javax.validation.Valid;
import javax.ws.rs.GET;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

@Api(value = "preferences", authorizations = @Authorization(value = "accessToken"))
@Produces(MediaType.APPLICATION_JSON)
public class PreferencesEndpoint {

    private final PreferencesFacade preferencesFacade;

    @Inject
    public PreferencesEndpoint(PreferencesFacade preferencesFacade) {
        this.preferencesFacade = preferencesFacade;
    }

    @ApiOperation("Get preferences")
    @GET
    public Preferences getPreferences() {
        return preferencesFacade.getPreferences();
    }

    @ApiOperation(value = "Set preferences", notes = "Supports partial update")
    @PUT
    public Preferences setPreferences(@Valid Preferences preferences) {
        return preferencesFacade.setPreferences(preferences);
    }

    @ApiOperation("Get available preferences values")
    @GET
    @Path("/available-values")
    public PreferencesAvailableValues getPreferencesAvailableValues() {
        return preferencesFacade.getPreferencesAvailableValues();
    }
}
