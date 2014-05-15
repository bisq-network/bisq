package io.bitsquare.gui.market.orderbook;

import io.bitsquare.btc.BtcFormatter;
import io.bitsquare.gui.util.Formatter;
import io.bitsquare.trade.Offer;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;

/**
 * Wrapper for observable properties used by orderbook table view
 */
public class OrderBookListItem
{
    protected final StringProperty price = new SimpleStringProperty();
    protected final StringProperty amount = new SimpleStringProperty();
    protected final StringProperty volume = new SimpleStringProperty();

    protected Offer offer;


    public OrderBookListItem(Offer offer)
    {
        this.offer = offer;

        double amountAsBtcDouble = BtcFormatter.satoshiToBTC(offer.getAmount());
        double minAmountAsBtcDouble = BtcFormatter.satoshiToBTC(offer.getMinAmount());

        this.price.set(Formatter.formatPrice(offer.getPrice()));
        this.amount.set(Formatter.formatAmountWithMinAmount(amountAsBtcDouble, minAmountAsBtcDouble));
        this.volume.set(Formatter.formatVolumeWithMinVolume(offer.getVolume(), offer.getMinVolume()));
    }

    public Offer getOffer()
    {
        return offer;
    }

    // called form table columns
    public final StringProperty priceProperty()
    {
        return this.price;
    }

    public final StringProperty amountProperty()
    {
        return this.amount;
    }

    public final StringProperty volumeProperty()
    {
        return this.volume;
    }
}
