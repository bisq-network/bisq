package bisq.httpapi.service.endpoint;

import bisq.httpapi.facade.NetworkFacade;
import bisq.httpapi.model.BitcoinNetworkStatus;
import bisq.httpapi.model.P2PNetworkStatus;

import javax.inject.Inject;



import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.Authorization;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;


@Api(value = "network", authorizations = @Authorization(value = "accessToken"))
@Produces(MediaType.APPLICATION_JSON)
public class NetworkEndpoint {

    private final NetworkFacade networkFacade;

    @Inject
    public NetworkEndpoint(NetworkFacade networkFacade) {
        this.networkFacade = networkFacade;
    }

    @ApiOperation(value = "Get Bitcoin network status")
    @GET
    @Path("/bitcoin/status")
    public BitcoinNetworkStatus getBitcoinNetworkStatus() {
        return networkFacade.getBitcoinNetworkStatus();
    }

    @ApiOperation(value = "Get P2P network status")
    @GET
    @Path("/p2p/status")
    public P2PNetworkStatus getP2PNetworkStatus() {
        return networkFacade.getP2PNetworkStatus();
    }
}
