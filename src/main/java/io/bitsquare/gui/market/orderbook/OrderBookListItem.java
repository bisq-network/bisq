package io.bitsquare.gui.market.orderbook;

import io.bitsquare.btc.BtcFormatter;
import io.bitsquare.gui.util.BitSquareFormatter;
import io.bitsquare.trade.Offer;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;

public class OrderBookListItem
{
    protected final StringProperty price = new SimpleStringProperty();
    protected final StringProperty amount = new SimpleStringProperty();
    protected final StringProperty volume = new SimpleStringProperty();

    protected final Offer offer;


    public OrderBookListItem(Offer offer)
    {
        this.offer = offer;
        this.price.set(BitSquareFormatter.formatPrice(offer.getPrice()));

        double amountAsBtcDouble = BtcFormatter.satoshiToBTC(offer.getAmount());
        double minAmountAsBtcDouble = BtcFormatter.satoshiToBTC(offer.getMinAmount());
        this.amount.set(BitSquareFormatter.formatAmountWithMinAmount(amountAsBtcDouble, minAmountAsBtcDouble));

        this.volume.set(BitSquareFormatter.formatVolumeWithMinVolume(offer.getVolume(), offer.getMinVolume()));
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
