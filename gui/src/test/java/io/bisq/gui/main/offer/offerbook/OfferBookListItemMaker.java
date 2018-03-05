package io.bisq.gui.main.offer.offerbook;

import com.natpryce.makeiteasy.Instantiator;
import com.natpryce.makeiteasy.Maker;
import com.natpryce.makeiteasy.Property;
import io.bisq.core.offer.OfferMaker;
import io.bisq.core.offer.OfferPayload;

import static com.natpryce.makeiteasy.MakeItEasy.a;
import static com.natpryce.makeiteasy.MakeItEasy.make;
import static com.natpryce.makeiteasy.MakeItEasy.with;
import static io.bisq.core.offer.OfferMaker.btcUsdOffer;

public class OfferBookListItemMaker {

    public static final Property<OfferBookListItem, Long> price = new Property<>();
    public static final Property<OfferBookListItem, Long> amount = new Property<>();
    public static final Property<OfferBookListItem, Long> minAmount = new Property<>();
    public static final Property<OfferBookListItem, OfferPayload.Direction> direction = new Property<>();
    public static final Property<OfferBookListItem, Boolean> useMarketBasedPrice = new Property<>();

    public static final Instantiator<OfferBookListItem> OfferBookListItem = lookup ->
            new OfferBookListItem(make(btcUsdOffer.but(
                    with(OfferMaker.price, lookup.valueOf(price, 100000L)),
                    with(OfferMaker.amount, lookup.valueOf(amount, 100000L)),
                    with(OfferMaker.minAmount, lookup.valueOf(amount, 100000L)),
                    with(OfferMaker.direction, lookup.valueOf(direction, OfferPayload.Direction.BUY)),
                    with(OfferMaker.useMarketBasedPrice, lookup.valueOf(useMarketBasedPrice, false)))));

    public static final Instantiator<OfferBookListItem> OfferBookListItemWithRange = lookup ->
            new OfferBookListItem(make(btcUsdOffer.but(
                    with(OfferMaker.price, lookup.valueOf(price, 100000L)),
                    with(OfferMaker.minAmount, lookup.valueOf(minAmount, 100000L)),
                    with(OfferMaker.amount, lookup.valueOf(amount, 200000L)))));

    public static final Maker<OfferBookListItem> btcItem = a(OfferBookListItem);
    public static final Maker<OfferBookListItem> btcSellItem = a(OfferBookListItem, with(direction, OfferPayload.Direction.SELL));

    public static final Maker<OfferBookListItem> btcItemWithRange = a(OfferBookListItemWithRange);
}
