package io.bisq.core.offer;

import io.bisq.common.locale.CurrencyUtil;
import io.bisq.common.monetary.Price;
import io.bisq.common.monetary.Volume;
import io.bisq.protobuffer.payload.payment.PaymentMethod;
import org.bitcoinj.core.Coin;
import org.bitcoinj.utils.MonetaryFormat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Date;

public class OfferForJson {
    private static final Logger log = LoggerFactory.getLogger(OfferForJson.class);

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

    // primaryMarket fields are based on industry standard where primaryMarket is always in the focus (in the app BTC is always in the focus - will be changed in a larger refactoring once)
    public String currencyPair;
    public Offer.Direction primaryMarketDirection;

    public String priceDisplayString;
    public String primaryMarketAmountDisplayString;
    public String primaryMarketMinAmountDisplayString;
    public String primaryMarketVolumeDisplayString;
    public String primaryMarketMinVolumeDisplayString;

    public long primaryMarketPrice;
    public long primaryMarketAmount;
    public long primaryMarketMinAmount;
    public long primaryMarketVolume;
    public long primaryMarketMinVolume;


    public OfferForJson(Offer.Direction direction,
                        String currencyCode,
                        Coin minAmount,
                        Coin amount,
                        Price price,
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
        this.price = price.getValue();
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

    protected final MonetaryFormat fiatFormat = new MonetaryFormat().shift(0).minDecimals(4).repeatOptionalDecimals(0, 0);
    protected final MonetaryFormat altcoinFormat = new MonetaryFormat().shift(0).minDecimals(8).repeatOptionalDecimals(0, 0);
    protected final MonetaryFormat coinFormat = MonetaryFormat.BTC;
    //protected final DecimalFormat decimalFormat = new DecimalFormat("#.#");

    private void setDisplayStrings() {
        try {
            final Price price = getPrice();
            if (CurrencyUtil.isCryptoCurrency(currencyCode)) {
                primaryMarketDirection = direction == Offer.Direction.BUY ? Offer.Direction.SELL : Offer.Direction.BUY;
                currencyPair = currencyCode + "/" + "BTC";

                // int precision = 8;
                //decimalFormat.setMaximumFractionDigits(precision);

                // amount and volume is inverted for json    
                priceDisplayString = altcoinFormat.noCode().format(price.getMonetary()).toString();
                primaryMarketMinAmountDisplayString = altcoinFormat.noCode().format(getMinVolume().getMonetary()).toString();
                primaryMarketAmountDisplayString = altcoinFormat.noCode().format(getVolume().getMonetary()).toString();
                primaryMarketMinVolumeDisplayString = coinFormat.noCode().format(getMinAmountAsCoin()).toString();
                primaryMarketVolumeDisplayString = coinFormat.noCode().format(getAmountAsCoin()).toString();

                primaryMarketPrice = price.getValue();
                primaryMarketMinAmount = getMinVolume().getValue();
                primaryMarketAmount = getVolume().getValue();
                primaryMarketMinVolume = getMinAmountAsCoin().getValue();
                primaryMarketVolume = getAmountAsCoin().getValue();


                //final double value = price.value != 0 ? price.value / 100000000D : 0;
                // priceDisplayString = decimalFormat.format(MathUtils.roundDouble(value, precision)).replace(",", ".");
                // primaryMarketMinAmountDisplayString = altcoinFormat.noCode().format(getMinVolume()).toString();
                // primaryMarketAmountDisplayString = altcoinFormat.noCode().format(getVolume()).toString();
                // primaryMarketMinVolumeDisplayString = coinFormat.noCode().format(getMinAmountAsCoin()).toString();
                // primaryMarketVolumeDisplayString = coinFormat.noCode().format(getAmountAsCoin()).toString();

                
                /*primaryMarketPrice = MathUtils.roundDoubleToLong(MathUtils.scaleUpByPowerOf10(value, precision));
                primaryMarketMinAmount = (long) MathUtils.scaleUpByPowerOf10(getMinVolume().longValue(), precision);
                primaryMarketAmount = (long) MathUtils.scaleUpByPowerOf10(getVolume().longValue(), precision);
                primaryMarketMinVolume = getMinAmountAsCoin().longValue();
                primaryMarketVolume = getAmountAsCoin().longValue();*/

            } else {
                primaryMarketDirection = direction;
                currencyPair = "BTC/" + currencyCode;

                priceDisplayString = fiatFormat.noCode().format(price.getMonetary()).toString();
                primaryMarketMinAmountDisplayString = coinFormat.noCode().format(getMinAmountAsCoin()).toString();
                primaryMarketAmountDisplayString = coinFormat.noCode().format(getAmountAsCoin()).toString();
                primaryMarketMinVolumeDisplayString = fiatFormat.noCode().format(getMinVolume().getMonetary()).toString();
                primaryMarketVolumeDisplayString = fiatFormat.noCode().format(getVolume().getMonetary()).toString();

                primaryMarketPrice = price.getValue();
                primaryMarketMinAmount = getMinAmountAsCoin().getValue();
                primaryMarketAmount = getAmountAsCoin().getValue();
                primaryMarketMinVolume = getMinVolume().getValue();
                primaryMarketVolume = getVolume().getValue();
                
           /*     
                priceDisplayString = fiatFormat.noCode().format(price.getMonetary()).toString();

                primaryMarketMinAmountDisplayString = coinFormat.noCode().format(getMinAmountAsCoin()).toString();
                primaryMarketAmountDisplayString = coinFormat.noCode().format(getAmountAsCoin()).toString();
                primaryMarketMinVolumeDisplayString = fiatFormat.noCode().format(getMinVolume().getMonetary()).toString();
                primaryMarketVolumeDisplayString = fiatFormat.noCode().format(getVolume().getMonetary()).toString();

                int precision = 4;
                primaryMarketPrice = (long) MathUtils.scaleUpByPowerOf10(price.getValue(), precision);
                primaryMarketMinAmount = getMinAmountAsCoin().getValue();
                primaryMarketAmount = getAmountAsCoin().getValue();
                primaryMarketMinVolume = (long) MathUtils.scaleUpByPowerOf10(getMinVolume().getValue(), precision);
                primaryMarketVolume = (long) MathUtils.scaleUpByPowerOf10(getVolume().getValue(), precision);*/
            }

        } catch (Throwable t) {
            log.error("Error at setDisplayStrings: " + t.getMessage());
        }
    }

    private Price getPrice() {
        return Price.valueOf(currencyCode, price);
    }

    private Coin getAmountAsCoin() {
        return Coin.valueOf(amount);
    }

    private Coin getMinAmountAsCoin() {
        return Coin.valueOf(minAmount);
    }

    private Volume getVolume() {
        return getPrice().getVolumeByAmount(getAmountAsCoin());
    }

    private Volume getMinVolume() {
        return getPrice().getVolumeByAmount(getMinAmountAsCoin());
    }
}
