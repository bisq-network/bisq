package io.bitsquare.bank;

import io.bitsquare.locale.Country;

import java.io.Serializable;
import java.util.Currency;
import java.util.Objects;

public class BankAccount implements Serializable
{

    private static final long serialVersionUID = 1792577576443221268L;

    private BankAccountTypeInfo bankAccountTypeInfo;
    private String accountPrimaryID;
    private String accountSecondaryID;
    private String accountHolderName;
    private Country country;
    private Currency currency;
    private String uid;


    private String accountTitle;

    public BankAccount(BankAccountTypeInfo bankAccountTypeInfo,
                       Currency currency,
                       Country country,
                       String accountTitle,
                       String accountHolderName,
                       String accountPrimaryID,
                       String accountSecondaryID)
    {
        this.bankAccountTypeInfo = bankAccountTypeInfo;
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

        BankAccount other = (BankAccount) obj;
        return other.getUid().equals(uid);
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

    public BankAccountTypeInfo getBankAccountTypeInfo()
    {
        return bankAccountTypeInfo;
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
                "bankAccountType=" + bankAccountTypeInfo.getType() +
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
