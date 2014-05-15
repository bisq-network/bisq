package io.bitsquare.trade;

import io.bitsquare.bank.BankAccountType;
import io.bitsquare.btc.BtcFormatter;
import io.bitsquare.user.Arbitrator;

import java.io.Serializable;
import java.math.BigInteger;
import java.util.Currency;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public class Offer implements Serializable
{
    private static final long serialVersionUID = -971164804305475826L;

    // key attributes for lookup
    private Direction direction;
    private Currency currency;

    private String uid;

    private double price;
    private BigInteger amount;
    private BigInteger minAmount;
    private String messagePubKeyAsHex;
    private BankAccountType.BankAccountTypeEnum bankAccountTypeEnum;
    private Locale bankAccountCountryLocale;

    private int collateral;
    private List<Locale> acceptedCountryLocales;
    private List<Locale> acceptedLanguageLocales;
    private String offerFeePaymentTxID;
    private String bankAccountUID;
    private Arbitrator arbitrator;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    public Offer(String messagePubKeyAsHex,
                 Direction direction,
                 double price,
                 BigInteger amount,
                 BigInteger minAmount,
                 BankAccountType.BankAccountTypeEnum bankAccountTypeEnum,
                 Currency currency,
                 Locale bankAccountCountryLocale,
                 String bankAccountUID,
                 Arbitrator arbitrator,
                 int collateral,
                 List<Locale> acceptedCountryLocales,
                 List<Locale> acceptedLanguageLocales)
    {
        this.messagePubKeyAsHex = messagePubKeyAsHex;
        this.direction = direction;
        this.price = price;
        this.amount = amount;
        this.minAmount = minAmount;
        this.bankAccountTypeEnum = bankAccountTypeEnum;
        this.currency = currency;
        this.bankAccountCountryLocale = bankAccountCountryLocale;
        this.bankAccountUID = bankAccountUID;
        this.arbitrator = arbitrator;
        this.collateral = collateral;
        this.acceptedCountryLocales = acceptedCountryLocales;
        this.acceptedLanguageLocales = acceptedLanguageLocales;

        this.uid = UUID.randomUUID().toString();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Setters
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void setOfferFeePaymentTxID(String offerFeePaymentTxID)
    {
        this.offerFeePaymentTxID = offerFeePaymentTxID;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Getters
    ///////////////////////////////////////////////////////////////////////////////////////////

    public String getMessagePubKeyAsHex()
    {
        return messagePubKeyAsHex;
    }

    public String getUid()
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

    @Override
    public String toString()
    {
        return "Offer{" +
                "direction=" + direction +
                ", currency=" + currency +
                ", uid='" + uid + '\'' +
                ", price=" + price +
                ", amount=" + amount +
                ", minAmount=" + minAmount +
                ", messagePubKey=" + messagePubKeyAsHex.hashCode() +
                ", bankAccountTypeEnum=" + bankAccountTypeEnum +
                ", bankAccountCountryLocale=" + bankAccountCountryLocale +
                ", collateral=" + collateral +
                ", acceptedCountryLocales=" + acceptedCountryLocales +
                ", acceptedLanguageLocales=" + acceptedLanguageLocales +
                ", offerFeePaymentTxID='" + offerFeePaymentTxID + '\'' +
                ", bankAccountUID='" + bankAccountUID + '\'' +
                ", arbitrator=" + arbitrator +
                '}';
    }
}
