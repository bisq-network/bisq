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

import org.bitcoinj.core.Coin;
import org.bitcoinj.utils.ExchangeRate;
import org.bitcoinj.utils.Fiat;

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

    // Fiat cause problems with offer removal (don` found out why, but we want plain objects anyway)
    private final long fiatPrice;
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
                 long fiatPrice,
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
        this.fiatPrice = fiatPrice;
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
        return Fiat.valueOf(currency.getCurrencyCode(), fiatPrice);
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

    public Direction getMirroredDirection() {
        return direction == Direction.BUY ? Direction.SELL : Direction.BUY;
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

    public Fiat getVolumeByAmount(Coin amount) {
        if (fiatPrice != 0 && amount != null && !amount.isZero())
            return new ExchangeRate(Fiat.valueOf(currency.getCurrencyCode(), fiatPrice)).coinToFiat(amount);
        else
            return null;
    }

    public Fiat getOfferVolume() {
        return getVolumeByAmount(amount);
    }

    public Fiat getMinOfferVolume() {
        return getVolumeByAmount(minAmount);
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

    public Coin getCollateralAmount() {
        return amount.multiply(collateral).divide(1000L);
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
                ", fiatPrice=" + fiatPrice +
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
