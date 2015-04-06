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

import io.bitsquare.arbitration.ArbitrationRepository;
import io.bitsquare.btc.BlockChainService;
import io.bitsquare.btc.TradeWalletService;
import io.bitsquare.btc.WalletService;
import io.bitsquare.common.taskrunner.Model;
import io.bitsquare.crypto.SignatureService;
import io.bitsquare.offer.Offer;
import io.bitsquare.p2p.MailboxMessage;
import io.bitsquare.p2p.MessageService;
import io.bitsquare.p2p.Peer;
import io.bitsquare.storage.Storage;
import io.bitsquare.trade.protocol.trade.ProcessModel;
import io.bitsquare.trade.protocol.trade.TradeProtocol;
import io.bitsquare.trade.states.TradeState;
import io.bitsquare.user.User;

import org.bitcoinj.core.Coin;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.TransactionConfidence;
import org.bitcoinj.utils.Fiat;

import com.google.common.base.Throwables;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.Serializable;

import java.util.Date;

import javax.annotation.Nullable;

import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.SimpleObjectProperty;

import org.jetbrains.annotations.NotNull;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Holds all data which are relevant to the trade, but not those which are only needed in the trade process as shared data between tasks. Those data are
 * stored in the task model.
 */
abstract public class Trade implements Model, Serializable {
    // That object is saved to disc. We need to take care of changes to not break deserialization.
    private static final long serialVersionUID = 1L;

    private transient static final Logger log = LoggerFactory.getLogger(Trade.class);

    // Mutable
    private Coin tradeAmount;
    private Peer tradingPeer;
    private transient ObjectProperty<Coin> tradeAmountProperty;
    private transient ObjectProperty<Fiat> tradeVolumeProperty;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Fields
    ///////////////////////////////////////////////////////////////////////////////////////////

    // Transient/Immutable
    private transient ObjectProperty<TradeState.ProcessState> processStateProperty;
    private transient ObjectProperty<TradeState.LifeCycleState> lifeCycleStateProperty;
    // Trades are saved in the TradeList
    transient private Storage<? extends TradeList> storage;
    transient protected TradeProtocol tradeProtocol;

    // Immutable
    private final Offer offer;
    private final ProcessModel processModel;
    private final Date creationDate;

    // Mutable
    private Date takeOfferDate;
    protected TradeState.ProcessState processState;
    protected TradeState.LifeCycleState lifeCycleState;
    private MailboxMessage mailboxMessage;
    private Transaction depositTx;
    private Contract contract;
    private String contractAsJson;
    private String sellerContractSignature;
    private String buyerContractSignature;
    private Transaction payoutTx;

    // Transient/Mutable
    transient private String errorMessage;
    transient private Throwable throwable;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor, initialization
    ///////////////////////////////////////////////////////////////////////////////////////////

    protected Trade(Offer offer, Storage<? extends TradeList> storage) {
        log.trace("Created by constructor");
        this.offer = offer;
        this.storage = storage;

        processModel = new ProcessModel();
        tradeVolumeProperty = new SimpleObjectProperty<>();
        tradeAmountProperty = new SimpleObjectProperty<>();

        initStates();
        initStateProperties();

        // That will only be used in case of a canceled open offer trade
        creationDate = new Date();
    }

    // taker
    protected Trade(Offer offer, Coin tradeAmount, Peer tradingPeer,
                    Storage<? extends TradeList> storage) {

        this(offer, storage);
        this.tradeAmount = tradeAmount;
        this.tradingPeer = tradingPeer;
        tradeAmountProperty.set(tradeAmount);
        tradeVolumeProperty.set(getTradeVolume());
    }

    private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
        in.defaultReadObject();
        log.trace("Created from serialized form.");

        initStateProperties();
        initAmountProperty();
    }

    public void init(MessageService messageService,
                     WalletService walletService,
                     TradeWalletService tradeWalletService,
                     BlockChainService blockChainService,
                     SignatureService signatureService,
                     ArbitrationRepository arbitrationRepository,
                     User user) {

        processModel.onAllServicesInitialized(offer,
                messageService,
                walletService,
                tradeWalletService,
                blockChainService,
                signatureService,
                arbitrationRepository,
                user);

        createProtocol();

        if (mailboxMessage != null) {
            tradeProtocol.applyMailboxMessage(mailboxMessage, this);
            // After applied to protocol we remove it
            mailboxMessage = null;
        }
        tradeProtocol.checkPayoutTxTimeLock(this);
    }

    protected void initStateProperties() {
        processStateProperty = new SimpleObjectProperty<>(processState);
        lifeCycleStateProperty = new SimpleObjectProperty<>(lifeCycleState);
    }

    protected void initAmountProperty() {
        tradeAmountProperty = new SimpleObjectProperty<>();
        tradeVolumeProperty = new SimpleObjectProperty<>();

        if (tradeAmount != null) {
            tradeAmountProperty.set(tradeAmount);
            tradeVolumeProperty.set(getTradeVolume());
        }
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // API
    ///////////////////////////////////////////////////////////////////////////////////////////

    // The deserialized tx has not actual confidence data, so we need to get the fresh one from the wallet.
    public void updateDepositTxFromWallet(TradeWalletService tradeWalletService) {
        if (depositTx != null)
            setDepositTx(tradeWalletService.getWalletTx(depositTx));
    }

    public void setDepositTx(Transaction tx) {
        this.depositTx = tx;
        setupConfidenceListener();
        storage.queueUpForSave();
    }

    public void disposeProtocol() {
        if (tradeProtocol != null) {
            tradeProtocol.cleanup();
            tradeProtocol = null;
        }
    }

    public void setMailboxMessage(MailboxMessage mailboxMessage) {
        this.mailboxMessage = mailboxMessage;
    }

    public void setStorage(Storage<? extends TradeList> storage) {
        this.storage = storage;
    }

    public void setProcessState(TradeState.ProcessState processState) {
        this.processState = processState;
        processStateProperty.set(processState);
        storage.queueUpForSave();
    }

    public void setLifeCycleState(TradeState.LifeCycleState lifeCycleState) {
        this.lifeCycleState = lifeCycleState;
        lifeCycleStateProperty.set(lifeCycleState);
        storage.queueUpForSave();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Storage
    ///////////////////////////////////////////////////////////////////////////////////////////

    // Get called from taskRunner after each completed task
    @Override
    public void persist() {
        storage.queueUpForSave();
    }

    @Override
    public void onComplete() {
        storage.queueUpForSave();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Getter only
    ///////////////////////////////////////////////////////////////////////////////////////////

    public String getId() {
        return offer.getId();
    }

    public Offer getOffer() {
        return offer;
    }

    @Nullable
    public Transaction getDepositTx() {
        return depositTx;
    }

    @NotNull
    public Coin getSecurityDeposit() {
        return offer.getSecurityDeposit();
    }

    public Coin getPayoutAmount() {
        return getSecurityDeposit();
    }

    public ProcessModel getProcessModel() {
        return processModel;
    }

    @Nullable
    public Fiat getTradeVolume() {
        if (tradeAmount != null)
            return offer.getVolumeByAmount(tradeAmount);
        else
            return null;
    }

    public ReadOnlyObjectProperty<? extends TradeState.ProcessState> processStateProperty() {
        return processStateProperty;
    }

    public ReadOnlyObjectProperty<? extends TradeState.LifeCycleState> lifeCycleStateProperty() {
        return lifeCycleStateProperty;
    }

    public ReadOnlyObjectProperty<Coin> tradeAmountProperty() {
        return tradeAmountProperty;
    }

    public ReadOnlyObjectProperty<Fiat> tradeVolumeProperty() {
        return tradeVolumeProperty;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Getter/Setter for Mutable objects
    ///////////////////////////////////////////////////////////////////////////////////////////

    public Date getDate() {
        return takeOfferDate != null ? takeOfferDate : creationDate;
    }

    public void setTakeOfferDate(Date takeOfferDate) {
        this.takeOfferDate = takeOfferDate;
    }

    public void setTradingPeer(Peer tradingPeer) {
        this.tradingPeer = tradingPeer;
    }

    @Nullable
    public Peer getTradingPeer() {
        return tradingPeer;
    }

    public void setTradeAmount(Coin tradeAmount) {
        this.tradeAmount = tradeAmount;
        tradeAmountProperty.set(tradeAmount);
        tradeVolumeProperty.set(getTradeVolume());
    }

    // TODO support case of multiple fiat accounts
    public long getLockTimeDelta() {
        return getOffer().getFiatAccountType().lockTimeDelta;
    }

    @Nullable
    public Coin getTradeAmount() {
        return tradeAmount;
    }

    public void setSellerContractSignature(String takerSignature) {
        this.sellerContractSignature = takerSignature;
    }

    @Nullable
    public String getSellerContractSignature() {
        return sellerContractSignature;
    }

    public void setBuyerContractSignature(String buyerContractSignature) {
        this.buyerContractSignature = buyerContractSignature;
    }

    @Nullable
    public String getBuyerContractSignature() {
        return buyerContractSignature;
    }

    public void setContractAsJson(String contractAsJson) {
        this.contractAsJson = contractAsJson;
    }

    @Nullable
    public String getContractAsJson() {
        return contractAsJson;
    }

    public void setContract(Contract contract) {
        this.contract = contract;
    }

    @Nullable
    public Contract getContract() {
        return contract;
    }

    public void setPayoutTx(Transaction payoutTx) {
        this.payoutTx = payoutTx;
    }

    // Not used now, but will be used in some reporting UI
    @Nullable
    public Transaction getPayoutTx() {
        return payoutTx;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    @Nullable
    public String getErrorMessage() {
        return errorMessage;
    }

    public void setThrowable(Throwable throwable) {
        this.throwable = throwable;
    }

    @Nullable
    public Throwable getThrowable() {
        return throwable;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Private
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void setupConfidenceListener() {
        if (depositTx != null) {
            TransactionConfidence transactionConfidence = depositTx.getConfidence();
            ListenableFuture<TransactionConfidence> future = transactionConfidence.getDepthFuture(1);
            Futures.addCallback(future, new FutureCallback<TransactionConfidence>() {
                @Override
                public void onSuccess(TransactionConfidence result) {
                    handleConfidenceResult();
                }

                @Override
                public void onFailure(@NotNull Throwable t) {
                    t.printStackTrace();
                    log.error(t.getMessage());
                    Throwables.propagate(t);
                }
            });
        }
    }

    abstract protected void createProtocol();

    abstract protected void handleConfidenceResult();

    abstract protected void initStates();

    @Override
    public String toString() {
        return "Trade{" +
                "tradeAmount=" + tradeAmount +
                ", tradingPeer=" + tradingPeer +
                ", tradeAmountProperty=" + tradeAmountProperty +
                ", tradeVolumeProperty=" + tradeVolumeProperty +
                ", processStateProperty=" + processStateProperty +
                ", lifeCycleStateProperty=" + lifeCycleStateProperty +
                ", storage=" + storage +
                ", tradeProtocol=" + tradeProtocol +
                ", offer=" + offer +
                ", date=" + takeOfferDate +
                ", processModel=" + processModel +
                ", processState=" + processState +
                ", lifeCycleState=" + lifeCycleState +
                ", mailboxMessage=" + mailboxMessage +
                ", depositTx=" + depositTx +
               /* ", contract=" + contract +
                ", contractAsJson='" + contractAsJson + '\'' +*/
              /*  ", sellerContractSignature='" + sellerContractSignature + '\'' +
                ", buyerContractSignature='" + buyerContractSignature + '\'' +*/
                ", payoutTx=" + payoutTx +
                ", errorMessage='" + errorMessage + '\'' +
                ", throwable=" + throwable +
                '}';
    }
}