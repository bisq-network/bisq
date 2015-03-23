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
import io.bitsquare.p2p.Peer;

import org.bitcoinj.core.Coin;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.TransactionConfidence;
import org.bitcoinj.utils.Fiat;

import com.google.common.base.Throwables;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;

import java.io.IOException;
import java.io.Serializable;

import java.util.Date;

import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Trade implements Serializable {
    private static final long serialVersionUID = -8275323072940974077L;
    private static final Logger log = LoggerFactory.getLogger(Trade.class);


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Enum
    ///////////////////////////////////////////////////////////////////////////////////////////

    public static enum LifeCycleState {
        OPEN_OFFER,
        CANCELED,
        PENDING,
        COMPLETED,
        FAILED
    }

    public static enum ProcessState {
        INIT,
        TAKE_OFFER_FEE_PUBLISH_FAILED,
        TAKE_OFFER_FEE_TX_CREATED,
        DEPOSIT_PUBLISHED,
        TAKE_OFFER_FEE_PUBLISHED,
        DEPOSIT_CONFIRMED,
        FIAT_PAYMENT_STARTED,
        FIAT_PAYMENT_RECEIVED,
        PAYOUT_PUBLISHED,
        MESSAGE_SENDING_FAILED,
        FAULT;

        private String errorMessage;

        public void setErrorMessage(String errorMessage) {
            this.errorMessage = errorMessage;
        }

        public String getErrorMessage() {
            return errorMessage;
        }
    }

    private final Offer offer;
    private final Date date;
    private ProcessState processState;
    private LifeCycleState lifeCycleState;

    private Coin tradeAmount;
    private Contract contract;
    private String contractAsJson;
    private String takerContractSignature;
    private String offererContractSignature;
    private Transaction depositTx;
    private Transaction payoutTx;
    private Peer tradingPeer;
    private int depthInBlocks = 0;

    // For changing values we use properties to get binding support in the UI (table)
    // When serialized those transient properties are not instantiated, so we instantiate them in the getters at first
    // access. Only use the accessor not the private field.
    transient private ObjectProperty<Coin> _tradeAmount;
    transient private ObjectProperty<Fiat> _tradeVolume;
    transient private ObjectProperty<ProcessState> _processState;
    transient private ObjectProperty<LifeCycleState> _lifeCycleState;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    public Trade(Offer offer) {
        this.offer = offer;
        date = new Date();

        setProcessState(ProcessState.INIT);
        log.debug("Trade ");
    }

    private void writeObject(java.io.ObjectOutputStream out) throws IOException {
        log.debug("Trade writeObject");
        out.defaultWriteObject();
        log.debug("Trade writeObject");
    }

    private void readObject(java.io.ObjectInputStream in) throws IOException, ClassNotFoundException {
        log.debug("Trade readObject");
        in.defaultReadObject();
        //TODO cannot call yet as persistence need to be refactored first. cann be called only after bitcoinJ is initialized as serialized tx objects throw 
        // exceptions
        //setConfidenceListener();
        log.debug("Trade readObject");
    }

    private void setConfidenceListener() {
       // log.debug("setConfidenceListener called. depthInBlocks=" + depthInBlocks + " / depositTx != null ? " + (depositTx.toString() != null));
        if (depositTx != null && depthInBlocks == 0) {
            TransactionConfidence transactionConfidence = depositTx.getConfidence();
            ListenableFuture<TransactionConfidence> future = transactionConfidence.getDepthFuture(1);
            Futures.addCallback(future, new FutureCallback<TransactionConfidence>() {
                @Override
                public void onSuccess(TransactionConfidence result) {
                    setProcessState(Trade.ProcessState.DEPOSIT_CONFIRMED);
                }

                @Override
                public void onFailure(Throwable t) {
                    t.printStackTrace();
                    log.error(t.getMessage());
                    Throwables.propagate(t);
                }
            });
        }
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Setters
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void setTradingPeer(Peer tradingPeer) {
        this.tradingPeer = tradingPeer;
    }

    public void setTakerContractSignature(String takerSignature) {
        this.takerContractSignature = takerSignature;
    }

    public void setOffererContractSignature(String offererContractSignature) {
        this.offererContractSignature = offererContractSignature;
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
        setConfidenceListener();
    }

    public void setPayoutTx(Transaction tx) {
        this.payoutTx = tx;
    }

    public void setProcessState(ProcessState processState) {
        this.processState = processState;
        processStateProperty().set(processState);
    }

    public void setLifeCycleState(LifeCycleState lifeCycleState) {
        this.lifeCycleState = lifeCycleState;
        lifeCycleStateProperty().set(lifeCycleState);
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

    public String getOffererContractSignature() {
        return offererContractSignature;
    }

    public Transaction getDepositTx() {
        return depositTx;
    }

    public Transaction getPayoutTx() {
        return payoutTx;
    }

    public ProcessState getProcessState() {
        return processState;
    }

    public LifeCycleState getLifeCycleState() {
        return lifeCycleState;
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

    public String getContractAsJson() {
        return contractAsJson;
    }

    public Date getDate() {
        return date;
    }

    public Peer getTradingPeer() {
        return tradingPeer;
    }


    // When serialized those transient properties are not instantiated, so we need to instantiate them at first access
    public ObjectProperty<Coin> tradeAmountProperty() {
        if (_tradeAmount == null)
            _tradeAmount = new SimpleObjectProperty<>(tradeAmount);

        return _tradeAmount;
    }

    public ObjectProperty<Fiat> tradeVolumeProperty() {
        if (_tradeVolume == null)
            _tradeVolume = new SimpleObjectProperty<>(getTradeVolume());

        return _tradeVolume;
    }

    public ObjectProperty<ProcessState> processStateProperty() {
        if (_processState == null)
            _processState = new SimpleObjectProperty<>(processState);

        return _processState;
    }

    public ObjectProperty<LifeCycleState> lifeCycleStateProperty() {
        if (_lifeCycleState == null)
            _lifeCycleState = new SimpleObjectProperty<>(lifeCycleState);

        return _lifeCycleState;
    }

    @Override
    public String toString() {
        return "Trade{" +
                "offer=" + offer +
                ", date=" + date +
                ", state=" + processState +
                ", tradeAmount=" + tradeAmount +
                ", contract=" + contract +
                ", contractAsJson='" + contractAsJson + '\'' +
                ", takerContractSignature='" + takerContractSignature + '\'' +
                ", offererContractSignature='" + offererContractSignature + '\'' +
                ", depositTx=" + depositTx +
                ", payoutTx=" + payoutTx +
                '}';
    }
}
