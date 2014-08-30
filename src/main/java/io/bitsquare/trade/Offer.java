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
import com.google.bitcoin.utils.ExchangeRate;
import com.google.bitcoin.utils.Fiat;

import java.io.Serializable;

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

    private final Fiat price;
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
    private final List<Arbitrator> arbitrators;
    private String offerFeePaymentTxID;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    public Offer(String id,
                 PublicKey messagePublicKey,
                 Direction direction,
                 Fiat price,
                 Coin amount,
                 Coin minAmount,
                 BankAccountType bankAccountType,
                 Currency currency,
                 Country bankAccountCountry,
                 String bankAccountUID,
                 List<Arbitrator> arbitrators,
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
        this.arbitrators = arbitrators;
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

    public Fiat getPrice() {
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

    public Fiat getVolumeForCoin(Coin coin) {
        if (price != null && coin != null && !coin.isZero() && !price.isZero()) {
            return new ExchangeRate(price).coinToFiat(coin);
        }
        else
            return null;
    }

    public Fiat getOfferVolume() {
        return getVolumeForCoin(amount);
    }

    public Fiat getMinOfferVolume() {
        return getVolumeForCoin(minAmount);
    }

    public String getOfferFeePaymentTxID() {
        return offerFeePaymentTxID;
    }

    public void setOfferFeePaymentTxID(String offerFeePaymentTxID) {
        this.offerFeePaymentTxID = offerFeePaymentTxID;
    }

    public List<Arbitrator> getArbitrators() {
        return arbitrators;
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
                ", arbitrator=" + arbitrators +
                '}';
    }


    public Date getCreationDate() {
        return creationDate;
    }
}
