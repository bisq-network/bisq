package io.bisq.gui.main.market.spread;

import io.bisq.common.monetary.Price;
import org.bitcoinj.core.Coin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;

public class SpreadItem {
    private static final Logger log = LoggerFactory.getLogger(SpreadItem.class);
    public final String currencyCode;
    public final int numberOfBuyOffers;
    public final int numberOfSellOffers;
    public final int numberOfOffers;
    @Nullable
    public final Price priceSpread;
    public final String percentage;
    public final Coin totalAmount;

    public SpreadItem(String currencyCode, int numberOfBuyOffers, int numberOfSellOffers, int numberOfOffers, @Nullable Price priceSpread, String percentage, Coin totalAmount) {
        this.currencyCode = currencyCode;
        this.numberOfBuyOffers = numberOfBuyOffers;
        this.numberOfSellOffers = numberOfSellOffers;
        this.numberOfOffers = numberOfOffers;
        this.priceSpread = priceSpread;
        this.percentage = percentage;
        this.totalAmount = totalAmount;
    }
}
