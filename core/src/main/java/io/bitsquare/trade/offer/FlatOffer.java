package io.bitsquare.trade.offer;

import io.bitsquare.common.util.MathUtils;
import io.bitsquare.locale.CurrencyUtil;
import io.bitsquare.payment.PaymentMethod;
import org.bitcoinj.core.Coin;
import org.bitcoinj.utils.ExchangeRate;
import org.bitcoinj.utils.Fiat;
import org.bitcoinj.utils.MonetaryFormat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.text.DecimalFormat;
import java.util.Date;

public class FlatOffer {
    private static final Logger log = LoggerFactory.getLogger(FlatOffer.class);

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

    // Used in Json to provide same formatting/rounding for price
    public String priceDisplayString = "";
    public String amountDisplayString = "";
    public String minAmountDisplayString = "";
    public String volumeDisplayString = "";

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

        setDisplayStrings();
    }

    private void readObject(java.io.ObjectInputStream in) throws IOException, ClassNotFoundException {
        try {
            in.defaultReadObject();
            setDisplayStrings();
        } catch (Throwable t) {
            log.warn("Cannot be deserialized." + t.getMessage());
        }
    }

    private void setDisplayStrings() {
        try {
            MonetaryFormat fiatFormat = MonetaryFormat.FIAT.repeatOptionalDecimals(0, 0);
            MonetaryFormat coinFormat = MonetaryFormat.BTC.minDecimals(2).repeatOptionalDecimals(1, 6);
            final Fiat priceAsFiat = getPriceAsFiat();
            if (CurrencyUtil.isCryptoCurrency(currencyCode)) {
                DecimalFormat decimalFormat = new DecimalFormat("#.#");
                decimalFormat.setMaximumFractionDigits(8);
                final double value = priceAsFiat.value != 0 ? 10000D / priceAsFiat.value : 0;
                priceDisplayString = decimalFormat.format(MathUtils.roundDouble(value, 8)).replace(",", ".");

            } else {
                priceDisplayString = fiatFormat.noCode().format(priceAsFiat).toString();
            }

            amountDisplayString = coinFormat.noCode().format(getAmountAsCoin()).toString();
            minAmountDisplayString = coinFormat.noCode().format(getMinAmountAsCoin()).toString();
            volumeDisplayString = fiatFormat.noCode().format(getVolumeAsFiat()).toString();
        } catch (Throwable t) {
            log.error("Error at setDisplayStrings: " + t.getMessage());
        }
    }

    private Fiat getPriceAsFiat() {
        return Fiat.valueOf(currencyCode, price);
    }

    private Coin getAmountAsCoin() {
        return Coin.valueOf(amount);
    }

    private Coin getMinAmountAsCoin() {
        return Coin.valueOf(minAmount);
    }

    private Fiat getVolumeAsFiat() {
        return new ExchangeRate(getPriceAsFiat()).coinToFiat(getAmountAsCoin());
    }
}
