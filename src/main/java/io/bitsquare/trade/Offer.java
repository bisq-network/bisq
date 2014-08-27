/*
 * This file is part of Bitsquare.
 *
 * Bitsquare is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * Bitsquare is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Bitsquare. If not, see <http://www.gnu.org/licenses/>.
 */

package io.bitsquare.trade;

import io.bitsquare.arbitrator.Arbitrator;
import io.bitsquare.bank.BankAccountType;
import io.bitsquare.locale.Country;

import com.google.bitcoin.core.Coin;

import java.io.Serializable;

import java.math.BigDecimal;

import java.security.PublicKey;

import java.util.Currency;
import java.util.Date;
import java.util.List;
import java.util.Locale;

//TODO flatten down?

public class Offer implements Serializable {
    private static final long serialVersionUID = -971164804305475826L;

    // key attributes for lookup
    private final Direction direction;
    private final Currency currency;

    private final String id;

    private final Date creationDate;

    private final double price;
    private final Coin amount;
    private final Coin minAmount;
    //TODO use hex string
    private final PublicKey messagePublicKey;
    private final BankAccountType bankAccountType;
    private final Country bankAccountCountry;

    private final long collateral;
    private final List<Country> acceptedCountries;
    private final List<Locale> acceptedLanguageLocales;
    private final String bankAccountUID;
    private final Arbitrator arbitrator;
    private String offerFeePaymentTxID;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    public Offer(String id,
                 PublicKey messagePublicKey,
                 Direction direction,
                 double price,
                 Coin amount,
                 Coin minAmount,
                 BankAccountType bankAccountType,
                 Currency currency,
                 Country bankAccountCountry,
                 String bankAccountUID,
                 Arbitrator arbitrator,
                 long collateral,
                 List<Country> acceptedCountries,
                 List<Locale> acceptedLanguageLocales) {
        this.id = id;
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
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Setters
    ///////////////////////////////////////////////////////////////////////////////////////////

    public PublicKey getMessagePublicKey() {
        return messagePublicKey;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Getters
    ///////////////////////////////////////////////////////////////////////////////////////////

    public String getId() {
        return id;
    }

    public double getPrice() {
        return price;
    }

    public Coin getAmount() {
        return amount;
    }

    public Coin getMinAmount() {
        return minAmount;
    }

    public Direction getDirection() {
        return direction;
    }

    public BankAccountType getBankAccountType() {
        return bankAccountType;
    }

    public Currency getCurrency() {
        return currency;
    }

    public Country getBankAccountCountry() {
        return bankAccountCountry;
    }

    public List<Country> getAcceptedCountries() {
        return acceptedCountries;
    }

    public List<Locale> getAcceptedLanguageLocales() {
        return acceptedLanguageLocales;
    }

    public double getVolumeForCoin(Coin coin) {
        BigDecimal amountBD = BigDecimal.valueOf(coin.longValue());
        BigDecimal volumeBD = amountBD.multiply(BigDecimal.valueOf(price));
        return volumeBD.divide(BigDecimal.valueOf(Coin.COIN.value)).doubleValue();
    }

    public double getOfferVolume() {
        return getVolumeForCoin(amount);
    }

    public double getMinOfferVolume() {
        return getVolumeForCoin(minAmount);
    }

    public String getOfferFeePaymentTxID() {
        return offerFeePaymentTxID;
    }

    public void setOfferFeePaymentTxID(String offerFeePaymentTxID) {
        this.offerFeePaymentTxID = offerFeePaymentTxID;
    }

    public Arbitrator getArbitrator() {
        return arbitrator;
    }

    public long getCollateral() {
        return collateral;
    }

    public String getBankAccountId() {
        return bankAccountUID;
    }


    @Override
    public String toString() {
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


    public Date getCreationDate() {
        return creationDate;
    }
}
