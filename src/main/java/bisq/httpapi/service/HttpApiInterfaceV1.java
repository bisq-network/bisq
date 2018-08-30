package bisq.httpapi.service;

import bisq.httpapi.service.endpoint.ArbitratorEndpoint;
import bisq.httpapi.service.endpoint.BackupEndpoint;
import bisq.httpapi.service.endpoint.ClosedTradableEndpoint;
import bisq.httpapi.service.endpoint.CurrencyEndpoint;
import bisq.httpapi.service.endpoint.MarketEndpoint;
import bisq.httpapi.service.endpoint.NetworkEndpoint;
import bisq.httpapi.service.endpoint.OfferEndpoint;
import bisq.httpapi.service.endpoint.PaymentAccountEndpoint;
import bisq.httpapi.service.endpoint.PreferencesEndpoint;
import bisq.httpapi.service.endpoint.TradeEndpoint;
import bisq.httpapi.service.endpoint.UserEndpoint;
import bisq.httpapi.service.endpoint.VersionEndpoint;
import bisq.httpapi.service.endpoint.WalletEndpoint;

import javax.inject.Inject;



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
    private final ArbitratorEndpoint arbitratorEndpoint;
    private final BackupEndpoint backupEndpoint;
    private final ClosedTradableEndpoint closedTradableEndpoint;
    private final CurrencyEndpoint currencyEndpoint;
    private final MarketEndpoint marketEndpoint;
    private final NetworkEndpoint networkEndpoint;
    private final OfferEndpoint offerEndPoint;
    private final PaymentAccountEndpoint paymentAccountEndpoint;
    private final PreferencesEndpoint preferencesEndpoint;
    private final TradeEndpoint tradeEndpoint;
    private final UserEndpoint userEndpoint;
    private final VersionEndpoint versionEndpoint;
    private final WalletEndpoint walletEndpoint;

    @Inject
    public HttpApiInterfaceV1(ArbitratorEndpoint arbitratorEndpoint,
                              BackupEndpoint backupEndpoint,
                              ClosedTradableEndpoint closedTradableEndpoint,
                              CurrencyEndpoint currencyEndpoint,
                              MarketEndpoint marketEndpoint,
                              NetworkEndpoint networkEndpoint,
                              OfferEndpoint offerEndPoint,
                              PaymentAccountEndpoint paymentAccountEndpoint,
                              PreferencesEndpoint preferencesEndpoint,
                              TradeEndpoint tradeEndpoint,
                              UserEndpoint userEndpoint,
                              VersionEndpoint versionEndpoint,
                              WalletEndpoint walletEndpoint) {
        this.arbitratorEndpoint = arbitratorEndpoint;
        this.backupEndpoint = backupEndpoint;
        this.closedTradableEndpoint = closedTradableEndpoint;
        this.currencyEndpoint = currencyEndpoint;
        this.marketEndpoint = marketEndpoint;
        this.networkEndpoint = networkEndpoint;
        this.offerEndPoint = offerEndPoint;
        this.paymentAccountEndpoint = paymentAccountEndpoint;
        this.preferencesEndpoint = preferencesEndpoint;
        this.tradeEndpoint = tradeEndpoint;
        this.userEndpoint = userEndpoint;
        this.versionEndpoint = versionEndpoint;
        this.walletEndpoint = walletEndpoint;
    }

    @Path("arbitrators")
    public ArbitratorEndpoint getArbitratorEndpoint() {
        return arbitratorEndpoint;
    }

    @Path("backups")
    public BackupEndpoint getBackupEndpoint() {
        return backupEndpoint;
    }

    @Path("closed-tradables")
    public ClosedTradableEndpoint getClosedTradableEndpoint() {
        return closedTradableEndpoint;
    }

    @Path("currencies")
    public CurrencyEndpoint getCurrencyEndpoint() {
        return currencyEndpoint;
    }

    @Path("markets")
    public MarketEndpoint getMarketEndpoint() {
        return marketEndpoint;
    }

    @Path("network")
    public NetworkEndpoint getNetworkEndpoint() {
        return networkEndpoint;
    }

    @Path("offers")
    public OfferEndpoint getOfferEndPoint() {
        return offerEndPoint;
    }

    @Path("payment-accounts")
    public PaymentAccountEndpoint getPaymentAccountEndpoint() {
        return paymentAccountEndpoint;
    }

    @Path("preferences")
    public PreferencesEndpoint getSettingsResource() {
        return preferencesEndpoint;
    }

    @Path("trades")
    public TradeEndpoint getTradeEndpoint() {
        return tradeEndpoint;
    }

    @Path("user")
    public UserEndpoint getUserEndpoint() {
        return userEndpoint;
    }

    @Path("version")
    public VersionEndpoint getVersionEndpoint() {
        return versionEndpoint;
    }

    @Path("wallet")
    public WalletEndpoint getWalletEndpoint() {
        return walletEndpoint;
    }
}
