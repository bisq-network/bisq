package bisq.httpapi.service;

import javax.inject.Inject;



import bisq.httpapi.service.resources.ArbitratorResource;
import bisq.httpapi.service.resources.BackupResource;
import bisq.httpapi.service.resources.ClosedTradableResource;
import bisq.httpapi.service.resources.CurrencyResource;
import bisq.httpapi.service.resources.MarketResource;
import bisq.httpapi.service.resources.NetworkResource;
import bisq.httpapi.service.resources.OfferResource;
import bisq.httpapi.service.resources.PaymentAccountResource;
import bisq.httpapi.service.resources.PreferencesResource;
import bisq.httpapi.service.resources.TradeResource;
import bisq.httpapi.service.resources.UserResource;
import bisq.httpapi.service.resources.VersionResource;
import bisq.httpapi.service.resources.WalletResource;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiKeyAuthDefinition;
import io.swagger.annotations.Authorization;
import io.swagger.annotations.SecurityDefinition;
import io.swagger.annotations.SwaggerDefinition;
import javax.ws.rs.Path;

/**
 * High level API version 1.
 */
@SwaggerDefinition(
        securityDefinition = @SecurityDefinition(
                apiKeyAuthDefinitions = @ApiKeyAuthDefinition(
                        in = ApiKeyAuthDefinition.ApiKeyLocation.HEADER,
                        key = "accessToken",
                        name = "authorization"
                )
        )
)
@Api(authorizations = @Authorization(value = "accessToken"))
@Path("/api/v1")
public class HttpApiInterfaceV1 {
    private final ArbitratorResource arbitratorResource;
    private final BackupResource backupResource;
    private final ClosedTradableResource closedTradableResource;
    private final CurrencyResource currencyResource;
    private final MarketResource marketResource;
    private final NetworkResource networkResource;
    private final OfferResource offerResource;
    private final PaymentAccountResource paymentAccountResource;
    private final PreferencesResource preferencesResource;
    private final TradeResource tradeResource;
    private final UserResource userResource;
    private final VersionResource versionResource;
    private final WalletResource walletResource;

    @Inject
    public HttpApiInterfaceV1(ArbitratorResource arbitratorResource,
                              BackupResource backupResource,
                              ClosedTradableResource closedTradableResource,
                              CurrencyResource currencyResource,
                              MarketResource marketResource,
                              NetworkResource networkResource,
                              OfferResource offerResource,
                              PaymentAccountResource paymentAccountResource,
                              PreferencesResource preferencesResource,
                              TradeResource tradeResource,
                              UserResource userResource,
                              VersionResource versionResource,
                              WalletResource walletResource) {
        this.arbitratorResource = arbitratorResource;
        this.backupResource = backupResource;
        this.closedTradableResource = closedTradableResource;
        this.currencyResource = currencyResource;
        this.marketResource = marketResource;
        this.networkResource = networkResource;
        this.offerResource = offerResource;
        this.paymentAccountResource = paymentAccountResource;
        this.preferencesResource = preferencesResource;
        this.tradeResource = tradeResource;
        this.userResource = userResource;
        this.versionResource = versionResource;
        this.walletResource = walletResource;
    }

    @Path("arbitrators")
    public ArbitratorResource getArbitratorResource() {
        return arbitratorResource;
    }

    @Path("backups")
    public BackupResource getBackupResource() {
        return backupResource;
    }

    @Path("closed-tradables")
    public ClosedTradableResource getClosedTradableResource() {
        return closedTradableResource;
    }

    @Path("currencies")
    public CurrencyResource getCurrencyResource() {
        return currencyResource;
    }

    @Path("markets")
    public MarketResource getMarketResource() {
        return marketResource;
    }

    @Path("network")
    public NetworkResource getNetworkResource() {
        return networkResource;
    }

    @Path("offers")
    public OfferResource getOfferResource() {
        return offerResource;
    }

    @Path("payment-accounts")
    public PaymentAccountResource getPaymentAccountResource() {
        return paymentAccountResource;
    }

    @Path("preferences")
    public PreferencesResource getSettingsResource() {
        return preferencesResource;
    }

    @Path("trades")
    public TradeResource getTradeResource() {
        return tradeResource;
    }

    @Path("user")
    public UserResource getUserResource() {
        return userResource;
    }

    @Path("version")
    public VersionResource getVersionResource() {
        return versionResource;
    }

    @Path("wallet")
    public WalletResource getWalletResource() {
        return walletResource;
    }
}
