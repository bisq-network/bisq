package io.bisq.api.model;

import io.bisq.core.offer.OfferPayload;

import java.math.BigDecimal;

public class OfferToCreate {

    public String accountId;
    public OfferPayload.Direction direction;
    public PriceType priceType;
    public String marketPair;
    public Double percentage_from_market_price;
    public Long fixedPrice;
    public BigDecimal amount;
    public BigDecimal minAmount;

}
