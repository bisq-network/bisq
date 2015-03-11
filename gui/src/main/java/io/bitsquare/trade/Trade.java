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

import io.bitsquare.offer.Offer;
import io.bitsquare.util.tasks.Task;

import org.bitcoinj.core.Coin;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.utils.Fiat;

import java.io.Serializable;

import java.util.Date;

import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;

public class Trade implements Serializable {
    private static final long serialVersionUID = -8275323072940974077L;
    private Class<? extends Task> previousTask;
    private Class<? extends Task> currentTask;

    public void setCurrentTask(Class<? extends Task> currentTask) {
        this.currentTask = currentTask;
    }

    public Class<? extends Task> getCurrentTask() {
        return currentTask;
    }

    public void setPreviousTask(Class<? extends Task> previousTask) {
        this.previousTask = previousTask;
    }

    public Class<? extends Task> getPreviousTask() {
        return previousTask;
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Enum
    ///////////////////////////////////////////////////////////////////////////////////////////

    public static enum State {
        OPEN,
        OFFERER_ACCEPTED,
        OFFERER_REJECTED, /* For taker only*/
        DEPOSIT_PUBLISHED,
        DEPOSIT_CONFIRMED,
        FIAT_PAYMENT_STARTED,
        FIAT_PAYMENT_RECEIVED,
        PAYOUT_PUBLISHED,
        FAILED
    }

    private final Offer offer;
    private final Date date;
    private String takeOfferFeeTxID;
    private Contract contract;
    private String contractAsJson;
    private String takerContractSignature;
    private Transaction depositTx;
    private Transaction payoutTx;

    private Coin tradeAmount;
    private State state;
    private Throwable fault;

    // For changing values we use properties to get binding support in the UI (table)
    // When serialized those transient properties are not instantiated, so we instantiate them in the getters at first
    // access. Only use the accessor not the private field.
    transient private ObjectProperty<Coin> _tradeAmount;
    transient private ObjectProperty<Fiat> _tradeVolume;
    transient private ObjectProperty<State> _state;
    transient private ObjectProperty<Throwable> _fault;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    public Trade(Offer offer) {
        this.offer = offer;
        date = new Date();

        state = State.OPEN;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Setters
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void setTakerContractSignature(String takerSignature) {
        this.takerContractSignature = takerSignature;
    }

    public void setTakeOfferFeeTxID(String takeOfferFeeTxID) {
        this.takeOfferFeeTxID = takeOfferFeeTxID;
    }

    public Coin getTradeAmount() {
        return tradeAmount;
    }

    public void setTradeAmount(Coin tradeAmount) {
        this.tradeAmount = tradeAmount;
        tradeAmountProperty().set(tradeAmount);
        tradeVolumeProperty().set(getTradeVolume());
    }

    public Contract getContract() {
        return contract;
    }

    public void setContractAsJson(String contractAsJson) {
        this.contractAsJson = contractAsJson;
    }

    public void setContract(Contract contract) {
        this.contract = contract;
    }

    public void setDepositTx(Transaction tx) {
        this.depositTx = tx;
    }

    public void setPayoutTx(Transaction tx) {
        this.payoutTx = tx;
    }

    public void setState(State state) {
        this.state = state;
        stateProperty().set(state);
    }

    public void setFault(Throwable fault) {
        this.fault = fault;
        faultProperty().set(fault);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Getters
    ///////////////////////////////////////////////////////////////////////////////////////////

    public Fiat getTradeVolume() {
        return offer.getVolumeByAmount(tradeAmount);
    }

    public String getTakerContractSignature() {
        return takerContractSignature;
    }

    public Transaction getDepositTx() {
        return depositTx;
    }

    public Transaction getPayoutTx() {
        return payoutTx;
    }

    public State getState() {
        return state;
    }

    public Throwable getFault() {
        return fault;
    }

    public Coin getSecurityDeposit() {
        return offer.getSecurityDeposit();
    }

    public String getId() {
        return offer.getId();
    }

    public Offer getOffer() {
        return offer;
    }

    public String getTakeOfferFeeTxId() {
        return takeOfferFeeTxID;
    }

    public String getContractAsJson() {
        return contractAsJson;
    }

    public Date getDate() {
        return date;
    }

    // When serialized those transient properties are not instantiated, so we need to instantiate them at first access
    public ObjectProperty<Coin> tradeAmountProperty() {
        if (_tradeAmount == null)
            _tradeAmount = new SimpleObjectProperty<>();

        return _tradeAmount;
    }

    public ObjectProperty<Fiat> tradeVolumeProperty() {
        if (_tradeVolume == null)
            _tradeVolume = new SimpleObjectProperty<>();

        return _tradeVolume;
    }

    public ObjectProperty<State> stateProperty() {
        if (_state == null)
            _state = new SimpleObjectProperty<>(state);

        return _state;
    }

    public ObjectProperty<Throwable> faultProperty() {
        if (_fault == null)
            _fault = new SimpleObjectProperty<>(fault);
        return _fault;
    }
}
