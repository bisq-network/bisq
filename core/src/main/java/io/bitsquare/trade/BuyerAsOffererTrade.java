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
import io.bitsquare.storage.Storage;
import io.bitsquare.trade.protocol.trade.buyer.BuyerAsOffererProtocol;
import io.bitsquare.trade.states.OffererState;
import io.bitsquare.trade.states.TradeState;

import org.bitcoinj.core.Coin;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.Serializable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BuyerAsOffererTrade extends Trade implements OffererTrade, BuyerTrade, Serializable {
    // That object is saved to disc. We need to take care of changes to not break deserialization.
    private static final long serialVersionUID = 1L;

    transient private static final Logger log = LoggerFactory.getLogger(BuyerAsOffererTrade.class);


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor, initialization
    ///////////////////////////////////////////////////////////////////////////////////////////

    public BuyerAsOffererTrade(Offer offer, Storage<? extends TradeList> storage) {
        super(offer, storage);
        log.trace("Created by constructor");
    }

    private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
        in.defaultReadObject();
        log.trace("Created from serialized form.");

        initStateProperties();
        initAmountProperty();
    }

    @Override
    protected void createProtocol() {
        tradeProtocol = new BuyerAsOffererProtocol(this);
    }

    @Override
    protected void initStates() {
        processState = OffererState.ProcessState.UNDEFINED;
        lifeCycleState = OffererState.LifeCycleState.OFFER_OPEN;
        initStateProperties();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // API
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void onFiatPaymentStarted() {
        assert tradeProtocol instanceof BuyerAsOffererProtocol;
        ((BuyerAsOffererProtocol) tradeProtocol).onFiatPaymentStarted();
    }

    @Override
    public Coin getPayoutAmount() {
        return getSecurityDeposit().add(getTradeAmount());
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Setter for Mutable objects
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void setProcessState(TradeState.ProcessState processState) {
        super.setProcessState(processState);

        switch ((OffererState.ProcessState) processState) {
            case EXCEPTION:
                disposeProtocol();
                setLifeCycleState(OffererState.LifeCycleState.FAILED);
                break;
        }
    }

    @Override
    public void setLifeCycleState(TradeState.LifeCycleState lifeCycleState) {
        super.setLifeCycleState(lifeCycleState);

        switch ((OffererState.LifeCycleState) lifeCycleState) {
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

        setProcessState(OffererState.ProcessState.EXCEPTION);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Protected
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    protected void handleConfidenceResult() {
        if (((OffererState.ProcessState) processState).ordinal() < OffererState.ProcessState.DEPOSIT_CONFIRMED.ordinal())
            setProcessState(OffererState.ProcessState.DEPOSIT_CONFIRMED);
    }
}
