package bisq.httpapi.model;

import javax.annotation.Nullable;



import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;
import org.hibernate.validator.constraints.NotEmpty;

public class TakeOffer {

    @NotNull
    @Min(1)
    public long amount;

    @Nullable
    public Long maxFundsForTrade;

    @NotNull
    @NotEmpty
    public String paymentAccountId;


    public TakeOffer() {
    }

    public TakeOffer(String paymentAccountId, long amount) {
        this(paymentAccountId, amount, null);
    }

    public TakeOffer(String paymentAccountId, long amount, @Nullable Long maxFundsForTrade) {
        this.amount = amount;
        this.maxFundsForTrade = maxFundsForTrade;
        this.paymentAccountId = paymentAccountId;
    }

}
