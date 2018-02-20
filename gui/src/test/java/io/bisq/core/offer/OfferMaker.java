package io.bisq.core.offer;

import com.natpryce.makeiteasy.Instantiator;
import com.natpryce.makeiteasy.Maker;
import com.natpryce.makeiteasy.Property;

import static com.natpryce.makeiteasy.MakeItEasy.a;

public class OfferMaker {

    public static final Property<Offer, Long> price = new Property<>();
    public static final Property<Offer, Long> minAmount = new Property<>();
    public static final Property<Offer, Long> amount = new Property<>();

    public static final Instantiator<Offer> Offer = lookup -> new Offer(
      new OfferPayload("",
              0L,
              null,
              null,
              OfferPayload.Direction.BUY,
              lookup.valueOf(price, 100000L),
              0,
              false,
              lookup.valueOf(amount, 100000L),
              lookup.valueOf(minAmount, 100000L),
              "BTC",
              "USD",
              null,
              null,
              "SEPA",
              "",
              null,
              null,
              null,
              null,
              null,
              "",
              0L,
              0L,
              0L,
              false,
              0L,
              0L,
              0L,
              0L,
              false,
              false,
              0L,
              0L,
              false,
              null,
              null,
              0));

    public static final Maker<Offer> btcOffer = a(Offer);
}
