package io.bitsquare.gui.main.markets.spread;

import org.bitcoinj.core.Coin;
import org.bitcoinj.utils.Fiat;
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
    public final Fiat spread;
    public final Coin totalAmount;

    public SpreadItem(String currencyCode, int numberOfBuyOffers, int numberOfSellOffers, int numberOfOffers, @Nullable Fiat spread, Coin totalAmount) {
        this.currencyCode = currencyCode;
        this.numberOfBuyOffers = numberOfBuyOffers;
        this.numberOfSellOffers = numberOfSellOffers;
        this.numberOfOffers = numberOfOffers;
        this.spread = spread;
        this.totalAmount = totalAmount;
    }
}
