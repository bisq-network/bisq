package io.bisq.api.model;

import io.bisq.core.offer.OfferPayload;
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
    public OfferPayload.Direction direction;

    @NotNull
    public PriceType priceType;

    @NotEmpty
    public String marketPair;

    @Min(0)
    public Double percentageFromMarketPrice;

    @Min(0)
    public long fixedPrice;

    @Min(0)
    public BigDecimal amount;

    @Min(0)
    public BigDecimal minAmount;

}
