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
import io.bitsquare.storage.Storage;
import io.bitsquare.trade.protocol.trade.ProcessModel;
import io.bitsquare.trade.protocol.trade.taker.TakerProtocol;
import io.bitsquare.trade.protocol.trade.taker.models.TakerProcessModel;

import org.bitcoinj.core.Coin;
import org.bitcoinj.core.TransactionConfidence;
import org.bitcoinj.utils.Fiat;

import com.google.common.base.Throwables;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;

import java.io.IOException;
import java.io.Serializable;

import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.SimpleObjectProperty;

import org.jetbrains.annotations.NotNull;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TakerTrade extends Trade implements Serializable {
    // That object is saved to disc. We need to take care of changes to not break deserialization.
    private static final long serialVersionUID = 1L;

    transient private static final Logger log = LoggerFactory.getLogger(TakerTrade.class);


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Enum
    ///////////////////////////////////////////////////////////////////////////////////////////

    public enum TakerLifeCycleState implements LifeCycleState {
        PENDING,
        COMPLETED,
        FAILED
    }

    public enum TakerProcessState implements ProcessState {
        UNDEFINED,
        TAKE_OFFER_FEE_TX_CREATED,
        TAKE_OFFER_FEE_PUBLISHED,
        TAKE_OFFER_FEE_PUBLISH_FAILED,

        DEPOSIT_PUBLISHED,
        DEPOSIT_CONFIRMED,

        FIAT_PAYMENT_STARTED,

        FIAT_PAYMENT_RECEIVED,
        PAYOUT_PUBLISHED,

        MESSAGE_SENDING_FAILED,
        EXCEPTION
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Fields
    ///////////////////////////////////////////////////////////////////////////////////////////

    // Transient/Immutable
    transient private ObjectProperty<TakerProcessState> processStateProperty;
    transient private ObjectProperty<TakerLifeCycleState> lifeCycleStateProperty;

    // Immutable
    private final Coin tradeAmount;
    private final Peer tradingPeer;

    // Mutable
    private TakerProcessState processState;
    private TakerLifeCycleState lifeCycleState;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor, initialization
    ///////////////////////////////////////////////////////////////////////////////////////////

    public TakerTrade(Offer offer, Coin tradeAmount, Peer tradingPeer,
                      Storage<? extends TradeList> storage) {
        super(offer, storage);
        log.trace("Created by constructor");

        this.tradeAmount = tradeAmount;
        this.tradingPeer = tradingPeer;

        processState = TakerProcessState.UNDEFINED;
        lifeCycleState = TakerLifeCycleState.PENDING;

        processStateProperty = new SimpleObjectProperty<>(processState);
        lifeCycleStateProperty = new SimpleObjectProperty<>(lifeCycleState);

        tradeAmountProperty = new SimpleObjectProperty<>(tradeAmount);
        tradeVolumeProperty = new SimpleObjectProperty<>(getTradeVolume());
    }

    private void readObject(java.io.ObjectInputStream in) throws IOException, ClassNotFoundException {
        in.defaultReadObject();
        log.trace("Created from serialized form.");

        processStateProperty = new SimpleObjectProperty<>(processState);
        lifeCycleStateProperty = new SimpleObjectProperty<>(lifeCycleState);

        tradeAmountProperty = new SimpleObjectProperty<>(tradeAmount);
        tradeVolumeProperty = new SimpleObjectProperty<>(getTradeVolume());
    }

    @Override
    protected ProcessModel createProcessModel() {
        return new TakerProcessModel();
    }

    @Override
    public void createProtocol() {
        protocol = new TakerProtocol(this);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // API
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void takeAvailableOffer() {
        assert processModel != null;
        ((TakerProtocol) protocol).takeAvailableOffer();
    }

    public void onFiatPaymentReceived() {
        assert protocol != null;
        ((TakerProtocol) protocol).onFiatPaymentReceived();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Getter only
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public Coin getTradeAmount() {
        return tradeAmount;
    }

    @Override
    public Fiat getTradeVolume() {
        return offer.getVolumeByAmount(tradeAmount);
    }

    public Peer getTradingPeer() {
        return tradingPeer;
    }

    @Override
    public ReadOnlyObjectProperty<TakerProcessState> processStateProperty() {
        return processStateProperty;
    }

    @Override
    public ReadOnlyObjectProperty<TakerLifeCycleState> lifeCycleStateProperty() {
        return lifeCycleStateProperty;
    }

    public TakerProcessModel getProcessModel() {
        return (TakerProcessModel) processModel;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Setter for Mutable objects
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void setLifeCycleState(TakerLifeCycleState lifeCycleState) {
        this.lifeCycleState = lifeCycleState;
        lifeCycleStateProperty.set(lifeCycleState);
    }

    public void setProcessState(TakerProcessState processState) {
        this.processState = processState;
        processStateProperty.set(processState);

        if (processState == TakerProcessState.EXCEPTION) {
            setLifeCycleState(TakerLifeCycleState.FAILED);
            disposeProtocol();
        }
    }

    @Override
    public void setThrowable(Throwable throwable) {
        super.setThrowable(throwable);
        setProcessState(TakerTrade.TakerProcessState.EXCEPTION);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Protected
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    protected void setupConfidenceListener() {
        assert depositTx != null;
        TransactionConfidence transactionConfidence = depositTx.getConfidence();
        ListenableFuture<TransactionConfidence> future = transactionConfidence.getDepthFuture(1);
        Futures.addCallback(future, new FutureCallback<TransactionConfidence>() {
            @Override
            public void onSuccess(TransactionConfidence result) {
                if (processState.ordinal() < TakerProcessState.DEPOSIT_CONFIRMED.ordinal())
                    setProcessState(TakerProcessState.DEPOSIT_CONFIRMED);
            }

            @Override
            public void onFailure(@NotNull Throwable t) {
                t.printStackTrace();
                log.error(t.getMessage());
                Throwables.propagate(t);
            }
        });
    }

    @Override
    public String toString() {
        return "TakerTrade{" +
                "tradeAmount=" + tradeAmount +
                ", tradingPeer=" + tradingPeer +
                ", processState=" + processState +
                ", lifeCycleState=" + lifeCycleState +
                '}';
    }
}
