package io.bitsquare.gui.trade.orderbook;

import io.bitsquare.gui.util.Formatter;
import io.bitsquare.trade.Offer;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;

/**
 * Wrapper for observable properties used by orderbook table view
 */
public class OrderBookListItem
{
    private final StringProperty price = new SimpleStringProperty();
    private final StringProperty amount = new SimpleStringProperty();
    private final StringProperty volume = new SimpleStringProperty();

    private Offer offer;


    public OrderBookListItem(Offer offer)
    {
        this.offer = offer;

        this.price.set(Formatter.formatPrice(offer.getPrice()));
        this.amount.set(Formatter.formatAmountWithMinAmount(offer.getAmount(), offer.getMinAmount()));
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
