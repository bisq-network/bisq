package bisq.httpapi.service.endpoint;

import bisq.httpapi.model.VersionDetails;

import bisq.common.app.Version;



import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.Authorization;
import javax.ws.rs.GET;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;


@Api(value = "version", authorizations = @Authorization(value = "accessToken"))
@Produces(MediaType.APPLICATION_JSON)
public class VersionEndpoint {

    @ApiOperation(value = "Get version details")
    @GET
    public VersionDetails getVersionDetails() {
        final VersionDetails versionDetails = new VersionDetails();
        versionDetails.application = Version.VERSION;
        versionDetails.network = Version.P2P_NETWORK_VERSION;
        versionDetails.p2PMessage = Version.getP2PMessageVersion();
        versionDetails.localDB = Version.LOCAL_DB_VERSION;
        versionDetails.tradeProtocol = Version.TRADE_PROTOCOL_VERSION;
        return versionDetails;
    }
}
