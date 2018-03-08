package io.bisq.api.model;

import org.hibernate.validator.constraints.NotEmpty;

public class TakeOffer {

    @NotEmpty
    public String paymentAccountId;

    //    TODO this should be a number
    @NotEmpty
    public String amount;

    public TakeOffer() {
    }

    public TakeOffer(String paymentAccountId, String amount) {
        this.paymentAccountId = paymentAccountId;
        this.amount = amount;
    }
}
