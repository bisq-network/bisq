package io.bisq.gui.main.offer.offerbook;

import com.natpryce.makeiteasy.Instantiator;
import com.natpryce.makeiteasy.Maker;
import com.natpryce.makeiteasy.Property;
import io.bisq.core.offer.OfferMaker;

import static com.natpryce.makeiteasy.MakeItEasy.a;
import static com.natpryce.makeiteasy.MakeItEasy.make;
import static com.natpryce.makeiteasy.MakeItEasy.with;
import static io.bisq.core.offer.OfferMaker.btcOffer;

public class OfferBookListItemMaker {

    public static final Property<OfferBookListItem, Long> price = new Property<>();
    public static final Property<OfferBookListItem, Long> volume = new Property<>();

    public static final Instantiator<OfferBookListItem> OfferBookListItem = lookup ->
            new OfferBookListItem(make(btcOffer.but(
                    with(OfferMaker.price, lookup.valueOf(price, 100000L)),
                    with(OfferMaker.volume, lookup.valueOf(volume, 100000L)))));

    public static final Maker<OfferBookListItem> btcItem = a(OfferBookListItem);
}
