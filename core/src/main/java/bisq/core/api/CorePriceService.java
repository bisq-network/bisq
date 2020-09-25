package bisq.core.api;

import bisq.core.provider.price.MarketPrice;
import bisq.core.provider.price.PriceFeedService;

import bisq.common.util.MathUtils;

import javax.inject.Inject;

import lombok.extern.slf4j.Slf4j;

import static java.lang.String.format;


@Slf4j
class CorePriceService {

    private final PriceFeedService priceFeedService;

    @Inject
    public CorePriceService(PriceFeedService priceFeedService) {
        this.priceFeedService = priceFeedService;
    }

    public double getMarketPrice(String currencyCode) {
        if (!priceFeedService.hasPrices())
            throw new IllegalStateException(format("price feed service has no prices"));

        MarketPrice marketPrice = priceFeedService.getMarketPrice(currencyCode.toUpperCase());
        if (marketPrice.isPriceAvailable()) {
            return MathUtils.roundDouble(marketPrice.getPrice(), 4);
        } else {
            throw new IllegalStateException(format("'%s' price is not available", currencyCode));
        }
    }
}
