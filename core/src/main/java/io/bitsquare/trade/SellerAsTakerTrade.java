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
import io.bitsquare.trade.protocol.trade.seller.taker.SellerAsTakerProtocol;
import io.bitsquare.trade.states.TakerState;
import io.bitsquare.trade.states.TradeState;

import org.bitcoinj.core.Coin;

import java.io.IOException;
import java.io.Serializable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SellerAsTakerTrade extends Trade implements Serializable {
    // That object is saved to disc. We need to take care of changes to not break deserialization.
    private static final long serialVersionUID = 1L;

    transient private static final Logger log = LoggerFactory.getLogger(TakerState.class);


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor, initialization
    ///////////////////////////////////////////////////////////////////////////////////////////

    public SellerAsTakerTrade(Offer offer, Coin tradeAmount, Peer tradingPeer,
                              Storage<? extends TradeList> storage) {
        super(offer, tradeAmount, tradingPeer, storage);
        log.trace("Created by constructor");
    }

    private void readObject(java.io.ObjectInputStream in) throws IOException, ClassNotFoundException {
        in.defaultReadObject();
        log.trace("Created from serialized form.");

        initStateProperties();
        initAmountProperty();
    }

    @Override
    protected void initStates() {
        processState = TakerState.ProcessState.UNDEFINED;
        lifeCycleState = TakerState.LifeCycleState.PENDING;
        initStateProperties();
    }

    @Override
    public void createProtocol() {
        tradeProtocol = new SellerAsTakerProtocol(this);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // API
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void takeAvailableOffer() {
        assert tradeProtocol instanceof SellerAsTakerProtocol;
        ((SellerAsTakerProtocol) tradeProtocol).takeAvailableOffer();
    }

    public void onFiatPaymentReceived() {
        assert tradeProtocol instanceof SellerAsTakerProtocol;
        ((SellerAsTakerProtocol) tradeProtocol).onFiatPaymentReceived();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Setter for Mutable objects
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void setProcessState(TradeState.ProcessState processState) {
        TakerState.ProcessState state = (TakerState.ProcessState) processState;
        this.processState = processState;
        processStateProperty.set(processState);

        switch (state) {
            case EXCEPTION:
                disposeProtocol();
                setLifeCycleState(TakerState.LifeCycleState.FAILED);
                break;
        }
    }

    @Override
    public void setLifeCycleState(TradeState.LifeCycleState lifeCycleState) {
        TakerState.LifeCycleState state = (TakerState.LifeCycleState) lifeCycleState;
        switch (state) {
            case FAILED:
                disposeProtocol();
                break;
            case COMPLETED:
                disposeProtocol();
                break;
        }
        this.lifeCycleState = lifeCycleState;
        lifeCycleStateProperty.set(lifeCycleState);
    }

    @Override
    public void setThrowable(Throwable throwable) {
        super.setThrowable(throwable);
        setProcessState(TakerState.ProcessState.EXCEPTION);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Protected
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    protected void handleConfidenceResult() {
        if (((TakerState.ProcessState) processState).ordinal() < TakerState.ProcessState.DEPOSIT_CONFIRMED.ordinal())
            setProcessState(TakerState.ProcessState.DEPOSIT_CONFIRMED);
    }
}
