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

package io.bitsquare.offer;

import io.bitsquare.arbitration.Arbitrator;
import io.bitsquare.btc.Restrictions;
import io.bitsquare.fiat.FiatAccountType;
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

import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static com.google.common.base.Preconditions.*;

public class Offer implements Serializable {
    private static final long serialVersionUID = -971164804305475826L;
    private transient static final Logger log = LoggerFactory.getLogger(Offer.class);

    public enum Direction {BUY, SELL}

    public enum State {
        UNKNOWN,
        AVAILABLE,
        RESERVED,
        REMOVED,
        OFFERER_OFFLINE,
        FAULT
    }

    // key attributes for lookup
    private final Direction direction;
    private final Currency currency;
    private final String id;
    private final Date creationDate;

    //TODO check with latest bitcoinJ version
    // Fiat cause problems with offer removal (don` found out why, but we want plain objects anyway)
    private final long fiatPrice;
    private final Coin amount;
    private final Coin minAmount;
    //TODO use hex string
    private final PublicKey p2pSigPubKey;
    private final FiatAccountType fiatAccountType;
    private final Country bankAccountCountry;

    private final Coin securityDeposit;
    private final List<Country> acceptedCountries;
    private final List<Locale> acceptedLanguageLocales;
    private final String bankAccountUID;
    private final List<Arbitrator> arbitrators;

    // Mutable property. Has to be set before offer is save in DHT as it changes the objects hash!
    private String offerFeePaymentTxID;
    private State state;

    // Those state properties are transient and only used at runtime! 
    // don't access directly as it might be null; use getStateProperty() which creates an object if not instantiated
    transient private ObjectProperty<State> stateProperty = new SimpleObjectProperty<>(State.UNKNOWN);


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    public Offer(String id,
                 PublicKey p2pSigPubKey,
                 Direction direction,
                 long fiatPrice,
                 Coin amount,
                 Coin minAmount,
                 FiatAccountType fiatAccountType,
                 Currency currency,
                 Country bankAccountCountry,
                 String bankAccountUID,
                 List<Arbitrator> arbitrators,
                 Coin securityDeposit,
                 List<Country> acceptedCountries,
                 List<Locale> acceptedLanguageLocales) {
        this.id = id;
        this.p2pSigPubKey = p2pSigPubKey;
        this.direction = direction;
        this.fiatPrice = fiatPrice;
        this.amount = amount;
        this.minAmount = minAmount;
        this.fiatAccountType = fiatAccountType;
        this.currency = currency;
        this.bankAccountCountry = bankAccountCountry;
        this.bankAccountUID = bankAccountUID;
        this.arbitrators = arbitrators;
        this.securityDeposit = securityDeposit;
        this.acceptedCountries = acceptedCountries;

        this.acceptedLanguageLocales = acceptedLanguageLocales;

        creationDate = new Date();
        setState(State.UNKNOWN);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Setters
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void setState(State state) {
        this.state = state;
        stateProperty().set(state);
    }

    public void setOfferFeePaymentTxID(String offerFeePaymentTxID) {
        this.offerFeePaymentTxID = offerFeePaymentTxID;
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

    public FiatAccountType getFiatAccountType() {
        return fiatAccountType;
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

    public List<Arbitrator> getArbitrators() {
        return arbitrators;
    }

    public Coin getSecurityDeposit() {
        return securityDeposit;
    }

    public String getBankAccountId() {
        return bankAccountUID;
    }

    public PublicKey getP2PSigPubKey() {
        return p2pSigPubKey;
    }

    public Date getCreationDate() {
        return creationDate;
    }

    public State getState() {
        return state;
    }

    public ObjectProperty<State> stateProperty() {
        if (stateProperty == null)
            stateProperty = new SimpleObjectProperty<>(state);
        return stateProperty;
    }

    public void validate() throws Exception {
        checkNotNull(getAcceptedCountries(), "AcceptedCountries is null");
        checkNotNull(getAcceptedLanguageLocales(), "AcceptedLanguageLocales is null");
        checkNotNull(getAmount(), "Amount is null");
        checkNotNull(getArbitrators(), "Arbitrator is null");
        checkNotNull(getBankAccountId(), "BankAccountId is null");
        checkNotNull(getSecurityDeposit(), "SecurityDeposit is null");
        checkNotNull(getCreationDate(), "CreationDate is null");
        checkNotNull(getCurrency(), "Currency is null");
        checkNotNull(getDirection(), "Direction is null");
        checkNotNull(getId(), "Id is null");
        checkNotNull(getP2PSigPubKey(), "p2pSigPubKey is null");
        checkNotNull(getMinAmount(), "MinAmount is null");
        checkNotNull(getPrice(), "Price is null");

        checkArgument(getMinAmount().compareTo(Restrictions.MIN_TRADE_AMOUNT) >= 0, "MinAmount is less then " + Restrictions.MIN_TRADE_AMOUNT
                .toFriendlyString());
        checkArgument(getAmount().compareTo(Restrictions.MAX_TRADE_AMOUNT) <= 0, "Amount is larger then " + Restrictions.MAX_TRADE_AMOUNT.toFriendlyString());
        checkArgument(getAmount().compareTo(getMinAmount()) >= 0, "MinAmount is larger then Amount");

        checkArgument(getSecurityDeposit().compareTo(Restrictions.MIN_SECURITY_DEPOSIT) >= 0,
                "SecurityDeposit is less then " + Restrictions.MIN_SECURITY_DEPOSIT.toFriendlyString());

        checkArgument(getPrice().isPositive(), "Price is not a positive value");
        // TODO check upper and lower bounds for fiat
    }

    @Override
    public String toString() {
        return "Offer{" +
                "id='" + id + '\'' +
                ", state=" + state +
                ", direction=" + direction +
                ", currency=" + currency +
                ", creationDate=" + creationDate +
                ", fiatPrice=" + fiatPrice +
                ", amount=" + amount +
                ", minAmount=" + minAmount +
                ", fiatAccountType=" + fiatAccountType +
                ", bankAccountCountry=" + bankAccountCountry +
                ", securityDeposit=" + securityDeposit +
                ", acceptedCountries=" + acceptedCountries +
                ", acceptedLanguageLocales=" + acceptedLanguageLocales +
                ", bankAccountUID='" + bankAccountUID + '\'' +
                ", arbitrators=" + arbitrators +
                ", offerFeePaymentTxID='" + offerFeePaymentTxID + '\'' +
                '}';
    }
}
