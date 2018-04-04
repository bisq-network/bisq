package io.bisq.api.service.v1;

import io.bisq.api.BisqProxy;
import io.swagger.annotations.Api;

import javax.ws.rs.Path;

@Api
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

    @Path("trades")
    public TradeResource getTradeResource() {
        return new TradeResource(bisqProxy);
    }

    @Path("wallet")
    public WalletResource getWalletResource() {
        return new WalletResource(bisqProxy);
    }
}
