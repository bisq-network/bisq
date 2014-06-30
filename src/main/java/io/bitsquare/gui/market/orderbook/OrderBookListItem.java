package io.bitsquare.gui.market.orderbook;

import io.bitsquare.btc.BtcFormatter;
import io.bitsquare.gui.util.BitSquareFormatter;
import io.bitsquare.trade.Offer;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import org.jetbrains.annotations.NotNull;

public class OrderBookListItem
{
    private final StringProperty price = new SimpleStringProperty();
    private final StringProperty amount = new SimpleStringProperty();
    private final StringProperty volume = new SimpleStringProperty();

    @NotNull
    private final Offer offer;


    public OrderBookListItem(@NotNull Offer offer)
    {
        this.offer = offer;
        this.price.set(BitSquareFormatter.formatPrice(offer.getPrice()));

        double amountAsBtcDouble = BtcFormatter.satoshiToBTC(offer.getAmount());
        double minAmountAsBtcDouble = BtcFormatter.satoshiToBTC(offer.getMinAmount());
        this.amount.set(BitSquareFormatter.formatAmountWithMinAmount(amountAsBtcDouble, minAmountAsBtcDouble));

        this.volume.set(BitSquareFormatter.formatVolumeWithMinVolume(offer.getVolume(), offer.getMinVolume()));
    }

    @NotNull
    public Offer getOffer()
    {
        return offer;
    }

    // called form table columns
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
}
