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

import com.google.common.base.Throwables;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import io.bitsquare.app.Log;
import io.bitsquare.app.Version;
import io.bitsquare.arbitration.ArbitratorManager;
import io.bitsquare.btc.FeePolicy;
import io.bitsquare.btc.TradeWalletService;
import io.bitsquare.btc.WalletService;
import io.bitsquare.common.crypto.KeyRing;
import io.bitsquare.common.taskrunner.Model;
import io.bitsquare.p2p.NodeAddress;
import io.bitsquare.p2p.P2PService;
import io.bitsquare.p2p.messaging.DecryptedMsgWithPubKey;
import io.bitsquare.storage.Storage;
import io.bitsquare.trade.offer.Offer;
import io.bitsquare.trade.offer.OpenOfferManager;
import io.bitsquare.trade.protocol.trade.ProcessModel;
import io.bitsquare.trade.protocol.trade.TradeProtocol;
import io.bitsquare.user.User;
import javafx.beans.property.*;
import org.bitcoinj.core.Coin;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.TransactionConfidence;
import org.bitcoinj.utils.Fiat;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.Serializable;
import java.util.Date;

/**
 * Holds all data which are relevant to the trade, but not those which are only needed in the trade process as shared data between tasks. Those data are
 * stored in the task model.
 */
abstract public class Trade implements Tradable, Model, Serializable {
    // That object is saved to disc. We need to take care of changes to not break deserialization.
    private static final long serialVersionUID = Version.LOCAL_DB_VERSION;

    private static final Logger log = LoggerFactory.getLogger(Trade.class);

    public enum State {
        PREPARATION(Phase.PREPARATION),

        TAKER_FEE_PAID(Phase.TAKER_FEE_PAID),

        DEPOSIT_PUBLISH_REQUESTED(Phase.DEPOSIT_REQUESTED),
        DEPOSIT_PUBLISHED(Phase.DEPOSIT_PAID),
        DEPOSIT_SEEN_IN_NETWORK(Phase.DEPOSIT_PAID), // triggered by balance update, used only in error cases
        DEPOSIT_PUBLISHED_MSG_SENT(Phase.DEPOSIT_PAID),
        DEPOSIT_PUBLISHED_MSG_RECEIVED(Phase.DEPOSIT_PAID),
        DEPOSIT_CONFIRMED(Phase.DEPOSIT_PAID),

        FIAT_PAYMENT_STARTED(Phase.FIAT_SENT),
        FIAT_PAYMENT_STARTED_MSG_SENT(Phase.FIAT_SENT),
        FIAT_PAYMENT_STARTED_MSG_RECEIVED(Phase.FIAT_SENT),

        FIAT_PAYMENT_RECEIPT(Phase.FIAT_RECEIVED),
        FIAT_PAYMENT_RECEIPT_MSG_SENT(Phase.FIAT_RECEIVED),
        FIAT_PAYMENT_RECEIPT_MSG_RECEIVED(Phase.FIAT_RECEIVED),

        PAYOUT_TX_COMMITTED(Phase.PAYOUT_PAID),
        PAYOUT_TX_SENT(Phase.PAYOUT_PAID),
        PAYOUT_TX_RECEIVED(Phase.PAYOUT_PAID),
        PAYOUT_BROAD_CASTED(Phase.PAYOUT_PAID),

        WITHDRAW_COMPLETED(Phase.WITHDRAWN);

        public Phase getPhase() {
            return phase;
        }

        private final Phase phase;

        State(Phase phase) {
            this.phase = phase;
        }
    }

    public enum Phase {
        PREPARATION,
        TAKER_FEE_PAID,
        DEPOSIT_REQUESTED,
        DEPOSIT_PAID,
        FIAT_SENT,
        FIAT_RECEIVED,
        PAYOUT_PAID,
        WITHDRAWN,
        DISPUTE
    }

    public enum DisputeState {
        NONE,
        DISPUTE_REQUESTED,
        DISPUTE_STARTED_BY_PEER,
        DISPUTE_CLOSED
    }

    public enum TradePeriodState {
        NORMAL,
        HALF_REACHED,
        TRADE_PERIOD_OVER
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Fields
    ///////////////////////////////////////////////////////////////////////////////////////////

    // Transient/Immutable
    transient private ObjectProperty<State> stateProperty;
    transient private ObjectProperty<DisputeState> disputeStateProperty;
    transient private ObjectProperty<TradePeriodState> tradePeriodStateProperty;
    // Trades are saved in the TradeList
    @Nullable
    transient private Storage<? extends TradableList> storage;
    transient protected TradeProtocol tradeProtocol;

    // Immutable
    private final Offer offer;
    private final ProcessModel processModel;

    // Mutable
    private DecryptedMsgWithPubKey decryptedMsgWithPubKey;
    private Date takeOfferDate = new Date(0); // in some error cases the date is not set and cause null pointers, so we set a default
    private Coin tradeAmount;
    private NodeAddress tradingPeerNodeAddress;
    protected State state;
    private DisputeState disputeState = DisputeState.NONE;
    private TradePeriodState tradePeriodState = TradePeriodState.NORMAL;
    private Transaction depositTx;
    private Contract contract;
    private String contractAsJson;
    private byte[] contractHash;
    private String takerContractSignature;
    private String offererContractSignature;
    private Transaction payoutTx;
    private long lockTimeAsBlockHeight;
    private int openDisputeTimeAsBlockHeight;
    private int checkPaymentTimeAsBlockHeight;
    private NodeAddress arbitratorNodeAddress;
    private String takerPaymentAccountId;
    private boolean halfTradePeriodReachedWarningDisplayed;
    private boolean tradePeriodOverWarningDisplayed;
    private String errorMessage;
    transient private StringProperty errorMessageProperty;
    transient private ObjectProperty<Coin> tradeAmountProperty;
    transient private ObjectProperty<Fiat> tradeVolumeProperty;
    @Nullable
    private String takeOfferFeeTxId;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor, initialization
    ///////////////////////////////////////////////////////////////////////////////////////////

    protected Trade(Offer offer, Storage<? extends TradableList> storage) {
        this.offer = offer;
        this.storage = storage;

        processModel = new ProcessModel();
        tradeVolumeProperty = new SimpleObjectProperty<>();
        tradeAmountProperty = new SimpleObjectProperty<>();
        errorMessageProperty = new SimpleStringProperty();

        initStates();
        initStateProperties();
    }

    // taker
    protected Trade(Offer offer, Coin tradeAmount, NodeAddress tradingPeerNodeAddress,
                    Storage<? extends TradableList> storage) {

        this(offer, storage);
        this.tradeAmount = tradeAmount;
        this.tradingPeerNodeAddress = tradingPeerNodeAddress;
        tradeAmountProperty.set(tradeAmount);
        tradeVolumeProperty.set(getTradeVolume());
    }

    private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
        try {
            in.defaultReadObject();
            initStateProperties();
            initAmountProperty();
            errorMessageProperty = new SimpleStringProperty(errorMessage);
        } catch (Throwable t) {
            log.trace("Cannot be deserialized." + t.getMessage());
        }
    }

    public void init(P2PService p2PService,
                     WalletService walletService,
                     TradeWalletService tradeWalletService,
                     ArbitratorManager arbitratorManager,
                     TradeManager tradeManager,
                     OpenOfferManager openOfferManager,
                     User user,
                     KeyRing keyRing) {

        processModel.onAllServicesInitialized(offer,
                tradeManager,
                openOfferManager,
                p2PService,
                walletService,
                tradeWalletService,
                arbitratorManager,
                user,
                keyRing);

        createProtocol();

        tradeProtocol.checkPayoutTxTimeLock(this);

        if (decryptedMsgWithPubKey != null) {
            tradeProtocol.applyMailboxMessage(decryptedMsgWithPubKey, this);
        }
    }

    protected void initStateProperties() {
        stateProperty = new SimpleObjectProperty<>(state);
        disputeStateProperty = new SimpleObjectProperty<>(disputeState);
        tradePeriodStateProperty = new SimpleObjectProperty<>(tradePeriodState);
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
            setDepositTx(tradeWalletService.getWalletTx(depositTx.getHash()));
    }

    public void setDepositTx(Transaction tx) {
        log.debug("setDepositTx " + tx);
        this.depositTx = tx;
        setupConfidenceListener();
        persist();
    }

    @Nullable
    public Transaction getDepositTx() {
        return depositTx;
    }

    public void setMailboxMessage(DecryptedMsgWithPubKey decryptedMsgWithPubKey) {
        log.trace("setMailboxMessage " + decryptedMsgWithPubKey);
        this.decryptedMsgWithPubKey = decryptedMsgWithPubKey;
    }

    public DecryptedMsgWithPubKey getMailboxMessage() {
        return decryptedMsgWithPubKey;
    }

    public void setStorage(Storage<? extends TradableList> storage) {
        this.storage = storage;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // States
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void setState(State state) {
        this.state = state;
        stateProperty.set(state);
        persist();
        persist();
    }

    public void setDisputeState(DisputeState disputeState) {
        Log.traceCall("disputeState=" + disputeState + "\n\ttrade=" + this);
        this.disputeState = disputeState;
        disputeStateProperty.set(disputeState);
        persist();
    }

    public DisputeState getDisputeState() {
        return disputeState;
    }

    public void setTradePeriodState(TradePeriodState tradePeriodState) {
        this.tradePeriodState = tradePeriodState;
        tradePeriodStateProperty.set(tradePeriodState);
        persist();
    }

    public TradePeriodState getTradePeriodState() {
        return tradePeriodState;
    }

    public boolean isTakerFeePaid() {
        return state.getPhase() != null && state.getPhase().ordinal() >= Phase.TAKER_FEE_PAID.ordinal();
    }

    public State getState() {
        return state;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Model implementation
    ///////////////////////////////////////////////////////////////////////////////////////////

    // Get called from taskRunner after each completed task
    @Override
    public void persist() {
        if (storage != null)
            storage.queueUpForSave();
    }

    @Override
    public void onComplete() {
        persist();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Getter only
    ///////////////////////////////////////////////////////////////////////////////////////////

    public String getId() {
        return offer.getId();
    }

    public String getShortId() {
        return offer.getShortId();
    }

    public Offer getOffer() {
        return offer;
    }

    public Coin getPayoutAmount() {
        return FeePolicy.getSecurityDeposit();
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


    public ReadOnlyObjectProperty<? extends State> stateProperty() {
        return stateProperty;
    }

    public ReadOnlyObjectProperty<Coin> tradeAmountProperty() {
        return tradeAmountProperty;
    }

    public ReadOnlyObjectProperty<Fiat> tradeVolumeProperty() {
        return tradeVolumeProperty;
    }


    public ReadOnlyObjectProperty<DisputeState> disputeStateProperty() {
        return disputeStateProperty;
    }

    public ReadOnlyObjectProperty<TradePeriodState> getTradePeriodStateProperty() {
        return tradePeriodStateProperty;
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Getter/Setter for Mutable objects
    ///////////////////////////////////////////////////////////////////////////////////////////

    public Date getDate() {
        return takeOfferDate;
    }

    public void setTakeOfferDate(Date takeOfferDate) {
        this.takeOfferDate = takeOfferDate;
    }

    public void setTradingPeerNodeAddress(NodeAddress tradingPeerNodeAddress) {
        if (tradingPeerNodeAddress == null)
            log.error("tradingPeerAddress=null");
        else
            this.tradingPeerNodeAddress = tradingPeerNodeAddress;
    }

    @Nullable
    public NodeAddress getTradingPeerNodeAddress() {
        return tradingPeerNodeAddress;
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

    public void setLockTimeAsBlockHeight(long lockTimeAsBlockHeight) {
        this.lockTimeAsBlockHeight = lockTimeAsBlockHeight;
    }

    public long getLockTimeAsBlockHeight() {
        return lockTimeAsBlockHeight;
    }

    public int getOpenDisputeTimeAsBlockHeight() {
        return openDisputeTimeAsBlockHeight;
    }

    public void setOpenDisputeTimeAsBlockHeight(int openDisputeTimeAsBlockHeight) {
        this.openDisputeTimeAsBlockHeight = openDisputeTimeAsBlockHeight;
    }

    public int getCheckPaymentTimeAsBlockHeight() {
        return checkPaymentTimeAsBlockHeight;
    }

    public void setCheckPaymentTimeAsBlockHeight(int checkPaymentTimeAsBlockHeight) {
        this.checkPaymentTimeAsBlockHeight = checkPaymentTimeAsBlockHeight;
    }

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
    @Nullable
    public Transaction getPayoutTx() {
        return payoutTx;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
        errorMessageProperty.set(errorMessage);
    }

    public ReadOnlyStringProperty errorMessageProperty() {
        return errorMessageProperty;
    }

    public NodeAddress getArbitratorNodeAddress() {
        return arbitratorNodeAddress;
    }

    public void setArbitratorNodeAddress(NodeAddress arbitratorNodeAddress) {
        this.arbitratorNodeAddress = arbitratorNodeAddress;
    }

    public String getTakerPaymentAccountId() {
        return takerPaymentAccountId;
    }

    public void setTakerPaymentAccountId(String takerPaymentAccountId) {
        this.takerPaymentAccountId = takerPaymentAccountId;
    }

    public void setHalfTradePeriodReachedWarningDisplayed(boolean halfTradePeriodReachedWarningDisplayed) {
        this.halfTradePeriodReachedWarningDisplayed = halfTradePeriodReachedWarningDisplayed;
        persist();
    }

    public boolean isHalfTradePeriodReachedWarningDisplayed() {
        return halfTradePeriodReachedWarningDisplayed;
    }

    public void setTradePeriodOverWarningDisplayed(boolean tradePeriodOverWarningDisplayed) {
        this.tradePeriodOverWarningDisplayed = tradePeriodOverWarningDisplayed;
        persist();
    }

    public boolean isTradePeriodOverWarningDisplayed() {
        return tradePeriodOverWarningDisplayed;
    }

    public void setContractHash(byte[] contractHash) {
        this.contractHash = contractHash;
    }

    public byte[] getContractHash() {
        return contractHash;
    }

    public void setTakeOfferFeeTxId(String takeOfferFeeTxId) {
        this.takeOfferFeeTxId = takeOfferFeeTxId;
    }

    public String getTakeOfferFeeTxId() {
        return takeOfferFeeTxId;
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Private
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void setupConfidenceListener() {
        log.debug("setupConfidenceListener");
        if (depositTx != null) {
            TransactionConfidence transactionConfidence = depositTx.getConfidence();
            log.debug("transactionConfidence " + transactionConfidence.getDepthInBlocks());
            if (transactionConfidence.getDepthInBlocks() > 0) {
                handleConfidenceResult();
            } else {
                ListenableFuture<TransactionConfidence> future = transactionConfidence.getDepthFuture(1);
                Futures.addCallback(future, new FutureCallback<TransactionConfidence>() {
                    @Override
                    public void onSuccess(TransactionConfidence result) {
                        log.debug("transactionConfidence " + transactionConfidence.getDepthInBlocks());
                        log.debug("state " + state);
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

        } else {
            log.error("depositTx == null. That must not happen.");
        }
    }

    abstract protected void createProtocol();

    private void handleConfidenceResult() {
        if (state.ordinal() < State.DEPOSIT_CONFIRMED.ordinal())
            setState(State.DEPOSIT_CONFIRMED);
    }

    abstract protected void initStates();

    @Override
    public String toString() {
        return "Trade{" +
                "\n\ttradeAmount=" + tradeAmount +
                "\n\ttradingPeerNodeAddress=" + tradingPeerNodeAddress +
                "\n\ttradeVolume=" + tradeVolumeProperty.get() +
                "\n\toffer=" + offer +
                "\n\tprocessModel=" + processModel +
                "\n\tdecryptedMsgWithPubKey=" + decryptedMsgWithPubKey +
                "\n\ttakeOfferDate=" + takeOfferDate +
                "\n\tstate=" + state +
                "\n\tdisputeState=" + disputeState +
                "\n\ttradePeriodState=" + tradePeriodState +
                "\n\tdepositTx=" + depositTx +
                "\n\ttakeOfferFeeTxId=" + takeOfferFeeTxId +
                "\n\tcontract=" + contract +
                "\n\ttakerContractSignature.hashCode()='" + (takerContractSignature != null ? takerContractSignature.hashCode() : "") + '\'' +
                "\n\toffererContractSignature.hashCode()='" + (offererContractSignature != null ? offererContractSignature.hashCode() : "") + '\'' +
                "\n\tpayoutTx=" + payoutTx +
                "\n\tlockTimeAsBlockHeight=" + lockTimeAsBlockHeight +
                "\n\topenDisputeTimeAsBlockHeight=" + openDisputeTimeAsBlockHeight +
                "\n\tcheckPaymentTimeAsBlockHeight=" + checkPaymentTimeAsBlockHeight +
                "\n\tarbitratorNodeAddress=" + arbitratorNodeAddress +
                "\n\ttakerPaymentAccountId='" + takerPaymentAccountId + '\'' +
                "\n\thalfTradePeriodReachedWarningDisplayed=" + halfTradePeriodReachedWarningDisplayed +
                "\n\ttradePeriodOverWarningDisplayed=" + tradePeriodOverWarningDisplayed +
                "\n\terrorMessage='" + errorMessage + '\'' +
                '}';
    }
}