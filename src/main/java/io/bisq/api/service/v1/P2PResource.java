package io.bisq.api.service.v1;

import io.bisq.api.BisqProxy;
import io.bisq.api.model.P2PNetworkStatus;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;


@Api("p2p")
@Produces(MediaType.APPLICATION_JSON)
public class P2PResource {

    private final BisqProxy bisqProxy;

    public P2PResource(BisqProxy bisqProxy) {
        this.bisqProxy = bisqProxy;
    }

    @ApiOperation(value = "Get P2P network status")
    @GET
    @Path("/status")
    public P2PNetworkStatus getP2PNetworkStatus() {
        return bisqProxy.getP2PNetworkStatus();
    }
}
