package io.bitsquare.gui.orders.offer;

import io.bitsquare.btc.BtcFormatter;
import io.bitsquare.gui.util.BitSquareFormatter;
import io.bitsquare.trade.Offer;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import org.jetbrains.annotations.NotNull;

public class OfferListItem
{
    private final StringProperty price = new SimpleStringProperty();
    private final StringProperty amount = new SimpleStringProperty();
    private final StringProperty date = new SimpleStringProperty();
    private final StringProperty volume = new SimpleStringProperty();
    @NotNull
    private final Offer offer;
    private final String offerId;

    public OfferListItem(@NotNull Offer offer)
    {
        this.offer = offer;

        this.date.set(BitSquareFormatter.formatDateTime(offer.getCreationDate()));
        this.price.set(BitSquareFormatter.formatPrice(offer.getPrice()));

        double amountAsBtcDouble = BtcFormatter.satoshiToBTC(offer.getAmount());
        double minAmountAsBtcDouble = BtcFormatter.satoshiToBTC(offer.getMinAmount());
        this.amount.set(BitSquareFormatter.formatAmountWithMinAmount(amountAsBtcDouble, minAmountAsBtcDouble));

        this.volume.set(BitSquareFormatter.formatVolumeWithMinVolume(offer.getVolume(), offer.getMinVolume()));
        this.offerId = offer.getId();
    }

    @NotNull
    public Offer getOffer()
    {
        return offer;
    }

    // called form table columns

    @NotNull
    public final StringProperty dateProperty()
    {
        return this.date;
    }

    @NotNull
    public final StringProperty priceProperty()
    {
        return this.price;
    }

    @NotNull
    public final StringProperty amountProperty()
    {
        return this.amount;
    }

    @NotNull
    public final StringProperty volumeProperty()
    {
        return this.volume;
    }

    public String getOfferId()
    {
        return offerId;
    }
}
