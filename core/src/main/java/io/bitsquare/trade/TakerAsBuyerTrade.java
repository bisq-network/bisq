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
import io.bitsquare.trade.protocol.trade.buyer.taker.BuyerAsTakerProtocol;

import org.bitcoinj.core.Coin;

import java.io.IOException;
import java.io.Serializable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TakerAsBuyerTrade extends Trade implements Serializable {
    // That object is saved to disc. We need to take care of changes to not break deserialization.
    private static final long serialVersionUID = 1L;

    transient private static final Logger log = LoggerFactory.getLogger(TakerAsBuyerTrade.class);


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Enum
    ///////////////////////////////////////////////////////////////////////////////////////////

    public enum LifeCycleState implements Trade.LifeCycleState {
        PENDING,
        COMPLETED,
        FAILED
    }

    public enum ProcessState implements Trade.ProcessState {
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
    // Constructor, initialization
    ///////////////////////////////////////////////////////////////////////////////////////////

    public TakerAsBuyerTrade(Offer offer, Coin tradeAmount, Peer tradingPeer,
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
        processState = ProcessState.UNDEFINED;
        lifeCycleState = TakerAsBuyerTrade.LifeCycleState.PENDING;
        initStateProperties();
    }

    @Override
    public void createProtocol() {
        protocol = new BuyerAsTakerProtocol(this);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // API
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void takeAvailableOffer() {
        assert protocol instanceof BuyerAsTakerProtocol;
        ((BuyerAsTakerProtocol) protocol).takeAvailableOffer();
    }

    public void onFiatPaymentStarted() {
        assert protocol instanceof BuyerAsTakerProtocol;
        ((BuyerAsTakerProtocol) protocol).onFiatPaymentStarted();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Setter for Mutable objects
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void setProcessState(Trade.ProcessState processState) {
        super.setProcessState(processState);

        switch ((ProcessState) processState) {
            case EXCEPTION:
                disposeProtocol();
                setLifeCycleState(TakerAsBuyerTrade.LifeCycleState.FAILED);
                break;
        }
    }

    @Override
    public void setLifeCycleState(Trade.LifeCycleState lifeCycleState) {
        super.setLifeCycleState(lifeCycleState);

        switch ((LifeCycleState) lifeCycleState) {
            case FAILED:
                disposeProtocol();
                break;
            case COMPLETED:
                disposeProtocol();
                break;
        }
    }

    @Override
    public void setThrowable(Throwable throwable) {
        super.setThrowable(throwable);
        setProcessState(ProcessState.EXCEPTION);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Protected
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    protected void handleConfidenceResult() {
        if (((ProcessState) processState).ordinal() < ProcessState.DEPOSIT_CONFIRMED.ordinal())
            setProcessState(ProcessState.DEPOSIT_CONFIRMED);
    }
}
