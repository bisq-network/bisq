package io.bisq.api.service.v1;

import io.bisq.api.BisqProxy;
import io.bisq.api.model.BitcoinNetworkStatus;
import io.bisq.api.model.P2PNetworkStatus;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.Authorization;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;


@Api(value = "network", authorizations = @Authorization(value = "accessToken"))
@Produces(MediaType.APPLICATION_JSON)
public class NetworkResource {

    private final BisqProxy bisqProxy;

    public NetworkResource(BisqProxy bisqProxy) {
        this.bisqProxy = bisqProxy;
    }

    @ApiOperation(value = "Get Bitcoin network status")
    @GET
    @Path("/bitcoin/status")
    public BitcoinNetworkStatus getBitcoinNetworkStatus() {
        return bisqProxy.getBitcoinNetworkStatus();
    }

    @ApiOperation(value = "Get P2P network status")
    @GET
    @Path("/p2p/status")
    public P2PNetworkStatus getP2PNetworkStatus() {
        return bisqProxy.getP2PNetworkStatus();
    }
}
