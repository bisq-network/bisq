package io.bitsquare.trade;

import io.bitsquare.bank.BankAccountType;

import java.util.Currency;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public class Offer
{
    private UUID uid;
    private double price;
    private double amount;
    private double minAmount;


    private String accountID;
    private String messageID;
    private Direction direction;
    private BankAccountType.BankAccountTypeEnum bankAccountTypeEnum;
    private Currency currency;
    private Locale bankAccountCountryLocale;
    private List<Locale> acceptedCountryLocales;
    private List<Locale> acceptedLanguageLocales;
    private String offerPaymentTxID;

    public Offer(String accountID,
                 String messageID,
                 Direction direction,
                 double price,
                 double amount,
                 double minAmount,
                 BankAccountType.BankAccountTypeEnum bankAccountTypeEnum,
                 Currency currency,
                 Locale bankAccountCountryLocale,
                 List<Locale> acceptedCountryLocales,
                 List<Locale> acceptedLanguageLocales)
    {
        this.accountID = accountID;
        this.messageID = messageID;
        this.direction = direction;
        this.price = price;
        this.amount = amount;
        this.minAmount = minAmount;
        this.bankAccountTypeEnum = bankAccountTypeEnum;
        this.currency = currency;
        this.bankAccountCountryLocale = bankAccountCountryLocale;
        this.acceptedCountryLocales = acceptedCountryLocales;
        this.acceptedLanguageLocales = acceptedLanguageLocales;

        uid = UUID.randomUUID();
    }

    // setter
    public void setOfferPaymentTxID(String offerPaymentTxID)
    {
        this.offerPaymentTxID = offerPaymentTxID;
    }

    // getters
    public String getAccountID()
    {
        return accountID;
    }

    public String getMessageID()
    {
        return messageID;
    }

    public UUID getUid()
    {
        return uid;
    }

    public double getPrice()
    {
        return price;
    }

    public double getAmount()
    {
        return amount;
    }

    public double getMinAmount()
    {
        return minAmount;
    }

    public Direction getDirection()
    {
        return direction;
    }

    public BankAccountType.BankAccountTypeEnum getBankAccountTypeEnum()
    {
        return bankAccountTypeEnum;
    }

    public Currency getCurrency()
    {
        return currency;
    }

    public Locale getBankAccountCountryLocale()
    {
        return bankAccountCountryLocale;
    }

    public List<Locale> getAcceptedCountryLocales()
    {
        return acceptedCountryLocales;
    }

    public List<Locale> getAcceptedLanguageLocales()
    {
        return acceptedLanguageLocales;
    }

    public double getVolume()
    {
        return price * amount;
    }

    public double getMinVolume()
    {
        return price * minAmount;
    }

    public String getOfferPaymentTxID()
    {
        return offerPaymentTxID;
    }

}
