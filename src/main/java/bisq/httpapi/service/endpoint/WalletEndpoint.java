package bisq.httpapi.service.endpoint;

import bisq.core.btc.AddressEntryException;
import bisq.core.btc.Balances;
import bisq.core.btc.InsufficientFundsException;

import bisq.httpapi.exceptions.AmountTooLowException;
import bisq.httpapi.facade.WalletFacade;
import bisq.httpapi.model.AuthForm;
import bisq.httpapi.model.SeedWords;
import bisq.httpapi.model.SeedWordsRestore;
import bisq.httpapi.model.WalletAddress;
import bisq.httpapi.model.WalletAddressList;
import bisq.httpapi.model.WalletTransactionList;
import bisq.httpapi.model.WithdrawFundsForm;

import org.bitcoinj.core.Coin;

import javax.inject.Inject;

import com.google.common.collect.ImmutableList;

import java.util.HashSet;

import lombok.extern.slf4j.Slf4j;



import io.dropwizard.jersey.validation.ValidationErrorMessage;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.Authorization;
import javax.validation.Valid;
import javax.validation.ValidationException;
import javax.validation.constraints.NotNull;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.container.AsyncResponse;
import javax.ws.rs.container.Suspended;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

@Api(value = "wallet", authorizations = @Authorization(value = "accessToken"))
@Produces(MediaType.APPLICATION_JSON)
@Slf4j
public class WalletEndpoint {

    private final Balances balances;
    private final WalletFacade walletFacade;

    @Inject
    public WalletEndpoint(Balances balances, WalletFacade walletFacade) {
        this.balances = balances;
        this.walletFacade = walletFacade;
    }

    @ApiOperation(value = "Get wallet details")
    @GET
    public bisq.httpapi.model.Balances getWalletDetails() {
        return new bisq.httpapi.model.Balances(balances.getAvailableBalance().get().value,
                balances.getReservedBalance().get().value,
                balances.getLockedBalance().get().value);
    }

    @ApiOperation("Get wallet addresses")
    @GET
    @Path("/addresses")
    public WalletAddressList getAddresses(@QueryParam("purpose") WalletFacade.WalletAddressPurpose purpose) {
        return walletFacade.getWalletAddresses(purpose);
    }

    @ApiOperation("Get or create wallet address")
    @POST
    @Path("/addresses") //TODO should path be "addresses" ?
    public WalletAddress getOrCreateAvailableUnusedWalletAddresses() {
        return walletFacade.getOrCreateAvailableUnusedWalletAddresses();
    }

    @ApiOperation("Get wallet seed words")
    @POST
    @Path("/seed-words/retrieve")
    public SeedWords getSeedWords(AuthForm form) {
        final String password = null == form ? null : form.password;
        return walletFacade.getSeedWords(password);
    }

    @ApiOperation("Restore wallet from seed words")
    @POST
    @Path("/seed-words/restore")
    public void restoreWalletFromSeedWords(@Suspended final AsyncResponse asyncResponse, @Valid @NotNull SeedWordsRestore data) {
        walletFacade.restoreWalletFromSeedWords(data.mnemonicCode, data.walletCreationDate, data.password)
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
        return walletFacade.getWalletTransactions();
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
            walletFacade.withdrawFunds(sourceAddresses, amountAsCoin, feeExcluded, targetAddress);
        } catch (AddressEntryException e) {
            throw new ValidationException(e.getMessage());
        } catch (InsufficientFundsException e) {
            throw new WebApplicationException(e.getMessage(), 423);
        } catch (AmountTooLowException e) {
            throw new WebApplicationException(e.getMessage(), 424);
        }
    }
}
