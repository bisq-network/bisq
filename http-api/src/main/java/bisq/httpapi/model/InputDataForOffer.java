package bisq.httpapi.model;

import bisq.core.offer.OfferPayload;

import java.math.BigDecimal;



import bisq.httpapi.model.validation.StringEnumeration;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;
import org.hibernate.validator.constraints.NotEmpty;

public class InputDataForOffer {

    // If funds are taken from Bisq wallet or funded from external wallet
    // Atm it has to be set to true as we don't support the other option atm.
    public boolean fundUsingBisqWallet;

    //TODO @bernard what is offerId for? Is is intended for edit offer?
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
