package io.bitsquare.trade.offer;

import io.bitsquare.payment.PaymentMethod;
import org.bitcoinj.core.Coin;
import org.bitcoinj.utils.Fiat;

import java.util.Date;

public class FlatOffer {
    public final Offer.Direction direction;
    public final String currencyCode;
    public final long minAmount;
    public final long amount;
    public final long price;
    public final long date;
    public final boolean useMarketBasedPrice;
    public final double marketPriceMargin;
    public final String paymentMethod;
    public final String id;
    public final String offerFeeTxID;

    public FlatOffer(Offer.Direction direction,
                     String currencyCode,
                     Coin minAmount,
                     Coin amount,
                     Fiat price,
                     Date date,
                     String id,
                     boolean useMarketBasedPrice,
                     double marketPriceMargin,
                     PaymentMethod paymentMethod,
                     String offerFeeTxID) {

        this.direction = direction;
        this.currencyCode = currencyCode;
        this.minAmount = minAmount.value;
        this.amount = amount.value;
        this.price = price.value;
        this.date = date.getTime();
        this.id = id;
        this.useMarketBasedPrice = useMarketBasedPrice;
        this.marketPriceMargin = marketPriceMargin;
        this.paymentMethod = paymentMethod.getId();
        this.offerFeeTxID = offerFeeTxID;
    }
}
