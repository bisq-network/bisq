package network.bisq.api.service.v1;

import bisq.core.btc.AddressEntryException;
import bisq.core.btc.InsufficientFundsException;
import com.google.common.collect.ImmutableList;
import network.bisq.api.AmountTooLowException;
import network.bisq.api.BisqProxy;
import io.dropwizard.jersey.validation.ValidationErrorMessage;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.Authorization;
import lombok.extern.slf4j.Slf4j;
import network.bisq.api.model.*;
import org.bitcoinj.core.Coin;

import javax.validation.Valid;
import javax.validation.ValidationException;
import javax.validation.constraints.NotNull;
import javax.ws.rs.*;
import javax.ws.rs.container.AsyncResponse;
import javax.ws.rs.container.Suspended;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.util.HashSet;


@Api(value = "wallet", authorizations = @Authorization(value = "accessToken"))
@Produces(MediaType.APPLICATION_JSON)
@Slf4j
public class WalletResource {

    private final BisqProxy bisqProxy;

    WalletResource(BisqProxy bisqProxy) {
        this.bisqProxy = bisqProxy;
    }

    @ApiOperation(value = "Get wallet details")
    @GET
    public WalletDetails getWalletDetails() {
        return bisqProxy.getWalletDetails();
    }

    @ApiOperation("Get wallet addresses")
    @GET
    @Path("/addresses")
    public WalletAddressList getAddresses(@QueryParam("purpose") BisqProxy.WalletAddressPurpose purpose) {
        return bisqProxy.getWalletAddresses(purpose);
    }

    @ApiOperation("Get or create wallet address")
    @POST
    @Path("/addresses")
    public WalletAddress getOrCreateAvailableUnusedWalletAddresses() {
        return bisqProxy.getOrCreateAvailableUnusedWalletAddresses();
    }

    @ApiOperation("Get wallet seed words")
    @POST
    @Path("/seed-words/retrieve")
    public SeedWords getSeedWords(AuthForm form) {
        final String password = null == form ? null : form.password;
        return bisqProxy.getSeedWords(password);
    }

    @ApiOperation("Restore wallet from seed words")
    @POST
    @Path("/seed-words/restore")
    public void restoreWalletFromSeedWords(@Suspended final AsyncResponse asyncResponse, @Valid @NotNull SeedWordsRestore data) {
        bisqProxy.restoreWalletFromSeedWords(data.mnemonicCode, data.walletCreationDate, data.password)
                .thenApply(response -> asyncResponse.resume(Response.noContent().build()))
                .exceptionally(e -> {
                    final Throwable cause = e.getCause();
                    final Response.ResponseBuilder responseBuilder;

                    final String message = cause.getMessage();
                    responseBuilder = Response.status(500);
                    if (null != message)
                        responseBuilder.entity(new ValidationErrorMessage(ImmutableList.of(message)));
                    log.error("Unable to restore wallet from seed", cause);
                    return asyncResponse.resume(responseBuilder.build());
                });
    }

    @ApiOperation("Get wallet transactions")
    @GET
    @Path("/transactions")
    public WalletTransactionList getTransactions() {
        return bisqProxy.getWalletTransactions();
    }

    @ApiOperation("Withdraw funds")
    @POST
    @Path("/withdraw")
    public void withdrawFunds(@Valid WithdrawFundsForm data) {
        final HashSet<String> sourceAddresses = new HashSet<>(data.sourceAddresses);
        final Coin amountAsCoin = Coin.valueOf(data.amount);
        final boolean feeExcluded = data.feeExcluded;
        final String targetAddress = data.targetAddress;
        try {
            bisqProxy.withdrawFunds(sourceAddresses, amountAsCoin, feeExcluded, targetAddress);
        } catch (AddressEntryException e) {
            throw new ValidationException(e.getMessage());
        } catch (InsufficientFundsException e) {
            throw new WebApplicationException(e.getMessage(), 423);
        } catch (AmountTooLowException e) {
            throw new WebApplicationException(e.getMessage(), 424);
        }
    }
}
