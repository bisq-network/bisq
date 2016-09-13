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
import io.bitsquare.arbitration.Arbitrator;
import io.bitsquare.arbitration.ArbitratorManager;
import io.bitsquare.btc.TradeWalletService;
import io.bitsquare.btc.WalletService;
import io.bitsquare.common.crypto.KeyRing;
import io.bitsquare.common.taskrunner.Model;
import io.bitsquare.crypto.DecryptedMsgWithPubKey;
import io.bitsquare.filter.FilterManager;
import io.bitsquare.p2p.NodeAddress;
import io.bitsquare.p2p.P2PService;
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
import org.bitcoinj.utils.ExchangeRate;
import org.bitcoinj.utils.Fiat;
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
        PREPARATION(Phase.PREPARATION),

        TAKER_FEE_PAID(Phase.TAKER_FEE_PAID),

        OFFERER_SENT_PUBLISH_DEPOSIT_TX_REQUEST(Phase.DEPOSIT_REQUESTED),
        TAKER_PUBLISHED_DEPOSIT_TX(Phase.DEPOSIT_PAID),
        DEPOSIT_SEEN_IN_NETWORK(Phase.DEPOSIT_PAID), // triggered by balance update, used only in error cases
        TAKER_SENT_DEPOSIT_TX_PUBLISHED_MSG(Phase.DEPOSIT_PAID),
        OFFERER_RECEIVED_DEPOSIT_TX_PUBLISHED_MSG(Phase.DEPOSIT_PAID),
        DEPOSIT_CONFIRMED_IN_BLOCK_CHAIN(Phase.DEPOSIT_PAID),

        BUYER_CONFIRMED_FIAT_PAYMENT_INITIATED(Phase.FIAT_SENT),
        BUYER_SENT_FIAT_PAYMENT_INITIATED_MSG(Phase.FIAT_SENT),
        SELLER_RECEIVED_FIAT_PAYMENT_INITIATED_MSG(Phase.FIAT_SENT),

        SELLER_CONFIRMED_FIAT_PAYMENT_RECEIPT(Phase.FIAT_RECEIVED),
        SELLER_SENT_FIAT_PAYMENT_RECEIPT_MSG(Phase.FIAT_RECEIVED),
        BUYER_RECEIVED_FIAT_PAYMENT_RECEIPT_MSG(Phase.FIAT_RECEIVED),

        BUYER_COMMITTED_PAYOUT_TX(Phase.PAYOUT_PAID), //TODO needed?
        BUYER_STARTED_SEND_PAYOUT_TX(Phase.PAYOUT_PAID), // not from the success/arrived handler!
        SELLER_RECEIVED_AND_COMMITTED_PAYOUT_TX(Phase.PAYOUT_PAID),
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
    private transient Date maxTradePeriodDate, halfTradePeriodDate;

    // Immutable
    private final Offer offer;
    private final ProcessModel processModel;

    // Mutable
    private DecryptedMsgWithPubKey decryptedMsgWithPubKey;
    private Date takeOfferDate;
    private Coin tradeAmount;
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
    private String offererContractSignature;
    private Transaction payoutTx;
    private long lockTimeAsBlockHeight;
    private NodeAddress arbitratorNodeAddress;
    private byte[] arbitratorBtcPubKey;
    private String takerPaymentAccountId;
    private String errorMessage;
    transient private StringProperty errorMessageProperty;
    transient private ObjectProperty<Coin> tradeAmountProperty;
    transient private ObjectProperty<Fiat> tradeVolumeProperty;
    transient private Set<DecryptedMsgWithPubKey> mailboxMessageSet = new HashSet<>();


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor, initialization
    ///////////////////////////////////////////////////////////////////////////////////////////

    // offerer
    protected Trade(Offer offer, Storage<? extends TradableList> storage) {
        this.offer = offer;
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
    protected Trade(Offer offer, Coin tradeAmount, long tradePrice, NodeAddress tradingPeerNodeAddress,
                    Storage<? extends TradableList> storage) {

        this(offer, storage);
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
                     WalletService walletService,
                     TradeWalletService tradeWalletService,
                     ArbitratorManager arbitratorManager,
                     TradeManager tradeManager,
                     OpenOfferManager openOfferManager,
                     User user,
                     FilterManager filterManager,
                     KeyRing keyRing,
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
                keyRing,
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
        boolean changed = this.state != state;
        this.state = state;
        stateProperty.set(state);
        if (changed)
            persist();
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

    public boolean isTakerFeePaid() {
        return state.getPhase() != null && state.getPhase().ordinal() >= Phase.TAKER_FEE_PAID.ordinal();
    }

    public boolean isDepositPaid() {
        return state.getPhase() != null && state.getPhase().ordinal() >= Phase.DEPOSIT_PAID.ordinal();
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
    public Fiat getTradeVolume() {
        if (tradeAmount != null && getTradePrice() != null)
            return new ExchangeRate(getTradePrice()).coinToFiat(tradeAmount);
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

    public Fiat getTradePrice() {
        return Fiat.valueOf(offer.getCurrencyCode(), tradePrice);
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

    public void applyArbitratorNodeAddress(NodeAddress arbitratorNodeAddress) {
        this.arbitratorNodeAddress = arbitratorNodeAddress;

        Arbitrator arbitrator = processModel.getUser().getAcceptedArbitratorByAddress(arbitratorNodeAddress);
        checkNotNull(arbitrator, "arbitrator must not be null");
        arbitratorBtcPubKey = arbitrator.getBtcPubKey();
    }

    public byte[] getArbitratorPubKey() {
        // Prior to v0.4.8.4 we did not store the arbitratorBtcPubKey in the trade object so we need to support the 
        // previously used version as well and request the arbitrator from the user object (but that caused sometimes a bug when 
        // the client did not get delivered an arbitrator from the P2P network).
        if (arbitratorBtcPubKey == null) {
            Arbitrator arbitrator = processModel.getUser().getAcceptedArbitratorByAddress(arbitratorNodeAddress);
            checkNotNull(arbitrator, "arbitrator must not be null");
            arbitratorBtcPubKey = arbitrator.getBtcPubKey();
        }

        checkNotNull(arbitratorBtcPubKey, "ArbitratorPubKey must not be null");
        return arbitratorBtcPubKey;
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
            if (transactionConfidence.getDepthInBlocks() > 0) {
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
        // we oly apply the state if we are not already further in the process
        if (state.ordinal() < State.DEPOSIT_CONFIRMED_IN_BLOCK_CHAIN.ordinal())
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
                "\n\ttakerContractSignature.hashCode()='" + (takerContractSignature != null ? takerContractSignature.hashCode() : "") + '\'' +
                "\n\toffererContractSignature.hashCode()='" + (offererContractSignature != null ? offererContractSignature.hashCode() : "") + '\'' +
                "\n\tpayoutTx=" + payoutTx +
                "\n\tlockTimeAsBlockHeight=" + lockTimeAsBlockHeight +
                "\n\tarbitratorNodeAddress=" + arbitratorNodeAddress +
                "\n\ttakerPaymentAccountId='" + takerPaymentAccountId + '\'' +
                "\n\terrorMessage='" + errorMessage + '\'' +
                '}';
    }
}