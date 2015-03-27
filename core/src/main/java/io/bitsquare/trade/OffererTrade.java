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

import io.bitsquare.btc.BlockChainService;
import io.bitsquare.btc.TradeWalletService;
import io.bitsquare.btc.WalletService;
import io.bitsquare.crypto.SignatureService;
import io.bitsquare.offer.Offer;
import io.bitsquare.p2p.MailboxService;
import io.bitsquare.p2p.MessageService;
import io.bitsquare.p2p.Peer;
import io.bitsquare.storage.Storage;
import io.bitsquare.trade.protocol.trade.offerer.OffererProtocol;
import io.bitsquare.trade.protocol.trade.offerer.models.OffererTradeProcessModel;

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
        OFFER_OPEN,
        OFFER_RESERVED,
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


    private Coin tradeAmount;
    private Peer tradingPeer;
    private OffererProcessState processState;
    private OffererLifeCycleState lifeCycleState;
    private OffererTradeProcessModel processModel;

    transient private ObjectProperty<OffererProcessState> processStateProperty = new SimpleObjectProperty<>();
    transient private ObjectProperty<OffererLifeCycleState> lifeCycleStateProperty = new SimpleObjectProperty<>();


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    public OffererTrade(Offer offer, OffererTradeProcessModel processModel, Storage storage) {
        super(offer, storage);

        this.processModel = processModel;
        protocol = new OffererProtocol(this);
        setLifeCycleState(OffererTrade.OffererLifeCycleState.OFFER_OPEN);
    }

    // Serialized object does not create our transient objects
    private void readObject(java.io.ObjectInputStream in) throws IOException, ClassNotFoundException {
        in.defaultReadObject();

        processStateProperty = new SimpleObjectProperty<>(processState);
        lifeCycleStateProperty = new SimpleObjectProperty<>(lifeCycleState);
        tradeAmountProperty = new SimpleObjectProperty<>();
        tradeVolumeProperty = new SimpleObjectProperty<>();
        if (tradeAmount != null) {
            tradeAmountProperty.set(tradeAmount);
            tradeVolumeProperty.set(getTradeVolume());
        }
    }

    public void onFiatPaymentStarted() {
        ((OffererProtocol) protocol).onFiatPaymentStarted();
    }

    public OffererTradeProcessModel getProcessModel() {
        return processModel;
    }

    public void reActivate(MessageService messageService,
                           MailboxService mailboxService,
                           WalletService walletService,
                           TradeWalletService tradeWalletService,
                           BlockChainService blockChainService,
                           SignatureService signatureService) {

        processModel.messageService = messageService;
        processModel.mailboxService = mailboxService;
        processModel.walletService = walletService;
        processModel.tradeWalletService = tradeWalletService;
        processModel.blockChainService = blockChainService;
        processModel.signatureService = signatureService;

        processModel.offerer.registrationKeyPair = walletService.getRegistrationAddressEntry().getKeyPair();
        processModel.offerer.addressEntry = walletService.getAddressEntry(getId());

        protocol = new OffererProtocol(this);

        if (mailboxMessage != null)
            protocol.setMailboxMessage(mailboxMessage);
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

    public void setTradeAmount(Coin tradeAmount) {
        this.tradeAmount = tradeAmount;
        tradeAmountProperty.set(tradeAmount);
        tradeVolumeProperty.set(getTradeVolume());
    }

    public void setTradingPeer(Peer tradingPeer) {
        this.tradingPeer = tradingPeer;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Getters
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public ReadOnlyObjectProperty<OffererProcessState> processStateProperty() {
        return processStateProperty;
    }

    @Override
    public ReadOnlyObjectProperty<OffererLifeCycleState> lifeCycleStateProperty() {
        return lifeCycleStateProperty;
    }

    @Override
    public Coin getTradeAmount() {
        return tradeAmount;
    }

    @Override
    public Fiat getTradeVolume() {
        return offer.getVolumeByAmount(tradeAmount);
    }

    @Override
    public Peer getTradingPeer() {
        return tradingPeer;
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
