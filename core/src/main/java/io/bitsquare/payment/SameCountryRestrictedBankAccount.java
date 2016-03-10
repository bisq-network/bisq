package io.bitsquare.payment;

public interface SameCountryRestrictedBankAccount extends BankAccount {
    String getCountryCode();
}
