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
import io.bitsquare.trade.protocol.trade.offerer.OffererAsBuyerProtocol;

import org.bitcoinj.core.TransactionConfidence;

import com.google.common.base.Throwables;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;

import java.io.IOException;
import java.io.Serializable;

import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.SimpleObjectProperty;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class OffererTrade extends Trade implements Serializable {
    // That object is saved to disc. We need to take care of changes to not break deserialization.
    private static final long serialVersionUID = 1L;
    transient private static final Logger log = LoggerFactory.getLogger(OffererTrade.class);

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Enum
    ///////////////////////////////////////////////////////////////////////////////////////////

    public enum OffererLifeCycleState implements LifeCycleState {
        OPEN_OFFER,
        OFFER_CANCELED,
        PENDING,
        COMPLETED,
        FAILED
    }

    public enum OffererProcessState implements ProcessState {
        DEPOSIT_PUBLISHED,
        DEPOSIT_CONFIRMED,

        FIAT_PAYMENT_STARTED,

        PAYOUT_PUBLISHED,

        MESSAGE_SENDING_FAILED,
        EXCEPTION
    }

    protected OffererProcessState processState;
    protected OffererLifeCycleState lifeCycleState;

    transient protected ObjectProperty<OffererProcessState> processStateProperty = new SimpleObjectProperty<>(processState);
    transient protected ObjectProperty<OffererLifeCycleState> lifeCycleStateProperty = new SimpleObjectProperty<>(lifeCycleState);


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    public OffererTrade(Offer offer) {
        super(offer);
    }

    // Serialized object does not create our transient objects
    private void readObject(java.io.ObjectInputStream in) throws IOException, ClassNotFoundException {
        in.defaultReadObject();

        processStateProperty = new SimpleObjectProperty<>(processState);
        lifeCycleStateProperty = new SimpleObjectProperty<>(lifeCycleState);
    }

    public void onFiatPaymentStarted() {
        ((OffererAsBuyerProtocol) protocol).onFiatPaymentStarted();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Setters
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void setProcessState(OffererProcessState processState) {
        this.processState = processState;
        processStateProperty.set(processState);

        switch (processState) {
            case EXCEPTION:
                disposeProtocol();
                setLifeCycleState(OffererLifeCycleState.FAILED);
                break;
        }
    }

    public void setLifeCycleState(OffererLifeCycleState lifeCycleState) {
        switch (lifeCycleState) {
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



    ///////////////////////////////////////////////////////////////////////////////////////////
    // Getters
    ///////////////////////////////////////////////////////////////////////////////////////////

    public OffererProcessState getProcessState() {
        return processState;
    }

    public OffererLifeCycleState getLifeCycleState() {
        return lifeCycleState;
    }

    public ReadOnlyObjectProperty<OffererProcessState> processStateProperty() {
        return processStateProperty;
    }

    public ReadOnlyObjectProperty<OffererLifeCycleState> lifeCycleStateProperty() {
        return lifeCycleStateProperty;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Private
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    protected void setConfidenceListener() {
        TransactionConfidence transactionConfidence = depositTx.getConfidence();
        ListenableFuture<TransactionConfidence> future = transactionConfidence.getDepthFuture(1);
        Futures.addCallback(future, new FutureCallback<TransactionConfidence>() {
            @Override
            public void onSuccess(TransactionConfidence result) {
                if (processState.ordinal() < OffererProcessState.DEPOSIT_CONFIRMED.ordinal())
                    setProcessState(OffererProcessState.DEPOSIT_CONFIRMED);
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
