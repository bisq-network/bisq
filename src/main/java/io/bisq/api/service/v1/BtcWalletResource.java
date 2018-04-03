package io.bisq.api.service.v1;

import io.bisq.api.BisqProxy;
import io.bisq.api.model.CreateBtcWalletAddress;
import io.bisq.api.model.WalletAddress;
import io.swagger.annotations.ApiOperation;

import javax.validation.Valid;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;


@Produces(MediaType.APPLICATION_JSON)
public class BtcWalletResource {

    private final BisqProxy bisqProxy;

    BtcWalletResource(BisqProxy bisqProxy) {
        this.bisqProxy = bisqProxy;
    }

    @ApiOperation("Get or create wallet addresses")
    @POST
    @Path("/addresses")
    public WalletAddress getOrCreateBtcWalletAddresses(@Valid CreateBtcWalletAddress payload) {
        return bisqProxy.getOrCreateBtcWalletAddresses(payload.context, payload.unused);
    }

}
