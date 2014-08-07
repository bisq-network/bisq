package io.bitsquare.gui.market.orderbook;

import io.bitsquare.gui.util.BitSquareFormatter;
import io.bitsquare.trade.Offer;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;

public class OrderBookListItem
{
    private final StringProperty price = new SimpleStringProperty();
    private final StringProperty amount = new SimpleStringProperty();
    private final StringProperty volume = new SimpleStringProperty();


    private final Offer offer;


    public OrderBookListItem(Offer offer)
    {
        this.offer = offer;
        this.price.set(BitSquareFormatter.formatPrice(offer.getPrice()));
        this.amount.set(BitSquareFormatter.formatCoin(offer.getAmount()) + " (" + BitSquareFormatter.formatCoin(offer.getMinAmount()) + ")");
        this.volume.set(BitSquareFormatter.formatVolumeWithMinVolume(offer.getOfferVolume(), offer.getMinOfferVolume()));
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
