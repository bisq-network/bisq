package io.bitsquare.trade;

import com.google.bitcoin.core.Coin;
import io.bitsquare.bank.BankAccountType;
import io.bitsquare.locale.Country;
import io.bitsquare.user.Arbitrator;
import java.io.Serializable;
import java.security.PublicKey;
import java.util.*;

public class Offer implements Serializable
{
    private static final long serialVersionUID = -971164804305475826L;

    // key attributes for lookup
    private final Direction direction;
    private final Currency currency;

    private final String id;

    private final Date creationDate;

    private final double price;
    private final Coin amount;
    private final Coin minAmount;
    private final PublicKey messagePublicKey;
    private final BankAccountType bankAccountType;
    private final Country bankAccountCountry;

    private final double collateral;
    private final List<Country> acceptedCountries;
    private final List<Locale> acceptedLanguageLocales;
    private final String bankAccountUID;
    private final Arbitrator arbitrator;
    private String offerFeePaymentTxID;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    public Offer(PublicKey messagePublicKey,
                 Direction direction,
                 double price,
                 Coin amount,
                 Coin minAmount,
                 BankAccountType bankAccountType,
                 Currency currency,
                 Country bankAccountCountry,
                 String bankAccountUID,
                 Arbitrator arbitrator,
                 double collateral,
                 List<Country> acceptedCountries,
                 List<Locale> acceptedLanguageLocales)
    {
        this.messagePublicKey = messagePublicKey;
        this.direction = direction;
        this.price = price;
        this.amount = amount;
        this.minAmount = minAmount;
        this.bankAccountType = bankAccountType;
        this.currency = currency;
        this.bankAccountCountry = bankAccountCountry;
        this.bankAccountUID = bankAccountUID;
        this.arbitrator = arbitrator;
        this.collateral = collateral;
        this.acceptedCountries = acceptedCountries;

        this.acceptedLanguageLocales = acceptedLanguageLocales;

        creationDate = new Date();
        id = UUID.randomUUID().toString();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Setters
    ///////////////////////////////////////////////////////////////////////////////////////////

    public PublicKey getMessagePublicKey()
    {
        return messagePublicKey;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Getters
    ///////////////////////////////////////////////////////////////////////////////////////////

    public String getId()
    {
        return id;
    }

    public double getPrice()
    {
        return price;
    }

    public Coin getAmount()
    {
        return amount;
    }

    public Coin getMinAmount()
    {
        return minAmount;
    }

    public Direction getDirection()
    {
        return direction;
    }

    public BankAccountType getBankAccountType()
    {
        return bankAccountType;
    }

    public Currency getCurrency()
    {
        return currency;
    }

    public Country getBankAccountCountry()
    {
        return bankAccountCountry;
    }

    public List<Country> getAcceptedCountries()
    {
        return acceptedCountries;
    }

    public List<Locale> getAcceptedLanguageLocales()
    {
        return acceptedLanguageLocales;
    }

    public double getVolume()
    {
        return price * amount.value;
    }

    public double getMinVolume()
    {
        return price * minAmount.value;
    }

    public String getOfferFeePaymentTxID()
    {
        return offerFeePaymentTxID;
    }

    public void setOfferFeePaymentTxID(String offerFeePaymentTxID)
    {
        this.offerFeePaymentTxID = offerFeePaymentTxID;
    }

    public Arbitrator getArbitrator()
    {
        return arbitrator;
    }

    public double getCollateral()
    {
        return collateral;
    }

    public String getBankAccountId()
    {
        return bankAccountUID;
    }


    @Override
    public String toString()
    {
        return "Offer{" +
                "direction=" + direction +
                ", currency=" + currency +
                ", uid='" + id + '\'' +
                ", price=" + price +
                ", amount=" + amount +
                ", minAmount=" + minAmount +
                ", messagePubKey=" + messagePublicKey.hashCode() +
                ", bankAccountTypeEnum=" + bankAccountType +
                ", bankAccountCountryLocale=" + bankAccountCountry +
                ", collateral=" + collateral +
                ", acceptedCountryLocales=" + acceptedCountries +
                ", acceptedLanguageLocales=" + acceptedLanguageLocales +
                ", offerFeePaymentTxID='" + offerFeePaymentTxID + '\'' +
                ", bankAccountUID='" + bankAccountUID + '\'' +
                ", arbitrator=" + arbitrator +
                '}';
    }


    public Date getCreationDate()
    {
        return creationDate;
    }
}
