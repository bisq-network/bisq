/*
 * This file is part of bisq.
 *
 * bisq is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * bisq is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with bisq. If not, see <http://www.gnu.org/licenses/>.
 */

package io.bisq.core.trade;

import com.google.common.base.Throwables;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import io.bisq.common.app.DevEnv;
import io.bisq.common.app.Log;
import io.bisq.common.app.Version;
import io.bisq.common.monetary.Price;
import io.bisq.common.monetary.Volume;
import io.bisq.common.storage.Storage;
import io.bisq.common.taskrunner.Model;
import io.bisq.core.arbitration.ArbitratorManager;
import io.bisq.core.btc.wallet.BtcWalletService;
import io.bisq.core.btc.wallet.TradeWalletService;
import io.bisq.core.filter.FilterManager;
import io.bisq.core.offer.Offer;
import io.bisq.core.offer.OpenOfferManager;
import io.bisq.core.trade.protocol.ProcessModel;
import io.bisq.core.trade.protocol.TradeProtocol;
import io.bisq.core.user.User;
import io.bisq.network.p2p.DecryptedMsgWithPubKey;
import io.bisq.network.p2p.storage.P2PService;
import io.bisq.protobuffer.payload.arbitration.Arbitrator;
import io.bisq.protobuffer.payload.arbitration.Mediator;
import io.bisq.protobuffer.payload.p2p.NodeAddress;
import io.bisq.protobuffer.payload.trade.Contract;
import io.bisq.vo.crypto.KeyRingVO;
import javafx.beans.property.*;
import org.bitcoinj.core.Coin;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.TransactionConfidence;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Holds all data which are relevant to the trade, but not those which are only needed in the trade process as shared data between tasks. Those data are
 * stored in the task model.
 */
public abstract class Trade implements Tradable, Model {
    // That object is saved to disc. We need to take care of changes to not break deserialization.
    private static final long serialVersionUID = Version.LOCAL_DB_VERSION;

    private static final Logger log = LoggerFactory.getLogger(Trade.class);

    public enum State {
        // #################### Phase PREPARATION 
        // When trade protocol starts no funds are on stake
        PREPARATION(Phase.PREPARATION),

        // At first part maker/taker have different roles
        // taker perspective
        // #################### Phase TAKER_FEE_PAID 
        TAKER_PUBLISHED_TAKER_FEE_TX(Phase.TAKER_FEE_PUBLISHED),

        // PUBLISH_DEPOSIT_TX_REQUEST
        // maker perspective
        MAKER_SENT_PUBLISH_DEPOSIT_TX_REQUEST(Phase.TAKER_FEE_PUBLISHED),
        MAKER_SAW_ARRIVED_PUBLISH_DEPOSIT_TX_REQUEST(Phase.TAKER_FEE_PUBLISHED),
        MAKER_STORED_IN_MAILBOX_PUBLISH_DEPOSIT_TX_REQUEST(Phase.TAKER_FEE_PUBLISHED),
        MAKER_SEND_FAILED_PUBLISH_DEPOSIT_TX_REQUEST(Phase.TAKER_FEE_PUBLISHED),

        // taker perspective
        TAKER_RECEIVED_PUBLISH_DEPOSIT_TX_REQUEST(Phase.TAKER_FEE_PUBLISHED),


        // #################### Phase DEPOSIT_PAID 
        TAKER_PUBLISHED_DEPOSIT_TX(Phase.DEPOSIT_PUBLISHED),


        // DEPOSIT_TX_PUBLISHED_MSG
        // taker perspective
        TAKER_SENT_DEPOSIT_TX_PUBLISHED_MSG(Phase.DEPOSIT_PUBLISHED),
        TAKER_SAW_ARRIVED_DEPOSIT_TX_PUBLISHED_MSG(Phase.DEPOSIT_PUBLISHED),
        TAKER_STORED_IN_MAILBOX_DEPOSIT_TX_PUBLISHED_MSG(Phase.DEPOSIT_PUBLISHED),
        TAKER_SEND_FAILED_DEPOSIT_TX_PUBLISHED_MSG(Phase.DEPOSIT_PUBLISHED),

        // maker perspective
        MAKER_RECEIVED_DEPOSIT_TX_PUBLISHED_MSG(Phase.DEPOSIT_PUBLISHED),

        // Alternatively the maker could have seen the deposit tx earlier before he received the DEPOSIT_TX_PUBLISHED_MSG
        MAKER_SAW_DEPOSIT_TX_IN_NETWORK(Phase.DEPOSIT_PUBLISHED),


        // #################### Phase DEPOSIT_CONFIRMED
        DEPOSIT_CONFIRMED_IN_BLOCK_CHAIN(Phase.DEPOSIT_CONFIRMED),


        // #################### Phase FIAT_SENT
        BUYER_CONFIRMED_IN_UI_FIAT_PAYMENT_INITIATED(Phase.FIAT_SENT),
        BUYER_SENT_FIAT_PAYMENT_INITIATED_MSG(Phase.FIAT_SENT),
        BUYER_SAW_ARRIVED_FIAT_PAYMENT_INITIATED_MSG(Phase.FIAT_SENT),
        BUYER_STORED_IN_MAILBOX_FIAT_PAYMENT_INITIATED_MSG(Phase.FIAT_SENT),
        BUYER_SEND_FAILED_FIAT_PAYMENT_INITIATED_MSG(Phase.FIAT_SENT),

        SELLER_RECEIVED_FIAT_PAYMENT_INITIATED_MSG(Phase.FIAT_SENT),

        // #################### Phase FIAT_RECEIVED
        SELLER_CONFIRMED_IN_UI_FIAT_PAYMENT_RECEIPT(Phase.FIAT_RECEIVED),

        // #################### Phase PAYOUT_PAID
        SELLER_PUBLISHED_PAYOUT_TX(Phase.PAYOUT_PUBLISHED),

        SELLER_SENT_PAYOUT_TX_PUBLISHED_MSG(Phase.PAYOUT_PUBLISHED),
        SELLER_SAW_ARRIVED_PAYOUT_TX_PUBLISHED_MSG(Phase.PAYOUT_PUBLISHED),
        SELLER_STORED_IN_MAILBOX_PAYOUT_TX_PUBLISHED_MSG(Phase.PAYOUT_PUBLISHED),
        SELLER_SEND_FAILED_PAYOUT_TX_PUBLISHED_MSG(Phase.PAYOUT_PUBLISHED),


        BUYER_RECEIVED_PAYOUT_TX_PUBLISHED_MSG(Phase.PAYOUT_PUBLISHED),
        // Alternatively the maker could have seen the payout tx earlier before he received the PAYOUT_TX_PUBLISHED_MSG
        BUYER_SAW_PAYOUT_TX_IN_NETWORK(Phase.PAYOUT_PUBLISHED),


        // #################### Phase WITHDRAWN
        WITHDRAW_COMPLETED(Phase.WITHDRAWN);

        public Phase getPhase() {
            return phase;
        }

        @NotNull
        private final Phase phase;

        State(@NotNull Phase phase) {
            this.phase = phase;
        }
    }

    public enum Phase {
        PREPARATION,
        TAKER_FEE_PUBLISHED,
        DEPOSIT_PUBLISHED,
        DEPOSIT_CONFIRMED,
        FIAT_SENT,
        FIAT_RECEIVED,
        PAYOUT_PUBLISHED,
        WITHDRAWN
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
    transient private ObjectProperty<Phase> statePhaseProperty;
    transient private ObjectProperty<DisputeState> disputeStateProperty;
    transient private ObjectProperty<TradePeriodState> tradePeriodStateProperty;
    // Trades are saved in the TradeList
    @Nullable
    transient private Storage<? extends TradableList> storage;
    transient protected TradeProtocol tradeProtocol;
    transient private Date maxTradePeriodDate, halfTradePeriodDate;

    // Immutable
    private final Offer offer;
    private final ProcessModel processModel;

    // Mutable
    private DecryptedMsgWithPubKey decryptedMsgWithPubKey;
    private Date takeOfferDate;
    private Coin tradeAmount;
    private final Coin txFee;
    private final Coin takeOfferFee;
    private long tradePrice;
    private NodeAddress tradingPeerNodeAddress;
    @Nullable
    private String takeOfferFeeTxId;
    protected State state;
    private DisputeState disputeState = DisputeState.NONE;
    private TradePeriodState tradePeriodState = TradePeriodState.NORMAL;
    private Transaction depositTx;
    private Contract contract;
    private String contractAsJson;
    private byte[] contractHash;
    private String takerContractSignature;
    private String makerContractSignature;
    private Transaction payoutTx;
    private NodeAddress arbitratorNodeAddress;
    private NodeAddress mediatorNodeAddress;
    private byte[] arbitratorBtcPubKey;
    private String takerPaymentAccountId;
    private String errorMessage;
    transient private StringProperty errorMessageProperty;
    transient private ObjectProperty<Coin> tradeAmountProperty;
    transient private ObjectProperty<Volume> tradeVolumeProperty;
    transient private Set<DecryptedMsgWithPubKey> mailboxMessageSet = new HashSet<>();


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor, initialization
    ///////////////////////////////////////////////////////////////////////////////////////////

    // maker
    protected Trade(Offer offer, Coin txFee, Coin takeOfferFee, Storage<? extends TradableList> storage) {
        this.offer = offer;
        this.txFee = txFee;
        this.takeOfferFee = takeOfferFee;
        this.storage = storage;
        this.takeOfferDate = new Date();

        processModel = new ProcessModel();
        tradeVolumeProperty = new SimpleObjectProperty<>();
        tradeAmountProperty = new SimpleObjectProperty<>();
        errorMessageProperty = new SimpleStringProperty();

        initStates();
        initStateProperties();
    }

    // taker
    protected Trade(Offer offer, Coin tradeAmount, Coin txFee, Coin takeOfferFee, long tradePrice, NodeAddress tradingPeerNodeAddress,
                    Storage<? extends TradableList> storage) {

        this(offer, txFee, takeOfferFee, storage);
        this.tradeAmount = tradeAmount;
        this.tradePrice = tradePrice;
        this.tradingPeerNodeAddress = tradingPeerNodeAddress;
        tradeAmountProperty.set(tradeAmount);
        tradeVolumeProperty.set(getTradeVolume());
        this.takeOfferDate = new Date();
    }

    private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
        try {
            in.defaultReadObject();
            initStateProperties();
            initAmountProperty();
            errorMessageProperty = new SimpleStringProperty(errorMessage);
            mailboxMessageSet = new HashSet<>();
        } catch (Throwable t) {
            log.warn("Cannot be deserialized." + t.getMessage());
        }
    }

    public void init(P2PService p2PService,
                     BtcWalletService walletService,
                     TradeWalletService tradeWalletService,
                     ArbitratorManager arbitratorManager,
                     TradeManager tradeManager,
                     OpenOfferManager openOfferManager,
                     User user,
                     FilterManager filterManager,
                     KeyRingVO keyRingVO,
                     boolean useSavingsWallet,
                     Coin fundsNeededForTrade) {
        Log.traceCall();
        processModel.onAllServicesInitialized(offer,
                tradeManager,
                openOfferManager,
                p2PService,
                walletService,
                tradeWalletService,
                arbitratorManager,
                user,
                filterManager,
                keyRingVO,
                useSavingsWallet,
                fundsNeededForTrade);

        createProtocol();

        log.trace("init: decryptedMsgWithPubKey = " + decryptedMsgWithPubKey);
        if (decryptedMsgWithPubKey != null && !mailboxMessageSet.contains(decryptedMsgWithPubKey)) {
            mailboxMessageSet.add(decryptedMsgWithPubKey);
            tradeProtocol.applyMailboxMessage(decryptedMsgWithPubKey, this);
        }
    }

    protected void initStateProperties() {
        stateProperty = new SimpleObjectProperty<>(state);
        statePhaseProperty = new SimpleObjectProperty<>(state.phase);
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
    public void updateDepositTxFromWallet() {
        if (depositTx != null)
            setDepositTx(processModel.getTradeWalletService().getWalletTx(depositTx.getHash()));
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
        log.trace("setMailboxMessage decryptedMsgWithPubKey=" + decryptedMsgWithPubKey);
        this.decryptedMsgWithPubKey = decryptedMsgWithPubKey;

        if (tradeProtocol != null && decryptedMsgWithPubKey != null && !mailboxMessageSet.contains(decryptedMsgWithPubKey)) {
            mailboxMessageSet.add(decryptedMsgWithPubKey);
            tradeProtocol.applyMailboxMessage(decryptedMsgWithPubKey, this);
        }
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
        log.info("state={}, id=", state, getShortId());
        if (state.getPhase().ordinal() >= this.state.getPhase().ordinal()) {
            boolean changed = this.state != state;
            this.state = state;
            stateProperty.set(state);
            statePhaseProperty.set(state.getPhase());

            if (state == State.WITHDRAW_COMPLETED && tradeProtocol != null)
                tradeProtocol.completed();

            if (changed)
                persist();
        } else {
            final String message = "we got a state change to a previous phase. that is likely a bug.\n" +
                    "old state is: " + this.state + ". New state is: " + state;
            log.error(message);
            if (DevEnv.DEV_MODE)
                throw new RuntimeException(message);
        }
    }

    public void setDisputeState(DisputeState disputeState) {
        Log.traceCall("disputeState=" + disputeState + "\n\ttrade=" + this);
        boolean changed = this.disputeState != disputeState;
        this.disputeState = disputeState;
        disputeStateProperty.set(disputeState);
        if (changed)
            persist();
    }

    public DisputeState getDisputeState() {
        return disputeState;
    }

    public void setTradePeriodState(TradePeriodState tradePeriodState) {
        boolean changed = this.tradePeriodState != tradePeriodState;
        this.tradePeriodState = tradePeriodState;
        tradePeriodStateProperty.set(tradePeriodState);
        if (changed)
            persist();
    }

    public TradePeriodState getTradePeriodState() {
        return tradePeriodState;
    }

    public boolean isInPreparation() {
        return state.getPhase().ordinal() == Phase.PREPARATION.ordinal();
    }

    public boolean isTakerFeePublished() {
        return state.getPhase().ordinal() >= Phase.TAKER_FEE_PUBLISHED.ordinal();
    }

    public boolean isDepositPublished() {
        return state.getPhase().ordinal() >= Phase.DEPOSIT_PUBLISHED.ordinal();
    }

    public boolean isDepositConfirmed() {
        return state.getPhase().ordinal() >= Phase.DEPOSIT_CONFIRMED.ordinal();
    }

    public boolean isFiatSent() {
        return state.getPhase().ordinal() >= Phase.FIAT_SENT.ordinal();
    }

    public boolean isFiatReceived() {
        return state.getPhase().ordinal() >= Phase.FIAT_RECEIVED.ordinal();
    }

    public boolean isPayoutPublished() {
        return state.getPhase().ordinal() >= Phase.PAYOUT_PUBLISHED.ordinal() || isWithdrawn();
    }

    public boolean isWithdrawn() {
        return state.getPhase().ordinal() == Phase.WITHDRAWN.ordinal();
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

    abstract public Coin getPayoutAmount();

    public ProcessModel getProcessModel() {
        return processModel;
    }

    @Nullable
    public Volume getTradeVolume() {
        if (tradeAmount != null && getTradePrice() != null)
            return getTradePrice().getVolumeByAmount(tradeAmount);
        else
            return null;
    }


    @Nullable
    public Date getMaxTradePeriodDate() {
        if (maxTradePeriodDate == null && takeOfferDate != null)
            maxTradePeriodDate = new Date(takeOfferDate.getTime() + getOffer().getPaymentMethod().getMaxTradePeriod());

        return maxTradePeriodDate;
    }

    @Nullable
    public Date getHalfTradePeriodDate() {
        if (halfTradePeriodDate == null && takeOfferDate != null)
            halfTradePeriodDate = new Date(takeOfferDate.getTime() + getOffer().getPaymentMethod().getMaxTradePeriod() / 2);

        return halfTradePeriodDate;
    }

    public boolean hasFailed() {
        return errorMessageProperty().get() != null;
    }

    public ReadOnlyObjectProperty<State> stateProperty() {
        return stateProperty;
    }

    public ReadOnlyObjectProperty<Phase> statePhaseProperty() {
        return statePhaseProperty;
    }

    public ReadOnlyObjectProperty<Coin> tradeAmountProperty() {
        return tradeAmountProperty;
    }

    public ReadOnlyObjectProperty<Volume> tradeVolumeProperty() {
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

    public void setTradePrice(long tradePrice) {
        this.tradePrice = tradePrice;
    }

    public Price getTradePrice() {
        return Price.valueOf(offer.getCurrencyCode(), tradePrice);
    }

    @Nullable
    public Coin getTradeAmount() {
        return tradeAmount;
    }

    public Coin getTxFee() {
        return txFee;
    }

    public Coin getTakeOfferFee() {
        return takeOfferFee;
    }

    public void setTakerContractSignature(String takerSignature) {
        this.takerContractSignature = takerSignature;
    }

    @Nullable
    public String getTakerContractSignature() {
        return takerContractSignature;
    }

    public void setMakerContractSignature(String makerContractSignature) {
        this.makerContractSignature = makerContractSignature;
    }

    @Nullable
    public String getMakerContractSignature() {
        return makerContractSignature;
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

    public String getErrorMessage() {
        return errorMessageProperty.get();
    }

    public NodeAddress getArbitratorNodeAddress() {
        return arbitratorNodeAddress;
    }

    public void applyArbitratorNodeAddress(NodeAddress arbitratorNodeAddress) {
        this.arbitratorNodeAddress = arbitratorNodeAddress;

        Arbitrator arbitrator = processModel.getUser().getAcceptedArbitratorByAddress(arbitratorNodeAddress);
        checkNotNull(arbitrator, "arbitrator must not be null");
        arbitratorBtcPubKey = arbitrator.getBtcPubKey();
    }

    public byte[] getArbitratorBtcPubKey() {
        Arbitrator arbitrator = processModel.getUser().getAcceptedArbitratorByAddress(arbitratorNodeAddress);
        checkNotNull(arbitrator, "arbitrator must not be null");
        arbitratorBtcPubKey = arbitrator.getBtcPubKey();

        checkNotNull(arbitratorBtcPubKey, "ArbitratorPubKey must not be null");
        return arbitratorBtcPubKey;
    }

    public NodeAddress getMediatorNodeAddress() {
        return mediatorNodeAddress;
    }

    public void applyMediatorNodeAddress(NodeAddress mediatorNodeAddress) {
        this.mediatorNodeAddress = mediatorNodeAddress;

        Mediator mediator = processModel.getUser().getAcceptedMediatorByAddress(mediatorNodeAddress);
        checkNotNull(mediator, "mediator must not be null");
    }


    public String getTakerPaymentAccountId() {
        return takerPaymentAccountId;
    }

    public void setTakerPaymentAccountId(String takerPaymentAccountId) {
        this.takerPaymentAccountId = takerPaymentAccountId;
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

    @org.jetbrains.annotations.Nullable
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
            if (transactionConfidence.getConfidenceType() == TransactionConfidence.ConfidenceType.BUILDING) {
                setConfirmedState();
            } else {
                ListenableFuture<TransactionConfidence> future = transactionConfidence.getDepthFuture(1);
                Futures.addCallback(future, new FutureCallback<TransactionConfidence>() {
                    @Override
                    public void onSuccess(TransactionConfidence result) {
                        log.debug("transactionConfidence " + transactionConfidence.getDepthInBlocks());
                        log.debug("state " + state);
                        setConfirmedState();
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

    private void setConfirmedState() {
        // we only apply the state if we are not already further in the process
        if (!isDepositConfirmed())
            setState(State.DEPOSIT_CONFIRMED_IN_BLOCK_CHAIN);
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
                "\n\ttakerContractSignature.hashCode()='" + (takerContractSignature != null ?
                takerContractSignature.hashCode() : "") + '\'' +
                "\n\tmakerContractSignature.hashCode()='" + (makerContractSignature != null ?
                makerContractSignature.hashCode() : "") + '\'' +
                "\n\tpayoutTx=" + payoutTx +
                "\n\tarbitratorNodeAddress=" + arbitratorNodeAddress +
                "\n\tmediatorNodeAddress=" + mediatorNodeAddress +
                "\n\ttakerPaymentAccountId='" + takerPaymentAccountId + '\'' +
                "\n\ttxFee='" + txFee.toFriendlyString() + '\'' +
                "\n\ttakeOfferFee='" + takeOfferFee.toFriendlyString() + '\'' +
                "\n\terrorMessage='" + errorMessage + '\'' +
                '}';
    }
}