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

import io.bitsquare.app.Version;
import io.bitsquare.arbitration.ArbitrationRepository;
import io.bitsquare.btc.BlockChainService;
import io.bitsquare.btc.TradeWalletService;
import io.bitsquare.btc.WalletService;
import io.bitsquare.common.taskrunner.Model;
import io.bitsquare.crypto.CryptoService;
import io.bitsquare.crypto.KeyRing;
import io.bitsquare.crypto.MessageWithPubKey;
import io.bitsquare.p2p.AddressService;
import io.bitsquare.p2p.MessageService;
import io.bitsquare.p2p.Peer;
import io.bitsquare.storage.Storage;
import io.bitsquare.trade.offer.Offer;
import io.bitsquare.trade.offer.OpenOfferManager;
import io.bitsquare.trade.protocol.trade.ProcessModel;
import io.bitsquare.trade.protocol.trade.TradeProtocol;
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
abstract public class Trade implements Tradable, Model, Serializable {
    // That object is saved to disc. We need to take care of changes to not break deserialization.
    private static final long serialVersionUID = Version.LOCAL_DB_VERSION;

    private transient static final Logger log = LoggerFactory.getLogger(Trade.class);

  /*  public enum CriticalPhase {
        PREPARATION,
        TAKER_FEE_PAID,
        DEPOSIT_PAID,
        FIAT_SENT,
        FIAT_RECEIVED,
        PAYOUT_PAID,
        WITHDRAWN,
        FAILED
    }*/

    // Mutable
    private Coin tradeAmount;
    private Peer tradingPeer;
    private transient ObjectProperty<Coin> tradeAmountProperty;
    private transient ObjectProperty<Fiat> tradeVolumeProperty;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Fields
    ///////////////////////////////////////////////////////////////////////////////////////////

    // Transient/Immutable
    private transient ObjectProperty<TradeState> processStateProperty;
    // Trades are saved in the TradeList
    transient private Storage<? extends TradableList> storage;
    transient protected TradeProtocol tradeProtocol;

    transient protected TradeManager tradeManager;
    transient protected OpenOfferManager openOfferManager;

    // Immutable
    private final Offer offer;
    private final ProcessModel processModel;
    private final Date creationDate;

    // Mutable
    private MessageWithPubKey messageWithPubKey;
    protected Date takeOfferDate;
    protected TradeState tradeState;
    private Transaction depositTx;
    private Contract contract;
    private String contractAsJson;
    private String sellerContractSignature;
    private String buyerContractSignature;
    private Transaction payoutTx;
    private long lockTime;
    private String errorMessage;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor, initialization
    ///////////////////////////////////////////////////////////////////////////////////////////

    protected Trade(Offer offer, Storage<? extends TradableList> storage) {
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
                    Storage<? extends TradableList> storage) {

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
                     AddressService addressService,
                     TradeWalletService tradeWalletService,
                     BlockChainService blockChainService,
                     CryptoService cryptoService,
                     ArbitrationRepository arbitrationRepository,
                     TradeManager tradeManager,
                     OpenOfferManager openOfferManager,
                     User user,
                     KeyRing keyRing) {

        this.tradeManager = tradeManager;
        this.openOfferManager = openOfferManager;

        processModel.onAllServicesInitialized(offer,
                messageService,
                addressService,
                walletService,
                tradeWalletService,
                blockChainService,
                cryptoService,
                arbitrationRepository,
                user,
                keyRing);

        createProtocol();

        tradeProtocol.checkPayoutTxTimeLock(this);

        if (messageWithPubKey != null) {
            tradeProtocol.applyMailboxMessage(messageWithPubKey, this);
            // After applied to protocol we remove it
            messageWithPubKey = null;
        }
    }

    protected void initStateProperties() {
        processStateProperty = new SimpleObjectProperty<>(tradeState);
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

    public void setMailboxMessage(MessageWithPubKey messageWithPubKey) {
        this.messageWithPubKey = messageWithPubKey;
    }

    public void setStorage(Storage<? extends TradableList> storage) {
        this.storage = storage;
    }

    public void setTradeState(TradeState tradeState) {
        this.tradeState = tradeState;
        processStateProperty.set(tradeState);
        storage.queueUpForSave();
    }

    abstract public boolean isFailedState();

    abstract public void setFailedState();

    public boolean isCriticalFault() {
        return tradeState.getPhase().ordinal() >= TradeState.Phase.DEPOSIT_PAID.ordinal();
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

    public ReadOnlyObjectProperty<? extends TradeState> tradeStateProperty() {
        return processStateProperty;
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

    @Nullable
    public Coin getTradeAmount() {
        return tradeAmount;
    }

    public void setLockTime(long lockTime) {
        log.debug("lockTime " + lockTime);
        this.lockTime = lockTime;
    }

    public long getLockTime() {
        return lockTime;
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

        if (errorMessage != null && errorMessage.length() > 0) {
            setFailedState();

            if (isCriticalFault())
                tradeManager.addTradeToFailedTrades(this);
            else if (isFailedState())
                tradeManager.addTradeToClosedTrades(this);
        }
    }

    @Nullable
    public String getErrorMessage() {
        return errorMessage;
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
                ", storage=" + storage +
                ", tradeProtocol=" + tradeProtocol +
                ", offer=" + offer +
                ", date=" + takeOfferDate +
                ", processModel=" + processModel +
                ", processState=" + tradeState +
                ", messageWithPubKey=" + messageWithPubKey +
                ", depositTx=" + depositTx +
               /* ", contract=" + contract +
                ", contractAsJson='" + contractAsJson + '\'' +*/
              /*  ", sellerContractSignature='" + sellerContractSignature + '\'' +
                ", buyerContractSignature='" + buyerContractSignature + '\'' +*/
                ", payoutTx=" + payoutTx +
                ", errorMessage='" + errorMessage + '\'' +
                '}';
    }

}