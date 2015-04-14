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

package io.bitsquare.trade.offer;

import io.bitsquare.btc.Restrictions;
import io.bitsquare.common.handlers.ResultHandler;
import io.bitsquare.crypto.KeyRing;
import io.bitsquare.crypto.PubKeyRing;
import io.bitsquare.fiat.FiatAccount;
import io.bitsquare.locale.Country;
import io.bitsquare.trade.protocol.availability.OfferAvailabilityModel;
import io.bitsquare.trade.protocol.availability.OfferAvailabilityProtocol;

import org.bitcoinj.core.Coin;
import org.bitcoinj.utils.ExchangeRate;
import org.bitcoinj.utils.Fiat;

import java.io.IOException;
import java.io.Serializable;

import java.util.Date;
import java.util.List;

import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;

import org.jetbrains.annotations.NotNull;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static com.google.common.base.Preconditions.*;

public class Offer implements Serializable {
    // That object is sent over the wire, so we need to take care of version compatibility.
    private static final long serialVersionUID = 1L;
    transient private static final Logger log = LoggerFactory.getLogger(Offer.class);

    public enum Direction {BUY, SELL}

    public enum State {
        UNDEFINED,
        AVAILABLE,
        NOT_AVAILABLE,
        REMOVED,
        OFFERER_OFFLINE
    }


    // key attributes for lookup
    private final String id;
    private final Direction direction;
    private final String currencyCode;
    private final Date creationDate;

    //TODO check with latest bitcoinJ version
    // Fiat cause problems with offer removal (don` found out why, but we want plain objects anyway)
    private final long fiatPrice;
    private final Coin amount;
    private final Coin minAmount;
    private final PubKeyRing pubKeyRing;
    private final FiatAccount.Type fiatAccountType;
    private final Country bankAccountCountry;

    private final Coin securityDeposit;
    private final List<Country> acceptedCountries;
    private final List<String> acceptedLanguageCodes;
    private final String bankAccountUID;
    private final List<String> arbitratorIds;

    // Mutable property. Has to be set before offer is save in DHT as it changes the objects hash!
    private String offerFeePaymentTxID;
    private State state = State.UNDEFINED;

    // Those state properties are transient and only used at runtime! 
    // don't access directly as it might be null; use getStateProperty() which creates an object if not instantiated
    transient private ObjectProperty<State> stateProperty = new SimpleObjectProperty<>(state);
    transient private OfferAvailabilityProtocol availabilityProtocol;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    public Offer(String id,
                 PubKeyRing pubKeyRing,
                 Direction direction,
                 long fiatPrice,
                 Coin amount,
                 Coin minAmount,
                 FiatAccount.Type fiatAccountType,
                 String currencyCode,
                 Country bankAccountCountry,
                 String bankAccountUID,
                 List<String> arbitratorIds,
                 Coin securityDeposit,
                 List<Country> acceptedCountries,
                 List<String> acceptedLanguageCodes) {
        this.id = id;
        this.pubKeyRing = pubKeyRing;
        this.direction = direction;
        this.fiatPrice = fiatPrice;
        this.amount = amount;
        this.minAmount = minAmount;
        this.fiatAccountType = fiatAccountType;
        this.currencyCode = currencyCode;
        this.bankAccountCountry = bankAccountCountry;
        this.bankAccountUID = bankAccountUID;
        this.arbitratorIds = arbitratorIds;
        this.securityDeposit = securityDeposit;
        this.acceptedCountries = acceptedCountries;

        this.acceptedLanguageCodes = acceptedLanguageCodes;

        creationDate = new Date();
        setState(State.UNDEFINED);
    }

    private void readObject(java.io.ObjectInputStream in) throws IOException, ClassNotFoundException {
        in.defaultReadObject();
        stateProperty = new SimpleObjectProperty<>(state);
    }

    public void validate() {
        checkNotNull(getAcceptedCountries(), "AcceptedCountries is null");
        checkNotNull(getAcceptedLanguageCodes(), "AcceptedLanguageLocales is null");
        checkNotNull(getAmount(), "Amount is null");
        checkNotNull(getArbitratorIds(), "Arbitrator is null");
        checkNotNull(getBankAccountId(), "BankAccountId is null");
        checkNotNull(getSecurityDeposit(), "SecurityDeposit is null");
        checkNotNull(getCreationDate(), "CreationDate is null");
        checkNotNull(getCurrencyCode(), "Currency is null");
        checkNotNull(getDirection(), "Direction is null");
        checkNotNull(getId(), "Id is null");
        checkNotNull(getPubKeyRing(), "pubKeyRing is null");
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

    public boolean isMyOffer(KeyRing keyRing) {
        return getPubKeyRing().getHashString().equals(keyRing.getPubKeyRing().getHashString());
    }

    public Fiat getVolumeByAmount(Coin amount) {
        if (fiatPrice != 0 && amount != null && !amount.isZero())
            return new ExchangeRate(Fiat.valueOf(currencyCode, fiatPrice)).coinToFiat(amount);
        else
            return null;
    }

    public Fiat getOfferVolume() {
        return getVolumeByAmount(amount);
    }

    public Fiat getMinOfferVolume() {
        return getVolumeByAmount(minAmount);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Availability
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void checkOfferAvailability(OfferAvailabilityModel model) {
        availabilityProtocol = new OfferAvailabilityProtocol(model,
                () -> cancelAvailabilityRequest(),
                (errorMessage) -> cancelAvailabilityRequest());
        availabilityProtocol.checkOfferAvailability();
    }


    public void checkOfferAvailability(OfferAvailabilityModel model, ResultHandler resultHandler) {
        availabilityProtocol = new OfferAvailabilityProtocol(model,
                () -> {
                    cancelAvailabilityRequest();
                    resultHandler.handleResult();
                },
                (errorMessage) -> availabilityProtocol.cancel());
        availabilityProtocol.checkOfferAvailability();
    }


    public void cancelAvailabilityRequest() {
        availabilityProtocol.cancel();
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

    @NotNull
    public String getId() {
        return id;
    }

    public PubKeyRing getPubKeyRing() {
        return pubKeyRing;
    }


    public Fiat getPrice() {
        return Fiat.valueOf(currencyCode, fiatPrice);
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

    public FiatAccount.Type getFiatAccountType() {
        return fiatAccountType;
    }

    public String getCurrencyCode() {
        return currencyCode;
    }

    public Country getBankAccountCountry() {
        return bankAccountCountry;
    }

    public List<Country> getAcceptedCountries() {
        return acceptedCountries;
    }

    public List<String> getAcceptedLanguageCodes() {
        return acceptedLanguageCodes;
    }

    public String getOfferFeePaymentTxID() {
        return offerFeePaymentTxID;
    }

    public List<String> getArbitratorIds() {
        return arbitratorIds;
    }

    @NotNull
    public Coin getSecurityDeposit() {
        return securityDeposit;
    }

    public String getBankAccountId() {
        return bankAccountUID;
    }

    public Date getCreationDate() {
        return creationDate;
    }

    public State getState() {
        return state;
    }

    public ObjectProperty<State> stateProperty() {
        return stateProperty;
    }

    @Override
    public String toString() {
        return "Offer{" +
                "id='" + id + '\'' +
                ", direction=" + direction +
                ", currencyCode='" + currencyCode + '\'' +
                ", creationDate=" + creationDate +
                ", fiatPrice=" + fiatPrice +
                ", amount=" + amount +
                ", minAmount=" + minAmount +
               /* ", pubKeyRing=" + pubKeyRing +*/
                ", fiatAccountType=" + fiatAccountType +
                ", bankAccountCountry=" + bankAccountCountry +
                ", securityDeposit=" + securityDeposit +
                ", acceptedCountries=" + acceptedCountries +
                ", acceptedLanguageCodes=" + acceptedLanguageCodes +
                ", bankAccountUID='" + bankAccountUID + '\'' +
                ", arbitratorIds=" + arbitratorIds +
                ", offerFeePaymentTxID='" + offerFeePaymentTxID + '\'' +
                ", state=" + state +
                ", stateProperty=" + stateProperty +
                '}';
    }
}
