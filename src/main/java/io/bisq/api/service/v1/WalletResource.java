package io.bisq.api.service.v1;

import io.bisq.api.BisqProxy;
import io.bisq.api.BisqProxyResult;
import io.bisq.api.model.WalletAddress;
import io.bisq.api.model.WalletDetails;
import io.bisq.api.model.WalletTransactionList;
import io.bisq.api.service.ResourceHelper;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import java.util.Collection;
import java.util.Optional;


@Api("wallet")
@Produces(MediaType.APPLICATION_JSON)
public class WalletResource {

    private final BisqProxy bisqProxy;

    public WalletResource(BisqProxy bisqProxy) {
        this.bisqProxy = bisqProxy;
    }

    @ApiOperation(value = "Get wallet details")
    @GET
    @Path("/")
    public WalletDetails getWalletDetails() {
        BisqProxyResult<WalletDetails> walletDetails = bisqProxy.getWalletDetails();
        if (walletDetails.isInError()) {
            ResourceHelper.handleBisqProxyError(Optional.of(walletDetails));
        }
        return walletDetails.getResult();
    }

    @ApiOperation("Get wallet addresses")
    @GET
    @Path("/addresses")
    public Collection<WalletAddress> getAddresses() {
        return bisqProxy.getWalletAddresses();
    }

    @ApiOperation("Get wallet transactions")
    @GET
    @Path("/transactions")
    public WalletTransactionList getTransactions() {
        return bisqProxy.getWalletTransactions();
    }

}
