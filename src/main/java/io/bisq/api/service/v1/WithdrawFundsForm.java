package io.bisq.api.service.v1;

import io.bisq.api.model.validation.NotNullItems;
import org.hibernate.validator.constraints.NotEmpty;

import java.util.List;

public class WithdrawFundsForm {

    public long amount;

    public boolean feeExcluded;

    @NotNullItems
    @NotEmpty
    public List<String> sourceAddresses;

    @NotEmpty
    public String targetAddress;
}
