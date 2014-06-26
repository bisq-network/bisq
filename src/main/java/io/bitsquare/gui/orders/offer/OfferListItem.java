package io.bitsquare.gui.orders.offer;

import io.bitsquare.btc.BtcFormatter;
import io.bitsquare.gui.util.BitSquareFormatter;
import io.bitsquare.trade.Offer;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;

import java.text.DateFormat;
import java.util.Locale;

public class OfferListItem
{
    protected final StringProperty price = new SimpleStringProperty();
    protected final StringProperty amount = new SimpleStringProperty();
    protected final StringProperty date = new SimpleStringProperty();
    protected final StringProperty volume = new SimpleStringProperty();
    private final String offerId;
    protected Offer offer;

    public OfferListItem(Offer offer)
    {
        this.offer = offer;

        DateFormat dateFormatter = DateFormat.getDateInstance(DateFormat.DEFAULT, Locale.getDefault());
        DateFormat timeFormatter = DateFormat.getTimeInstance(DateFormat.DEFAULT, Locale.getDefault());
        this.date.set(dateFormatter.format(offer.getCreationDate()) + " " + timeFormatter.format(offer.getCreationDate()));

        this.price.set(BitSquareFormatter.formatPrice(offer.getPrice()));

        double amountAsBtcDouble = BtcFormatter.satoshiToBTC(offer.getAmount());
        double minAmountAsBtcDouble = BtcFormatter.satoshiToBTC(offer.getMinAmount());
        this.amount.set(BitSquareFormatter.formatAmountWithMinAmount(amountAsBtcDouble, minAmountAsBtcDouble));

        this.volume.set(BitSquareFormatter.formatVolumeWithMinVolume(offer.getVolume(), offer.getMinVolume()));
        this.offerId = offer.getId();
    }

    public Offer getOffer()
    {
        return offer;
    }

    // called form table columns

    public final StringProperty dateProperty()
    {
        return this.date;
    }

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

    public String getOfferId()
    {
        return offerId;
    }
}
