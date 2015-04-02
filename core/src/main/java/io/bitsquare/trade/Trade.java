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
import io.bitsquare.trade.protocol.Protocol;
import io.bitsquare.trade.protocol.trade.shared.models.ProcessModel;
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
abstract public class Trade extends Model implements Serializable {
    // That object is saved to disc. We need to take care of changes to not break deserialization.
    private static final long serialVersionUID = 1L;

    private transient static final Logger log = LoggerFactory.getLogger(Trade.class);
    // Mutable
    protected Coin tradeAmount;
    protected Peer tradingPeer;
    transient protected ObjectProperty<Coin> tradeAmountProperty;
    transient protected ObjectProperty<Fiat> tradeVolumeProperty;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Interfaces
    ///////////////////////////////////////////////////////////////////////////////////////////

    public interface LifeCycleState {
    }

    public interface ProcessState {
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Fields
    ///////////////////////////////////////////////////////////////////////////////////////////

    // Transient/Immutable
    transient protected ObjectProperty<ProcessState> processStateProperty;
    transient protected ObjectProperty<LifeCycleState> lifeCycleStateProperty;

    transient private Storage<? extends TradeList> storage;
    transient protected Protocol protocol;

    // Immutable
    protected final Offer offer;
    private final Date date;
    protected final ProcessModel processModel;

    // Mutable
    protected ProcessState processState;
    protected LifeCycleState lifeCycleState;
    private MailboxMessage mailboxMessage;
    protected Transaction depositTx;
    private Contract contract;
    private String contractAsJson;
    private String takerContractSignature;
    private String offererContractSignature;
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

        date = new Date();
        processModel = createProcessModel();
        tradeVolumeProperty = new SimpleObjectProperty<>();
        tradeAmountProperty = new SimpleObjectProperty<>();

        initStates();
        initStateProperties();
    }

    // taker
    protected Trade(Offer offer, Coin tradeAmount, Peer tradingPeer,
                    Storage<? extends TradeList> storage) {

        this(offer, storage);
        this.tradeAmount = tradeAmount;
        this.tradingPeer = tradingPeer;
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

        if (mailboxMessage != null)
            protocol.setMailboxMessage(mailboxMessage);
    }

    protected void initStateProperties() {
        processStateProperty = new SimpleObjectProperty<>(processState);
        lifeCycleStateProperty = new SimpleObjectProperty<>(lifeCycleState);
    }


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
        if (protocol != null) {
            protocol.cleanup();
            protocol = null;
        }
    }

    public void setMailboxMessage(MailboxMessage mailboxMessage) {
        this.mailboxMessage = mailboxMessage;
        if (protocol != null)
            protocol.setMailboxMessage(mailboxMessage);

        storage.queueUpForSave();
    }

    public void setStorage(Storage<? extends TradeList> storage) {
        this.storage = storage;
    }

    public void setProcessState(Trade.ProcessState processState) {
        this.processState = processState;
        processStateProperty.set(processState);
        storage.queueUpForSave();
    }

    public void setLifeCycleState(Trade.LifeCycleState lifeCycleState) {
        this.lifeCycleState = lifeCycleState;
        lifeCycleStateProperty.set(lifeCycleState);
        storage.queueUpForSave();
    }

    protected void setupConfidenceListener() {
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

    // taker only
    public void takeAvailableOffer() {
    }

    abstract protected void createProtocol();

    public ReadOnlyObjectProperty<Coin> tradeAmountProperty() {
        return tradeAmountProperty;
    }

    public ReadOnlyObjectProperty<Fiat> tradeVolumeProperty() {
        return tradeVolumeProperty;
    }

    protected void initAmountProperty() {
        tradeAmountProperty = new SimpleObjectProperty<>();
        tradeVolumeProperty = new SimpleObjectProperty<>();

        if (tradeAmount != null) {
            tradeAmountProperty.set(tradeAmount);
            tradeVolumeProperty.set(getTradeVolume());
        }
    }

    abstract protected void handleConfidenceResult();

    abstract protected void initStates();

    public ProcessModel createProcessModel() {
        return new ProcessModel();
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

    public Date getDate() {
        return date;
    }

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

    public Coin getSecurityDeposit() {
        return offer.getSecurityDeposit();
    }

    public ProcessModel getProcessModel() {
        return processModel;
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

    @Nullable
    public Coin getTradeAmount() {
        return tradeAmount;
    }

    @Nullable
    public Fiat getTradeVolume() {
        if (tradeAmount != null)
            return offer.getVolumeByAmount(tradeAmount);
        else
            return null;
    }

    public ReadOnlyObjectProperty<? extends ProcessState> processStateProperty() {
        return processStateProperty;
    }

    public ReadOnlyObjectProperty<? extends LifeCycleState> lifeCycleStateProperty() {
        return lifeCycleStateProperty;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Getter/Setter for Mutable objects
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void setTakerContractSignature(String takerSignature) {
        this.takerContractSignature = takerSignature;
    }

    @Nullable
    public String getTakerContractSignature() {
        return takerContractSignature;
    }

    public void setOffererContractSignature(String offererContractSignature) {
        this.offererContractSignature = offererContractSignature;
    }

    @Nullable
    public String getOffererContractSignature() {
        return offererContractSignature;
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

    @Override
    public String toString() {
        return ", protocol=" + protocol +
                ", offer=" + offer +
                ", date=" + date +
                ", processModel=" + processModel +
                ", processState=" + processState +
                ", lifeCycleState=" + lifeCycleState +
                ", mailboxMessage=" + mailboxMessage +
                ", depositTx=" + depositTx +
                ", contract=" + contract +
                ", contractAsJson='" + contractAsJson + '\'' +
                ", takerContractSignature='" + takerContractSignature + '\'' +
                ", offererContractSignature='" + offererContractSignature + '\'' +
                ", payoutTx=" + payoutTx +
                ", errorMessage='" + errorMessage + '\'' +
                ", throwable=" + throwable +
                '}';
    }
}