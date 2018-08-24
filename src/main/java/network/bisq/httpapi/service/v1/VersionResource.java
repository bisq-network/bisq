package network.bisq.httpapi.service.v1;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.Authorization;
import javax.ws.rs.GET;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import network.bisq.httpapi.BisqProxy;
import network.bisq.httpapi.model.VersionDetails;


@Api(value = "version", authorizations = @Authorization(value = "accessToken"))
@Produces(MediaType.APPLICATION_JSON)
public class VersionResource {

    private final BisqProxy bisqProxy;

    VersionResource(BisqProxy bisqProxy) {
        this.bisqProxy = bisqProxy;
    }

    @ApiOperation(value = "Get version details")
    @GET
    public VersionDetails getVersionDetails() {
        return bisqProxy.getVersionDetails();
    }

}
