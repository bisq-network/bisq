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

    // TODO just for mock yet
    public BankAccount(BankAccountType bankAccountType)
    {
        this.bankAccountType = bankAccountType;

    }

    public BankAccount(BankAccountType bankAccountType, String accountPrimaryID, String accountSecondaryID, String accountHolderName, Locale countryLocale, Currency currency)
    {
        this.bankAccountType = bankAccountType;
        this.accountPrimaryID = accountPrimaryID;
        this.accountSecondaryID = accountSecondaryID;
        this.accountHolderName = accountHolderName;
        this.countryLocale = countryLocale;
        this.currency = currency;

        uid = bankAccountType + "_" + accountPrimaryID + "_" + accountSecondaryID + "_" + accountHolderName + "_" + countryLocale.getISO3Country();
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

    // Changes of that structure must be reflected in VERSION updates
    public String getStringifiedBankAccount()
    {
        return "{" +
                "type=" + bankAccountType +
                ", primaryID='" + accountPrimaryID + '\'' +
                ", secondaryID='" + accountSecondaryID + '\'' +
                ", holderName='" + accountHolderName + '\'' +
                ", currency='" + currency.getCurrencyCode() + '\'' +
                ", country='" + countryLocale.getISO3Country() + '\'' +
                ", v='" + VERSION + '\'' +
                '}';
    }

    public String getShortName()
    {
        return bankAccountType + " " + accountPrimaryID + " / " + accountSecondaryID + " / " + currency.getCurrencyCode();
    }


}
