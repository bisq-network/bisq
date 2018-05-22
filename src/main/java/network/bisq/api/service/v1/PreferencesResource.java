package network.bisq.api.service.v1;

import network.bisq.api.BisqProxy;
import network.bisq.api.model.Preferences;
import network.bisq.api.model.PreferencesAvailableValues;
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
public class PreferencesResource {

    private final BisqProxy bisqProxy;

    public PreferencesResource(BisqProxy bisqProxy) {
        this.bisqProxy = bisqProxy;
    }

    @ApiOperation("Get preferences")
    @GET
    public Preferences getPreferences() {
        return bisqProxy.getPreferences();
    }

    @ApiOperation(value = "Set preferences", notes = "Supports partial update")
    @PUT
    public Preferences setPreferences(@Valid Preferences preferences) {
        return bisqProxy.setPreferences(preferences);
    }

    @ApiOperation("Get available preferences values")
    @GET
    @Path("/available-values")
    public PreferencesAvailableValues getPreferencesAvailableValues() {
        return bisqProxy.getPreferencesAvailableValues();
    }

}
