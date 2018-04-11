package io.bisq.api.model;

import io.bisq.api.model.validation.StringEnumeration;
import bisq.core.offer.OfferPayload;
import org.hibernate.validator.constraints.NotEmpty;

import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;
import java.math.BigDecimal;

public class OfferToCreate {

    public boolean fundUsingBisqWallet;

    public String offerId;

    @NotEmpty
    public String accountId;

    @NotNull
    @StringEnumeration(enumClass = OfferPayload.Direction.class)
    public String direction;

    @NotNull
    @StringEnumeration(enumClass = PriceType.class)
    public String priceType;

    @NotEmpty
    public String marketPair;

    public BigDecimal percentageFromMarketPrice;

    @Min(0)
    public long fixedPrice;

    @Min(1)
    public long amount;

    @Min(1)
    public long minAmount;

    @Min(1)
    public Long buyerSecurityDeposit;
}
