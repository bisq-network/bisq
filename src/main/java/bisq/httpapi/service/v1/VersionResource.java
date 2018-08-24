package bisq.httpapi.service.v1;

import bisq.httpapi.BisqProxy;
import bisq.httpapi.model.VersionDetails;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.Authorization;
import javax.ws.rs.GET;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;


@Api(value = "version", authorizations = @Authorization(value = "accessToken"))
@Produces(MediaType.APPLICATION_JSON)
public class VersionResource {

    private final BisqProxy bisqProxy;

    public VersionResource(BisqProxy bisqProxy) {
        this.bisqProxy = bisqProxy;
    }

    @ApiOperation(value = "Get version details")
    @GET
    public VersionDetails getVersionDetails() {
        return bisqProxy.getVersionDetails();
    }

}
