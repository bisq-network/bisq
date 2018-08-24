package bisq.httpapi.service;

import bisq.httpapi.BisqProxy;
import bisq.httpapi.service.v1.ArbitratorResource;
import bisq.httpapi.service.v1.BackupResource;
import bisq.httpapi.service.v1.ClosedTradableResource;
import bisq.httpapi.service.v1.CurrencyResource;
import bisq.httpapi.service.v1.MarketResource;
import bisq.httpapi.service.v1.NetworkResource;
import bisq.httpapi.service.v1.OfferResource;
import bisq.httpapi.service.v1.PaymentAccountResource;
import bisq.httpapi.service.v1.PreferencesResource;
import bisq.httpapi.service.v1.TradeResource;
import bisq.httpapi.service.v1.UserResource;
import bisq.httpapi.service.v1.VersionResource;
import bisq.httpapi.service.v1.WalletResource;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiKeyAuthDefinition;
import io.swagger.annotations.Authorization;
import io.swagger.annotations.SecurityDefinition;
import io.swagger.annotations.SwaggerDefinition;
import javax.ws.rs.Path;


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
public class ApiV1 {

    private final BisqProxy bisqProxy;

    public ApiV1(BisqProxy bisqProxy) {
        this.bisqProxy = bisqProxy;
    }

    @Path("arbitrators")
    public ArbitratorResource getArbitratorResource() {
        return new ArbitratorResource(bisqProxy);
    }

    @Path("backups")
    public BackupResource getBackupResource() {
        return new BackupResource(bisqProxy);
    }

    @Path("closed-tradables")
    public ClosedTradableResource getClosedTradableResource() {
        return new ClosedTradableResource(bisqProxy);
    }

    @Path("currencies")
    public CurrencyResource getCurrencyResource() {
        return new CurrencyResource(bisqProxy);
    }

    @Path("markets")
    public MarketResource getMarketResource() {
        return new MarketResource(bisqProxy);
    }

    @Path("network")
    public NetworkResource getNetworkResource() {
        return new NetworkResource(bisqProxy);
    }

    @Path("offers")
    public OfferResource getOfferResource() {
        return new OfferResource(bisqProxy);
    }

    @Path("payment-accounts")
    public PaymentAccountResource getPaymentAccountResource() {
        return new PaymentAccountResource(bisqProxy);
    }

    @Path("preferences")
    public PreferencesResource getSettingsResource() {
        return new PreferencesResource(bisqProxy);
    }

    @Path("trades")
    public TradeResource getTradeResource() {
        return new TradeResource(bisqProxy);
    }

    @Path("user")
    public UserResource getUserResource() {
        return new UserResource(bisqProxy);
    }

    @Path("version")
    public VersionResource getVersionResource() {
        return new VersionResource(bisqProxy);
    }

    @Path("wallet")
    public WalletResource getWalletResource() {
        return new WalletResource(bisqProxy);
    }
}
