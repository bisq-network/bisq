package io.bisq.core.payment;

import javax.annotation.Nullable;

public interface BankAccount {
    @Nullable
    String getBankId();
}
