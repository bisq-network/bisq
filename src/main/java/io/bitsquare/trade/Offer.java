package io.bitsquare.trade;

import io.bitsquare.bank.BankAccountType;
import io.bitsquare.btc.BtcFormatter;
import io.bitsquare.locale.Country;
import io.bitsquare.user.Arbitrator;
import java.io.Serializable;
import java.math.BigInteger;
import java.util.*;
import org.jetbrains.annotations.NotNull;

public class Offer implements Serializable
{
    private static final long serialVersionUID = -971164804305475826L;

    // key attributes for lookup
    private final Direction direction;
    private final Currency currency;

    private final String id;
    @NotNull
    private final Date creationDate;

    private final double price;
    private final BigInteger amount;
    private final BigInteger minAmount;
    private final String messagePubKeyAsHex;
    private final BankAccountType bankAccountType;
    private final Country bankAccountCountry;

    private final int collateral;
    private final List<Country> acceptedCountries;
    private final List<Locale> acceptedLanguageLocales;
    private final String bankAccountUID;
    private final Arbitrator arbitrator;
    private String offerFeePaymentTxID;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    public Offer(String messagePubKeyAsHex,
                 Direction direction,
                 double price,
                 BigInteger amount,
                 BigInteger minAmount,
                 BankAccountType bankAccountType,
                 Currency currency,
                 Country bankAccountCountry,
                 String bankAccountUID,
                 Arbitrator arbitrator,
                 int collateral,
                 List<Country> acceptedCountries,
                 List<Locale> acceptedLanguageLocales)
    {
        this.messagePubKeyAsHex = messagePubKeyAsHex;
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

    public String getMessagePubKeyAsHex()
    {
        return messagePubKeyAsHex;
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
        return price * BtcFormatter.satoshiToBTC(amount);
    }

    public double getMinVolume()
    {
        return price * BtcFormatter.satoshiToBTC(minAmount);
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

    public int getCollateral()
    {
        return collateral;
    }

    public String getBankAccountUID()
    {
        return bankAccountUID;
    }

    @NotNull
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
                ", messagePubKey=" + messagePubKeyAsHex.hashCode() +
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

    @NotNull
    public Date getCreationDate()
    {
        return creationDate;
    }
}
