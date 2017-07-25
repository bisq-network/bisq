package io.bisq.api.model;

import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Set;

/**
 * Created by mike on 04/09/16.
 */
public class AccountList {
    public Set<Account> accounts;

    @JsonValue
    public Set<Account> getPaymentAccounts() {
        return accounts;
    }
}
