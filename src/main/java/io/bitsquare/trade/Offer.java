package io.bitsquare.trade;

import io.bitsquare.bank.BankAccountType;
import io.bitsquare.user.Arbitrator;

import java.math.BigInteger;
import java.util.Currency;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public class Offer
{
    private UUID uid;
    private double price;
    private BigInteger amount;
    private BigInteger minAmount;
    private String accountID;
    private String messageID;
    private Direction direction;
    private BankAccountType.BankAccountTypeEnum bankAccountTypeEnum;
    private Currency currency;
    private Locale bankAccountCountryLocale;

    private double collateral;
    private List<Locale> acceptedCountryLocales;
    private List<Locale> acceptedLanguageLocales;
    private String offerPaymentTxID;
    private Arbitrator arbitrator;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    public Offer(String accountID,
                 String messageID,
                 Direction direction,
                 double price,
                 BigInteger amount,
                 BigInteger minAmount,
                 BankAccountType.BankAccountTypeEnum bankAccountTypeEnum,
                 Currency currency,
                 Locale bankAccountCountryLocale,
                 Arbitrator arbitrator,
                 double collateral,
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
        this.arbitrator = arbitrator;
        this.collateral = collateral;
        this.acceptedCountryLocales = acceptedCountryLocales;
        this.acceptedLanguageLocales = acceptedLanguageLocales;

        uid = UUID.randomUUID();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Setters
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void setOfferPaymentTxID(String offerPaymentTxID)
    {
        this.offerPaymentTxID = offerPaymentTxID;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Getters
    ///////////////////////////////////////////////////////////////////////////////////////////

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

    public BigInteger getAmount()
    {
        return amount;
    }

    public BigInteger getMinAmount()
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
        return price * amount.doubleValue();
    }

    public double getMinVolume()
    {
        return price * minAmount.doubleValue();
    }

    public String getOfferPaymentTxID()
    {
        return offerPaymentTxID;
    }

    public Arbitrator getArbitrator()
    {
        return arbitrator;
    }

    public double getCollateral()
    {
        return collateral;
    }
}
