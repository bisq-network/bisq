package io.bitsquare.bank;

import io.bitsquare.locale.Country;
import java.io.Serializable;
import java.util.Currency;
import java.util.Objects;
import javax.annotation.concurrent.Immutable;

@Immutable
public class BankAccount implements Serializable
{
    private static final long serialVersionUID = 1792577576443221268L;

    private final BankAccountType bankAccountType;
    private final String accountPrimaryID;  // like IBAN
    private final String accountSecondaryID; // like BIC
    private final String accountHolderName;
    private final Country country;     // where bank is registered
    // The main currency if account support multiple currencies.
    // The user can create multiple bank accounts with same bank account but other currency if his bank account support that.
    private final Currency currency;
    private final String accountTitle;

    public BankAccount(BankAccountType bankAccountType, Currency currency, Country country, String accountTitle, String accountHolderName, String accountPrimaryID, String accountSecondaryID)
    {
        this.bankAccountType = bankAccountType;
        this.currency = currency;
        this.country = country;
        this.accountTitle = accountTitle;
        this.accountHolderName = accountHolderName;
        this.accountPrimaryID = accountPrimaryID;
        this.accountSecondaryID = accountSecondaryID;
    }

    public int hashCode()
    {
        return Objects.hashCode(accountTitle);
    }

    public boolean equals(Object obj)
    {
        if (!(obj instanceof BankAccount)) return false;
        if (obj == this) return true;

        final BankAccount other = (BankAccount) obj;
        return accountTitle.equals(other.getUid());
    }


    public String getAccountPrimaryID()
    {
        return accountPrimaryID;
    }

    public String getAccountSecondaryID()
    {
        return accountSecondaryID;
    }

    public String getAccountHolderName()
    {
        return accountHolderName;
    }

    public BankAccountType getBankAccountType()
    {
        return bankAccountType;
    }

    public Currency getCurrency()
    {
        return currency;
    }

    public Country getCountry()
    {
        return country;
    }

    // we use the accountTitle as unique id
    public String getUid()
    {
        return accountTitle;
    }

    public String getAccountTitle()
    {
        return accountTitle;
    }

    @Override
    public String toString()
    {
        return "BankAccount{" +
                "bankAccountType=" + bankAccountType +
                ", accountPrimaryID='" + accountPrimaryID + '\'' +
                ", accountSecondaryID='" + accountSecondaryID + '\'' +
                ", accountHolderName='" + accountHolderName + '\'' +
                ", country=" + country +
                ", currency=" + currency +
                ", accountTitle='" + accountTitle + '\'' +
                '}';
    }
}
