package network.bisq.api.model;

import org.hibernate.validator.constraints.NotEmpty;

import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;

public class TakeOffer {

    @NotEmpty
    public String paymentAccountId;

    @NotNull
    @Min(1)
    public long amount;

    public TakeOffer() {
    }

    public TakeOffer(String paymentAccountId, long amount) {
        this.paymentAccountId = paymentAccountId;
        this.amount = amount;
    }
}
