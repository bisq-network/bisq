package io.bitsquare.bank;

import io.bitsquare.locale.Country;
import java.io.Serializable;
import java.util.Currency;
import java.util.Objects;

public class BankAccount implements Serializable
{
    private static final long serialVersionUID = 1792577576443221268L;


    private final BankAccountType bankAccountType;

    private final String accountPrimaryID;

    private final String accountSecondaryID;

    private final String accountHolderName;

    private final Country country;

    private final Currency currency;

    private final String uid;

    private final String accountTitle;

    public BankAccount(BankAccountType bankAccountType,
                       Currency currency,
                       Country country,
                       String accountTitle,
                       String accountHolderName,
                       String accountPrimaryID,
                       String accountSecondaryID)
    {
        this.bankAccountType = bankAccountType;
        this.currency = currency;
        this.country = country;
        this.accountTitle = accountTitle;
        this.accountHolderName = accountHolderName;
        this.accountPrimaryID = accountPrimaryID;
        this.accountSecondaryID = accountSecondaryID;

        uid = accountTitle;
    }

    public int hashCode()
    {
        return Objects.hashCode(uid);
    }

    public boolean equals(Object obj)
    {
        if (!(obj instanceof BankAccount))
            return false;
        if (obj == this)
            return true;

        final BankAccount other = (BankAccount) obj;
        return uid.equals(other.getUid());
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


    public String getUid()
    {
        return uid;
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
                ", countryLocale=" + country +
                ", currency=" + currency +
                ", uid='" + uid + '\'' +
                ", accountTitle='" + accountTitle + '\'' +
                '}';
    }

}
