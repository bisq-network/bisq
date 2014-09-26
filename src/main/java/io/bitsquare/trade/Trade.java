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

import com.google.bitcoin.core.Coin;
import com.google.bitcoin.core.Transaction;
import com.google.bitcoin.utils.Fiat;

import java.io.Serializable;

import java.util.Date;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleObjectProperty;

//TODO flatten down?

public class Trade implements Serializable {
    private static final long serialVersionUID = -8275323072940974077L;

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Enum
    ///////////////////////////////////////////////////////////////////////////////////////////

    public static enum State {
        OPEN,
        OFFERER_ACCEPTED,
        OFFERER_REJECTED, /* For taker only*/
        DEPOSIT_PUBLISHED,
        DEPOSIT_CONFIRMED,
        PAYMENT_STARTED,
        FAILED,
        COMPLETED
    }


    private final Offer offer;
    private final Date date;
    private String takeOfferFeeTxID;
    private Coin tradeAmount;
    private Contract contract;
    private String contractAsJson;
    private String takerSignature;
    private Transaction depositTx;
    private Transaction payoutTx;
    private State state;
    private Throwable fault;

    // When serialized those transient properties are not instantiated, so we instantiate them in the getters at first 
    // access. Only use the accessor not the private field.
    // TODO use ObjectProperties instead of BooleanProperty
    transient private BooleanProperty _payoutTxChanged;
    transient private BooleanProperty _contractChanged;
    transient private ObjectProperty<Transaction> _depositTx;
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

    public void setContractTakerSignature(String takerSignature) {
        this.takerSignature = takerSignature;
    }

    public void setTakeOfferFeeTxID(String takeOfferFeeTxID) {
        this.takeOfferFeeTxID = takeOfferFeeTxID;
    }

    public Coin getTradeAmount() {
        return tradeAmount;
    }

    public void setTradeAmount(Coin tradeAmount) {
        this.tradeAmount = tradeAmount;
    }

    public Contract getContract() {
        return contract;
    }

    public void setContractAsJson(String contractAsJson) {
        this.contractAsJson = contractAsJson;
    }

    public void setContract(Contract contract) {
        this.contract = contract;
        contractChangedProperty().set(!contractChangedProperty().get());
    }

    public void setDepositTx(Transaction tx) {
        this.depositTx = tx;
        depositTxProperty().set(tx);
    }

    public void setPayoutTx(Transaction tx) {
        this.payoutTx = tx;
        payoutTxChangedProperty().set(!payoutTxChangedProperty().get());
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

    public String getTakerSignature() {
        return takerSignature;
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

    public Coin getCollateralAmount() {
        return tradeAmount.multiply(offer.getCollateral()).divide(1000L);
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
    public ObjectProperty<Transaction> depositTxProperty() {
        if (_depositTx == null)
            _depositTx = new SimpleObjectProperty<>(depositTx);

        return _depositTx;
    }

    public BooleanProperty contractChangedProperty() {
        if (_contractChanged == null)
            _contractChanged = new SimpleBooleanProperty();

        return _contractChanged;
    }

    public BooleanProperty payoutTxChangedProperty() {
        if (_payoutTxChanged == null)
            _payoutTxChanged = new SimpleBooleanProperty();

        return _payoutTxChanged;
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
