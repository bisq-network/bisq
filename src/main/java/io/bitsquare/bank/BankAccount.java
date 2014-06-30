package io.bitsquare.bank;

import io.bitsquare.locale.Country;
import java.io.Serializable;
import java.util.Currency;
import java.util.Objects;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class BankAccount implements Serializable
{
    private static final long serialVersionUID = 1792577576443221268L;

    @NotNull
    private final BankAccountType bankAccountType;
    @NotNull
    private final String accountPrimaryID;
    @NotNull
    private final String accountSecondaryID;
    @NotNull
    private final String accountHolderName;
    @NotNull
    private final Country country;
    @NotNull
    private final Currency currency;
    @NotNull
    private final String uid;
    @NotNull
    private final String accountTitle;

    public BankAccount(@NotNull BankAccountType bankAccountType,
                       @NotNull Currency currency,
                       @NotNull Country country,
                       @NotNull String accountTitle,
                       @NotNull String accountHolderName,
                       @NotNull String accountPrimaryID,
                       @NotNull String accountSecondaryID)
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

    public boolean equals(@Nullable Object obj)
    {
        if (!(obj instanceof BankAccount))
            return false;
        if (obj == this)
            return true;

        final BankAccount other = (BankAccount) obj;
        return uid.equals(other.getUid());
    }

    @NotNull
    public String getAccountPrimaryID()
    {
        return accountPrimaryID;
    }

    @NotNull
    public String getAccountSecondaryID()
    {
        return accountSecondaryID;
    }

    @NotNull
    public String getAccountHolderName()
    {
        return accountHolderName;
    }

    @NotNull
    public BankAccountType getBankAccountType()
    {
        return bankAccountType;
    }

    @NotNull
    public Currency getCurrency()
    {
        return currency;
    }

    @NotNull
    public Country getCountry()
    {
        return country;
    }

    @NotNull
    public String getUid()
    {
        return uid;
    }

    @NotNull
    public String getAccountTitle()
    {
        return accountTitle;
    }

    @NotNull
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
