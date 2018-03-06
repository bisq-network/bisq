package io.bisq.api.service.v1;

import io.bisq.api.BisqProxy;
import io.bisq.api.model.BtcWalletAddress;
import io.bisq.api.model.CreateBtcWalletAddress;
import io.bisq.core.btc.AddressEntry;
import io.swagger.annotations.ApiOperation;

import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;


@Produces(MediaType.APPLICATION_JSON)
public class BtcWalletResource {

    private final BisqProxy bisqProxy;

    public BtcWalletResource(BisqProxy bisqProxy) {
        this.bisqProxy = bisqProxy;
    }

    @ApiOperation("Get or create wallet addresses")
    @POST
    @Path("/addresses")
    public BtcWalletAddress getOrCreateBtcWalletAddresses(CreateBtcWalletAddress payload) {
        final AddressEntry addressEntry = bisqProxy.getOrCreateBtcWalletAddresses(payload.context, payload.unused);
        final BtcWalletAddress btcWalletAddress = new BtcWalletAddress();
        btcWalletAddress.address = addressEntry.getAddressString();
        return btcWalletAddress;
    }

}
