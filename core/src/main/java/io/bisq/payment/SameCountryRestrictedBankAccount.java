package io.bisq.payment;

public interface SameCountryRestrictedBankAccount extends BankAccount {
    String getCountryCode();
}
