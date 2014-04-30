package io.bitsquare.bank;

import java.io.Serializable;
import java.util.Currency;
import java.util.Locale;

public class BankAccount implements Serializable
{

    private static final long serialVersionUID = 1792577576443221268L;
    private static final long VERSION = 1;

    private BankAccountType bankAccountType;
    private String accountPrimaryID;
    private String accountSecondaryID;
    private String accountHolderName;
    private Locale countryLocale;
    private Currency currency;
    private String uid;


    private String accountTitle;

    public BankAccount(BankAccountType bankAccountType,
                       Currency currency,
                       Locale countryLocale,
                       String accountTitle,
                       String accountHolderName,
                       String accountPrimaryID,
                       String accountSecondaryID)
    {
        this.bankAccountType = bankAccountType;
        this.currency = currency;
        this.countryLocale = countryLocale;
        this.accountTitle = accountTitle;
        this.accountHolderName = accountHolderName;
        this.accountPrimaryID = accountPrimaryID;
        this.accountSecondaryID = accountSecondaryID;

        uid = bankAccountType + "_" + accountPrimaryID + "_" + accountSecondaryID + "_" + accountHolderName + "_" + countryLocale.getCountry();
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

    public Locale getCountryLocale()
    {
        return countryLocale;
    }

    public String getUid()
    {
        return uid;
    }

    public String getAccountTitle()
    {
        return accountTitle;
    }

    // Changes of that structure must be reflected in VERSION updates
    public String getStringifiedBankAccount()
    {
        return "{" +
                "type=" + bankAccountType +
                ", primaryID='" + accountPrimaryID + '\'' +
                ", secondaryID='" + accountSecondaryID + '\'' +
                ", holderName='" + accountHolderName + '\'' +
                ", currency='" + currency.getCurrencyCode() + '\'' +
                ", country='" + countryLocale.getCountry() + '\'' +
                ", v='" + VERSION + '\'' +
                '}';
    }

}
