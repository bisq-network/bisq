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
import io.bitsquare.trade.protocol.trade.buyer.offerer.BuyerAsOffererProtocol;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.Serializable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class OffererAsBuyerTrade extends OffererTrade implements Serializable {
    // That object is saved to disc. We need to take care of changes to not break deserialization.
    private static final long serialVersionUID = 1L;

    transient private static final Logger log = LoggerFactory.getLogger(OffererAsBuyerTrade.class);


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Enum
    ///////////////////////////////////////////////////////////////////////////////////////////

    public enum LifeCycleState implements OffererTrade.LifeCycleState {
        OFFER_OPEN,
        OFFER_RESERVED,
        OFFER_CANCELED,
        PENDING,
        COMPLETED,
        FAILED
    }

    public enum ProcessState implements OffererTrade.ProcessState {
        UNDEFINED,
        DEPOSIT_PUBLISHED,
        DEPOSIT_CONFIRMED,

        FIAT_PAYMENT_STARTED,

        PAYOUT_PUBLISHED,

        MESSAGE_SENDING_FAILED,
        EXCEPTION
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor, initialization
    ///////////////////////////////////////////////////////////////////////////////////////////

    public OffererAsBuyerTrade(Offer offer, Storage<? extends TradeList> storage) {
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
        protocol = new BuyerAsOffererProtocol(this);
    }

    @Override
    protected void initStates() {
        processState = ProcessState.UNDEFINED;
        lifeCycleState = LifeCycleState.OFFER_OPEN;
        initStateProperties();
    }
    
    
    ///////////////////////////////////////////////////////////////////////////////////////////
    // Fiat
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void onFiatPaymentStarted() {
        assert protocol instanceof BuyerAsOffererProtocol;
        ((BuyerAsOffererProtocol) protocol).onFiatPaymentStarted();
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
                setLifeCycleState(LifeCycleState.FAILED);
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
