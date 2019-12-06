/*
 * This file is part of Bisq.
 *
 * Bisq is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * Bisq is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Bisq. If not, see <http://www.gnu.org/licenses/>.
 */

package bisq.core.trade;

import bisq.core.account.witness.AccountAgeWitnessService;
import bisq.core.btc.wallet.BsqWalletService;
import bisq.core.btc.wallet.BtcWalletService;
import bisq.core.btc.wallet.TradeWalletService;
import bisq.core.dao.DaoFacade;
import bisq.core.filter.FilterManager;
import bisq.core.locale.CurrencyUtil;
import bisq.core.monetary.Price;
import bisq.core.monetary.Volume;
import bisq.core.offer.Offer;
import bisq.core.offer.OfferUtil;
import bisq.core.offer.OpenOfferManager;
import bisq.core.payment.payload.PaymentMethod;
import bisq.core.proto.CoreProtoResolver;
import bisq.core.support.dispute.arbitration.arbitrator.Arbitrator;
import bisq.core.support.dispute.arbitration.arbitrator.ArbitratorManager;
import bisq.core.support.dispute.mediation.MediationResultState;
import bisq.core.support.dispute.mediation.mediator.MediatorManager;
import bisq.core.support.dispute.refund.RefundResultState;
import bisq.core.support.dispute.refund.refundagent.RefundAgentManager;
import bisq.core.support.messages.ChatMessage;
import bisq.core.trade.protocol.ProcessModel;
import bisq.core.trade.protocol.TradeProtocol;
import bisq.core.trade.statistics.ReferralIdService;
import bisq.core.trade.statistics.TradeStatisticsManager;
import bisq.core.user.User;

import bisq.network.p2p.DecryptedMessageWithPubKey;
import bisq.network.p2p.NodeAddress;
import bisq.network.p2p.P2PService;

import bisq.common.crypto.KeyRing;
import bisq.common.crypto.PubKeyRing;
import bisq.common.proto.ProtoUtil;
import bisq.common.storage.Storage;
import bisq.common.taskrunner.Model;
import bisq.common.util.Utilities;

import com.google.protobuf.ByteString;
import com.google.protobuf.Message;

import org.bitcoinj.core.Coin;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.TransactionConfidence;

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;

import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyStringProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

import java.util.Date;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import org.jetbrains.annotations.NotNull;

import javax.annotation.Nullable;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Holds all data which are relevant to the trade, but not those which are only needed in the trade process as shared data between tasks. Those data are
 * stored in the task model.
 */
@Slf4j
public abstract class Trade implements Tradable, Model {

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Enums
    ///////////////////////////////////////////////////////////////////////////////////////////

    public enum State {
        // #################### Phase PREPARATION
        // When trade protocol starts no funds are on stake
        PREPARATION(Phase.INIT),

        // At first part maker/taker have different roles
        // taker perspective
        // #################### Phase TAKER_FEE_PAID
        TAKER_PUBLISHED_TAKER_FEE_TX(Phase.TAKER_FEE_PUBLISHED),

        // PUBLISH_DEPOSIT_TX_REQUEST
        // maker perspective
        MAKER_SENT_PUBLISH_DEPOSIT_TX_REQUEST(Phase.TAKER_FEE_PUBLISHED),
        MAKER_SAW_ARRIVED_PUBLISH_DEPOSIT_TX_REQUEST(Phase.TAKER_FEE_PUBLISHED),
        MAKER_STORED_IN_MAILBOX_PUBLISH_DEPOSIT_TX_REQUEST(Phase.TAKER_FEE_PUBLISHED), //todo remove
        MAKER_SEND_FAILED_PUBLISH_DEPOSIT_TX_REQUEST(Phase.TAKER_FEE_PUBLISHED),

        // taker perspective
        TAKER_RECEIVED_PUBLISH_DEPOSIT_TX_REQUEST(Phase.TAKER_FEE_PUBLISHED),


        // #################### Phase DEPOSIT_PAID
        SELLER_PUBLISHED_DEPOSIT_TX(Phase.DEPOSIT_PUBLISHED),


        // DEPOSIT_TX_PUBLISHED_MSG
        // seller perspective
        SELLER_SENT_DEPOSIT_TX_PUBLISHED_MSG(Phase.DEPOSIT_PUBLISHED),
        SELLER_SAW_ARRIVED_DEPOSIT_TX_PUBLISHED_MSG(Phase.DEPOSIT_PUBLISHED),
        SELLER_STORED_IN_MAILBOX_DEPOSIT_TX_PUBLISHED_MSG(Phase.DEPOSIT_PUBLISHED),
        SELLER_SEND_FAILED_DEPOSIT_TX_PUBLISHED_MSG(Phase.DEPOSIT_PUBLISHED),

        // buyer perspective
        BUYER_RECEIVED_DEPOSIT_TX_PUBLISHED_MSG(Phase.DEPOSIT_PUBLISHED),

        // Alternatively the buyer could have seen the deposit tx earlier before he received the DEPOSIT_TX_PUBLISHED_MSG
        BUYER_SAW_DEPOSIT_TX_IN_NETWORK(Phase.DEPOSIT_PUBLISHED),


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

        @NotNull
        public Phase getPhase() {
            return phase;
        }

        @NotNull
        private final Phase phase;

        State(@NotNull Phase phase) {
            this.phase = phase;
        }

        public static Trade.State fromProto(protobuf.Trade.State state) {
            return ProtoUtil.enumFromProto(Trade.State.class, state.name());
        }

        public static protobuf.Trade.State toProtoMessage(Trade.State state) {
            return protobuf.Trade.State.valueOf(state.name());
        }
    }

    public enum Phase {
        INIT,
        TAKER_FEE_PUBLISHED,
        DEPOSIT_PUBLISHED,
        DEPOSIT_CONFIRMED,
        FIAT_SENT,
        FIAT_RECEIVED,
        PAYOUT_PUBLISHED,
        WITHDRAWN;

        public static Trade.Phase fromProto(protobuf.Trade.Phase phase) {
            return ProtoUtil.enumFromProto(Trade.Phase.class, phase.name());
        }

        public static protobuf.Trade.Phase toProtoMessage(Trade.Phase phase) {
            return protobuf.Trade.Phase.valueOf(phase.name());
        }
    }

    public enum DisputeState {
        NO_DISPUTE,
        // arbitration
        DISPUTE_REQUESTED,
        DISPUTE_STARTED_BY_PEER,
        DISPUTE_CLOSED,

        // mediation
        MEDIATION_REQUESTED,
        MEDIATION_STARTED_BY_PEER,
        MEDIATION_CLOSED,

        // refund
        REFUND_REQUESTED,
        REFUND_REQUEST_STARTED_BY_PEER,
        REFUND_REQUEST_CLOSED;

        public static Trade.DisputeState fromProto(protobuf.Trade.DisputeState disputeState) {
            return ProtoUtil.enumFromProto(Trade.DisputeState.class, disputeState.name());
        }

        public static protobuf.Trade.DisputeState toProtoMessage(Trade.DisputeState disputeState) {
            return protobuf.Trade.DisputeState.valueOf(disputeState.name());
        }
    }

    public enum TradePeriodState {
        FIRST_HALF,
        SECOND_HALF,
        TRADE_PERIOD_OVER;

        public static Trade.TradePeriodState fromProto(protobuf.Trade.TradePeriodState tradePeriodState) {
            return ProtoUtil.enumFromProto(Trade.TradePeriodState.class, tradePeriodState.name());
        }

        public static protobuf.Trade.TradePeriodState toProtoMessage(Trade.TradePeriodState tradePeriodState) {
            return protobuf.Trade.TradePeriodState.valueOf(tradePeriodState.name());
        }
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Fields
    ///////////////////////////////////////////////////////////////////////////////////////////

    // Persistable
    // Immutable
    @Nullable
    @Getter
    private final Offer offer;
    @Getter
    private final boolean isCurrencyForTakerFeeBtc;
    @Getter
    private final long txFeeAsLong;
    @Getter
    private final long takerFeeAsLong;
    @Setter
    private long takeOfferDate;
    @Getter
    @Setter
    private ProcessModel processModel;

    //  Mutable
    @Nullable
    @Getter
    @Setter
    private String takerFeeTxId;
    @Nullable
    @Getter
    @Setter
    private String depositTxId;
    @Nullable
    @Getter
    @Setter
    private String payoutTxId;
    @Getter
    @Setter
    private long tradeAmountAsLong;
    @Setter
    private long tradePrice;
    @Nullable
    @Getter
    private NodeAddress tradingPeerNodeAddress;
    @Getter
    private State state = State.PREPARATION;
    @Getter
    private DisputeState disputeState = DisputeState.NO_DISPUTE;
    @Getter
    private TradePeriodState tradePeriodState = TradePeriodState.FIRST_HALF;
    @Nullable
    @Getter
    @Setter
    private Contract contract;
    @Nullable
    @Getter
    @Setter
    private String contractAsJson;
    @Nullable
    @Getter
    @Setter
    private byte[] contractHash;
    @Nullable
    @Getter
    @Setter
    private String takerContractSignature;
    @Nullable
    @Getter
    @Setter
    private String makerContractSignature;
    @Nullable
    @Getter
    @Setter
    private NodeAddress arbitratorNodeAddress;
    @Nullable
    @Setter
    private byte[] arbitratorBtcPubKey;
    @Nullable
    @Getter
    @Setter
    private PubKeyRing arbitratorPubKeyRing;
    @Nullable
    @Getter
    @Setter
    private NodeAddress mediatorNodeAddress;
    @Nullable
    @Getter
    @Setter
    private PubKeyRing mediatorPubKeyRing;
    @Nullable
    @Getter
    @Setter
    private String takerPaymentAccountId;
    @Nullable
    private String errorMessage;
    @Getter
    @Setter
    @Nullable
    private String counterCurrencyTxId;
    @Getter
    private final ObservableList<ChatMessage> chatMessages = FXCollections.observableArrayList();

    // Transient
    // Immutable
    @Getter
    transient final private Coin txFee;
    @Getter
    transient final private Coin takerFee;
    @Getter // to set in constructor so not final but set at init
    transient private Storage<? extends TradableList> storage;
    @Getter // to set in constructor so not final but set at init
    transient private BtcWalletService btcWalletService;

    transient final private ObjectProperty<State> stateProperty = new SimpleObjectProperty<>(state);
    transient final private ObjectProperty<Phase> statePhaseProperty = new SimpleObjectProperty<>(state.phase);
    transient final private ObjectProperty<DisputeState> disputeStateProperty = new SimpleObjectProperty<>(disputeState);
    transient final private ObjectProperty<TradePeriodState> tradePeriodStateProperty = new SimpleObjectProperty<>(tradePeriodState);
    transient final private StringProperty errorMessageProperty = new SimpleStringProperty();

    //  Mutable
    @Getter
    transient protected TradeProtocol tradeProtocol;
    @Nullable
    transient private Transaction depositTx;

    // Added in v1.2.0
    @Nullable
    transient private Transaction delayedPayoutTx;

    @Nullable
    transient private Transaction payoutTx;
    @Nullable
    transient private Coin tradeAmount;

    transient private ObjectProperty<Coin> tradeAmountProperty;
    transient private ObjectProperty<Volume> tradeVolumeProperty;
    final transient private Set<DecryptedMessageWithPubKey> decryptedMessageWithPubKeySet = new HashSet<>();

    // Added in v1.1.6
    @Getter
    @Nullable
    private MediationResultState mediationResultState = MediationResultState.UNDEFINED_MEDIATION_RESULT;
    transient final private ObjectProperty<MediationResultState> mediationResultStateProperty = new SimpleObjectProperty<>(mediationResultState);

    // Added in v1.2.0
    @Getter
    @Setter
    private long lockTime;
    @Nullable
    @Getter
    @Setter
    private byte[] delayedPayoutTxBytes;
    @Nullable
    @Getter
    @Setter
    private NodeAddress refundAgentNodeAddress;
    @Nullable
    @Getter
    @Setter
    private PubKeyRing refundAgentPubKeyRing;
    @Getter
    @Nullable
    private RefundResultState refundResultState = RefundResultState.UNDEFINED_REFUND_RESULT;
    transient final private ObjectProperty<RefundResultState> refundResultStateProperty = new SimpleObjectProperty<>(refundResultState);


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor, initialization
    ///////////////////////////////////////////////////////////////////////////////////////////

    // maker
    protected Trade(Offer offer,
                    Coin txFee,
                    Coin takerFee,
                    boolean isCurrencyForTakerFeeBtc,
                    @Nullable NodeAddress arbitratorNodeAddress,
                    @Nullable NodeAddress mediatorNodeAddress,
                    @Nullable NodeAddress refundAgentNodeAddress,
                    Storage<? extends TradableList> storage,
                    BtcWalletService btcWalletService) {
        this.offer = offer;
        this.txFee = txFee;
        this.takerFee = takerFee;
        this.isCurrencyForTakerFeeBtc = isCurrencyForTakerFeeBtc;
        this.storage = storage;
        this.btcWalletService = btcWalletService;
        this.arbitratorNodeAddress = arbitratorNodeAddress;
        this.mediatorNodeAddress = mediatorNodeAddress;
        this.refundAgentNodeAddress = refundAgentNodeAddress;

        txFeeAsLong = txFee.value;
        takerFeeAsLong = takerFee.value;
        takeOfferDate = new Date().getTime();
        processModel = new ProcessModel();
    }


    // taker
    @SuppressWarnings("NullableProblems")
    protected Trade(Offer offer,
                    Coin tradeAmount,
                    Coin txFee,
                    Coin takerFee,
                    boolean isCurrencyForTakerFeeBtc,
                    long tradePrice,
                    NodeAddress tradingPeerNodeAddress,
                    @Nullable NodeAddress arbitratorNodeAddress,
                    @Nullable NodeAddress mediatorNodeAddress,
                    @Nullable NodeAddress refundAgentNodeAddress,
                    Storage<? extends TradableList> storage,
                    BtcWalletService btcWalletService) {

        this(offer,
                txFee,
                takerFee,
                isCurrencyForTakerFeeBtc,
                arbitratorNodeAddress,
                mediatorNodeAddress,
                refundAgentNodeAddress,
                storage,
                btcWalletService);
        this.tradePrice = tradePrice;
        this.tradingPeerNodeAddress = tradingPeerNodeAddress;

        setTradeAmount(tradeAmount);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // PROTO BUFFER
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public Message toProtoMessage() {
        final protobuf.Trade.Builder builder = protobuf.Trade.newBuilder()
                .setOffer(checkNotNull(offer).toProtoMessage())
                .setIsCurrencyForTakerFeeBtc(isCurrencyForTakerFeeBtc)
                .setTxFeeAsLong(txFeeAsLong)
                .setTakerFeeAsLong(takerFeeAsLong)
                .setTakeOfferDate(takeOfferDate)
                .setProcessModel(processModel.toProtoMessage())
                .setTradeAmountAsLong(tradeAmountAsLong)
                .setTradePrice(tradePrice)
                .setState(Trade.State.toProtoMessage(state))
                .setDisputeState(Trade.DisputeState.toProtoMessage(disputeState))
                .setTradePeriodState(Trade.TradePeriodState.toProtoMessage(tradePeriodState))
                .addAllChatMessage(chatMessages.stream()
                        .map(msg -> msg.toProtoNetworkEnvelope().getChatMessage())
                        .collect(Collectors.toList()))
                .setLockTime(lockTime);

        Optional.ofNullable(takerFeeTxId).ifPresent(builder::setTakerFeeTxId);
        Optional.ofNullable(depositTxId).ifPresent(builder::setDepositTxId);
        Optional.ofNullable(payoutTxId).ifPresent(builder::setPayoutTxId);
        Optional.ofNullable(tradingPeerNodeAddress).ifPresent(e -> builder.setTradingPeerNodeAddress(tradingPeerNodeAddress.toProtoMessage()));
        Optional.ofNullable(contract).ifPresent(e -> builder.setContract(contract.toProtoMessage()));
        Optional.ofNullable(contractAsJson).ifPresent(builder::setContractAsJson);
        Optional.ofNullable(contractHash).ifPresent(e -> builder.setContractHash(ByteString.copyFrom(contractHash)));
        Optional.ofNullable(takerContractSignature).ifPresent(builder::setTakerContractSignature);
        Optional.ofNullable(makerContractSignature).ifPresent(builder::setMakerContractSignature);
        Optional.ofNullable(arbitratorNodeAddress).ifPresent(e -> builder.setArbitratorNodeAddress(arbitratorNodeAddress.toProtoMessage()));
        Optional.ofNullable(mediatorNodeAddress).ifPresent(e -> builder.setMediatorNodeAddress(mediatorNodeAddress.toProtoMessage()));
        Optional.ofNullable(refundAgentNodeAddress).ifPresent(e -> builder.setRefundAgentNodeAddress(refundAgentNodeAddress.toProtoMessage()));
        Optional.ofNullable(arbitratorBtcPubKey).ifPresent(e -> builder.setArbitratorBtcPubKey(ByteString.copyFrom(arbitratorBtcPubKey)));
        Optional.ofNullable(takerPaymentAccountId).ifPresent(builder::setTakerPaymentAccountId);
        Optional.ofNullable(errorMessage).ifPresent(builder::setErrorMessage);
        Optional.ofNullable(arbitratorPubKeyRing).ifPresent(e -> builder.setArbitratorPubKeyRing(arbitratorPubKeyRing.toProtoMessage()));
        Optional.ofNullable(mediatorPubKeyRing).ifPresent(e -> builder.setMediatorPubKeyRing(mediatorPubKeyRing.toProtoMessage()));
        Optional.ofNullable(refundAgentPubKeyRing).ifPresent(e -> builder.setRefundAgentPubKeyRing(refundAgentPubKeyRing.toProtoMessage()));
        Optional.ofNullable(counterCurrencyTxId).ifPresent(e -> builder.setCounterCurrencyTxId(counterCurrencyTxId));
        Optional.ofNullable(mediationResultState).ifPresent(e -> builder.setMediationResultState(MediationResultState.toProtoMessage(mediationResultState)));
        Optional.ofNullable(refundResultState).ifPresent(e -> builder.setRefundResultState(RefundResultState.toProtoMessage(refundResultState)));
        Optional.ofNullable(delayedPayoutTxBytes).ifPresent(e -> builder.setDelayedPayoutTxBytes(ByteString.copyFrom(delayedPayoutTxBytes)));
        return builder.build();
    }

    public static Trade fromProto(Trade trade, protobuf.Trade proto, CoreProtoResolver coreProtoResolver) {
        trade.setTakeOfferDate(proto.getTakeOfferDate());
        trade.setProcessModel(ProcessModel.fromProto(proto.getProcessModel(), coreProtoResolver));
        trade.setState(State.fromProto(proto.getState()));
        trade.setDisputeState(DisputeState.fromProto(proto.getDisputeState()));
        trade.setTradePeriodState(TradePeriodState.fromProto(proto.getTradePeriodState()));
        trade.setTakerFeeTxId(ProtoUtil.stringOrNullFromProto(proto.getTakerFeeTxId()));
        trade.setDepositTxId(ProtoUtil.stringOrNullFromProto(proto.getDepositTxId()));
        trade.setPayoutTxId(ProtoUtil.stringOrNullFromProto(proto.getPayoutTxId()));
        trade.setContract(proto.hasContract() ? Contract.fromProto(proto.getContract(), coreProtoResolver) : null);
        trade.setContractAsJson(ProtoUtil.stringOrNullFromProto(proto.getContractAsJson()));
        trade.setContractHash(ProtoUtil.byteArrayOrNullFromProto(proto.getContractHash()));
        trade.setTakerContractSignature(ProtoUtil.stringOrNullFromProto(proto.getTakerContractSignature()));
        trade.setMakerContractSignature(ProtoUtil.stringOrNullFromProto(proto.getMakerContractSignature()));
        trade.setArbitratorNodeAddress(proto.hasArbitratorNodeAddress() ? NodeAddress.fromProto(proto.getArbitratorNodeAddress()) : null);
        trade.setMediatorNodeAddress(proto.hasMediatorNodeAddress() ? NodeAddress.fromProto(proto.getMediatorNodeAddress()) : null);
        trade.setRefundAgentNodeAddress(proto.hasRefundAgentNodeAddress() ? NodeAddress.fromProto(proto.getRefundAgentNodeAddress()) : null);
        trade.setArbitratorBtcPubKey(ProtoUtil.byteArrayOrNullFromProto(proto.getArbitratorBtcPubKey()));
        trade.setTakerPaymentAccountId(ProtoUtil.stringOrNullFromProto(proto.getTakerPaymentAccountId()));
        trade.setErrorMessage(ProtoUtil.stringOrNullFromProto(proto.getErrorMessage()));
        trade.setArbitratorPubKeyRing(proto.hasArbitratorPubKeyRing() ? PubKeyRing.fromProto(proto.getArbitratorPubKeyRing()) : null);
        trade.setMediatorPubKeyRing(proto.hasMediatorPubKeyRing() ? PubKeyRing.fromProto(proto.getMediatorPubKeyRing()) : null);
        trade.setRefundAgentPubKeyRing(proto.hasRefundAgentPubKeyRing() ? PubKeyRing.fromProto(proto.getRefundAgentPubKeyRing()) : null);
        trade.setCounterCurrencyTxId(proto.getCounterCurrencyTxId().isEmpty() ? null : proto.getCounterCurrencyTxId());
        trade.setMediationResultState(MediationResultState.fromProto(proto.getMediationResultState()));
        trade.setRefundResultState(RefundResultState.fromProto(proto.getRefundResultState()));
        trade.setDelayedPayoutTxBytes(ProtoUtil.byteArrayOrNullFromProto(proto.getDelayedPayoutTxBytes()));
        trade.setLockTime(proto.getLockTime());

        trade.chatMessages.addAll(proto.getChatMessageList().stream()
                .map(ChatMessage::fromPayloadProto)
                .collect(Collectors.toList()));

        return trade;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // API
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void setTransientFields(Storage<? extends TradableList> storage, BtcWalletService btcWalletService) {
        this.storage = storage;
        this.btcWalletService = btcWalletService;
    }

    public void init(P2PService p2PService,
                     BtcWalletService btcWalletService,
                     BsqWalletService bsqWalletService,
                     TradeWalletService tradeWalletService,
                     DaoFacade daoFacade,
                     TradeManager tradeManager,
                     OpenOfferManager openOfferManager,
                     ReferralIdService referralIdService,
                     User user,
                     FilterManager filterManager,
                     AccountAgeWitnessService accountAgeWitnessService,
                     TradeStatisticsManager tradeStatisticsManager,
                     ArbitratorManager arbitratorManager,
                     MediatorManager mediatorManager,
                     RefundAgentManager refundAgentManager,
                     KeyRing keyRing,
                     boolean useSavingsWallet,
                     Coin fundsNeededForTrade) {
        processModel.onAllServicesInitialized(checkNotNull(offer, "offer must not be null"),
                tradeManager,
                openOfferManager,
                p2PService,
                btcWalletService,
                bsqWalletService,
                tradeWalletService,
                daoFacade,
                referralIdService,
                user,
                filterManager,
                accountAgeWitnessService,
                tradeStatisticsManager,
                arbitratorManager,
                mediatorManager,
                refundAgentManager,
                keyRing,
                useSavingsWallet,
                fundsNeededForTrade);

        arbitratorManager.getDisputeAgentByNodeAddress(arbitratorNodeAddress).ifPresent(arbitrator -> {
            arbitratorBtcPubKey = arbitrator.getBtcPubKey();
            arbitratorPubKeyRing = arbitrator.getPubKeyRing();
            persist();
        });

        mediatorManager.getDisputeAgentByNodeAddress(mediatorNodeAddress).ifPresent(mediator -> {
            mediatorPubKeyRing = mediator.getPubKeyRing();
            persist();
        });

        refundAgentManager.getDisputeAgentByNodeAddress(refundAgentNodeAddress).ifPresent(refundAgent -> {
            refundAgentPubKeyRing = refundAgent.getPubKeyRing();
            persist();
        });

        createTradeProtocol();

        // If we have already received a msg we apply it.
        // removeDecryptedMsgWithPubKey will be called synchronous after apply. We don't have threaded context
        // or async calls there.
        // Clone to avoid ConcurrentModificationException. We remove items at the applyMailboxMessage call...
        HashSet<DecryptedMessageWithPubKey> set = new HashSet<>(decryptedMessageWithPubKeySet);
        set.forEach(msg -> tradeProtocol.applyMailboxMessage(msg, this));
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // API
    ///////////////////////////////////////////////////////////////////////////////////////////

    // The deserialized tx has not actual confidence data, so we need to get the fresh one from the wallet.
    void updateDepositTxFromWallet() {
        if (getDepositTx() != null)
            applyDepositTx(processModel.getTradeWalletService().getWalletTx(getDepositTx().getHash()));
    }

    public void applyDepositTx(Transaction tx) {
        this.depositTx = tx;
        depositTxId = depositTx.getHashAsString();
        setupConfidenceListener();
        persist();
    }

    @Nullable
    public Transaction getDepositTx() {
        if (depositTx == null)
            depositTx = depositTxId != null ? btcWalletService.getTransaction(depositTxId) : null;
        return depositTx;
    }

    public void applyDelayedPayoutTx(Transaction delayedPayoutTx) {
        this.delayedPayoutTx = delayedPayoutTx;
        this.delayedPayoutTxBytes = delayedPayoutTx.bitcoinSerialize();
        persist();
    }

    public void applyDelayedPayoutTxBytes(byte[] delayedPayoutTxBytes) {
        this.delayedPayoutTxBytes = delayedPayoutTxBytes;
        persist();
    }

    @Nullable
    public Transaction getDelayedPayoutTx() {
        if (delayedPayoutTx == null) {
            delayedPayoutTx = delayedPayoutTxBytes != null && processModel.getBtcWalletService() != null ?
                    processModel.getBtcWalletService().getTxFromSerializedTx(delayedPayoutTxBytes) :
                    null;
        }
        return delayedPayoutTx;
    }

    // We don't need to persist the msg as if we don't apply it it will not be removed from the P2P network and we
    // will receive it again on next startup. This might happen in edge cases when the user shuts down after we
    // received the msg but before the init is called.
    void addDecryptedMessageWithPubKey(DecryptedMessageWithPubKey decryptedMessageWithPubKey) {
        if (!decryptedMessageWithPubKeySet.contains(decryptedMessageWithPubKey)) {
            decryptedMessageWithPubKeySet.add(decryptedMessageWithPubKey);

            // If we have already initialized we apply.
            // removeDecryptedMsgWithPubKey will be called synchronous after apply. We don't have threaded context
            // or async calls there.
            if (tradeProtocol != null)
                tradeProtocol.applyMailboxMessage(decryptedMessageWithPubKey, this);
        }
    }

    public void removeDecryptedMessageWithPubKey(DecryptedMessageWithPubKey decryptedMessageWithPubKey) {
        if (decryptedMessageWithPubKeySet.contains(decryptedMessageWithPubKey))
            decryptedMessageWithPubKeySet.remove(decryptedMessageWithPubKey);
    }

    public void addAndPersistChatMessage(ChatMessage chatMessage) {
        if (!chatMessages.contains(chatMessage)) {
            chatMessages.add(chatMessage);
            storage.queueUpForSave();
        } else {
            log.error("Trade ChatMessage already exists");
        }
    }

    public void appendErrorMessage(String msg) {
        errorMessage = errorMessage == null ? msg : errorMessage + "\n" + msg;
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
    // Abstract
    ///////////////////////////////////////////////////////////////////////////////////////////

    protected abstract void createTradeProtocol();

    public abstract Coin getPayoutAmount();


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Setters
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void setState(State state) {
        log.info("Set new state at {} (id={}): {}", this.getClass().getSimpleName(), getShortId(), state);
        if (state.getPhase().ordinal() < this.state.getPhase().ordinal()) {
            String message = "We got a state change to a previous phase.\n" +
                    "Old state is: " + this.state + ". New state is: " + state;
            log.warn(message);
        }

        boolean changed = this.state != state;
        this.state = state;
        stateProperty.set(state);
        statePhaseProperty.set(state.getPhase());

        if (state == State.WITHDRAW_COMPLETED && tradeProtocol != null)
            tradeProtocol.completed();

        if (changed)
            persist();
    }

    public void setDisputeState(DisputeState disputeState) {
        boolean changed = this.disputeState != disputeState;
        this.disputeState = disputeState;
        disputeStateProperty.set(disputeState);
        if (changed)
            persist();
    }

    public void setMediationResultState(MediationResultState mediationResultState) {
        boolean changed = this.mediationResultState != mediationResultState;
        this.mediationResultState = mediationResultState;
        mediationResultStateProperty.set(mediationResultState);
        if (changed)
            persist();
    }

    public void setRefundResultState(RefundResultState refundResultState) {
        boolean changed = this.refundResultState != refundResultState;
        this.refundResultState = refundResultState;
        refundResultStateProperty.set(refundResultState);
        if (changed)
            persist();
    }


    public void setTradePeriodState(TradePeriodState tradePeriodState) {
        boolean changed = this.tradePeriodState != tradePeriodState;
        this.tradePeriodState = tradePeriodState;
        tradePeriodStateProperty.set(tradePeriodState);
        if (changed)
            persist();
    }

    public void setTradingPeerNodeAddress(NodeAddress tradingPeerNodeAddress) {
        if (tradingPeerNodeAddress == null)
            log.error("tradingPeerAddress=null");
        else
            this.tradingPeerNodeAddress = tradingPeerNodeAddress;
    }

    public void setTradeAmount(Coin tradeAmount) {
        this.tradeAmount = tradeAmount;
        tradeAmountAsLong = tradeAmount.value;
        getTradeAmountProperty().set(tradeAmount);
        getTradeVolumeProperty().set(getTradeVolume());
    }

    public void setPayoutTx(Transaction payoutTx) {
        this.payoutTx = payoutTx;
        payoutTxId = payoutTx.getHashAsString();
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
        errorMessageProperty.set(errorMessage);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Getter
    ///////////////////////////////////////////////////////////////////////////////////////////

    public Date getTakeOfferDate() {
        return new Date(takeOfferDate);
    }

    @Nullable
    public Volume getTradeVolume() {
        if (getTradeAmount() != null && getTradePrice() != null) {
            Volume volumeByAmount = getTradePrice().getVolumeByAmount(getTradeAmount());
            if (offer != null) {
                if (offer.getPaymentMethod().getId().equals(PaymentMethod.HAL_CASH_ID))
                    volumeByAmount = OfferUtil.getAdjustedVolumeForHalCash(volumeByAmount);
                else if (CurrencyUtil.isFiatCurrency(offer.getCurrencyCode()))
                    volumeByAmount = OfferUtil.getRoundedFiatVolume(volumeByAmount);
            }
            return volumeByAmount;
        } else {
            return null;
        }
    }

    public Date getHalfTradePeriodDate() {
        return new Date(getTradeStartTime() + getMaxTradePeriod() / 2);
    }

    public Date getMaxTradePeriodDate() {
        return new Date(getTradeStartTime() + getMaxTradePeriod());
    }

    private long getMaxTradePeriod() {
        return getOffer().getPaymentMethod().getMaxTradePeriod();
    }

    private long getTradeStartTime() {
        final long now = System.currentTimeMillis();
        long startTime;
        final Transaction depositTx = getDepositTx();
        if (depositTx != null && getTakeOfferDate() != null) {
            if (depositTx.getConfidence().getDepthInBlocks() > 0) {
                final long tradeTime = getTakeOfferDate().getTime();
                // Use tx.getIncludedInBestChainAt() when available, otherwise use tx.getUpdateTime()
                long blockTime = depositTx.getIncludedInBestChainAt() != null ? depositTx.getIncludedInBestChainAt().getTime() : depositTx.getUpdateTime().getTime();
                // If block date is in future (Date in Bitcoin blocks can be off by +/- 2 hours) we use our current date.
                // If block date is earlier than our trade date we use our trade date.
                if (blockTime > now)
                    startTime = now;
                else if (blockTime < tradeTime)
                    startTime = tradeTime;
                else
                    startTime = blockTime;

                log.debug("We set the start for the trade period to {}. Trade started at: {}. Block got mined at: {}",
                        new Date(startTime), new Date(tradeTime), new Date(blockTime));
            } else {
                log.debug("depositTx not confirmed yet. We don't start counting remaining trade period yet. txId={}", depositTx.getHashAsString());
                startTime = now;
            }
        } else {
            log.warn("depositTx is null");
            startTime = now;
        }
        return startTime;
    }

    public boolean hasFailed() {
        return errorMessageProperty().get() != null;
    }

    public boolean isInPreparation() {
        return getState().getPhase().ordinal() == Phase.INIT.ordinal();
    }

    public boolean isTakerFeePublished() {
        return getState().getPhase().ordinal() >= Phase.TAKER_FEE_PUBLISHED.ordinal();
    }

    public boolean isDepositPublished() {
        return getState().getPhase().ordinal() >= Phase.DEPOSIT_PUBLISHED.ordinal();
    }

    public boolean isFundsLockedIn() {
        // If no deposit tx was published we have no funds locked in
        if (!isDepositPublished()) {
            return false;
        }

        // If we have the payout tx published (non disputed case) we have no funds locked in. Here we might have more
        // complex cases where users open a mediation but continue the trade to finalize it without mediated payout.
        // The trade state handles that but does not handle mediated payouts or refund agents payouts.
        if (isPayoutPublished()) {
            return false;
        }

        // Legacy arbitration is not handled anymore as not used anymore.

        // In mediation case we check for the mediationResultState. As there are multiple sub-states we use ordinal.
        if (disputeState == DisputeState.MEDIATION_CLOSED) {
            if (mediationResultState != null &&
                    mediationResultState.ordinal() >= MediationResultState.PAYOUT_TX_PUBLISHED.ordinal()) {
                return false;
            }
        }

        // In refund agent case the funds are spent anyway with the time locked payout. We do not consider that as
        // locked in funds.
        if (disputeState == DisputeState.REFUND_REQUESTED ||
                disputeState == DisputeState.REFUND_REQUEST_STARTED_BY_PEER ||
                disputeState == DisputeState.REFUND_REQUEST_CLOSED) {
            return false;
        }

        return true;
    }

    public boolean isDepositConfirmed() {
        return getState().getPhase().ordinal() >= Phase.DEPOSIT_CONFIRMED.ordinal();
    }

    public boolean isFiatSent() {
        return getState().getPhase().ordinal() >= Phase.FIAT_SENT.ordinal();
    }

    public boolean isFiatReceived() {
        return getState().getPhase().ordinal() >= Phase.FIAT_RECEIVED.ordinal();
    }

    public boolean isPayoutPublished() {
        return getState().getPhase().ordinal() >= Phase.PAYOUT_PUBLISHED.ordinal() || isWithdrawn();
    }

    public boolean isWithdrawn() {
        return getState().getPhase().ordinal() == Phase.WITHDRAWN.ordinal();
    }

    public ReadOnlyObjectProperty<State> stateProperty() {
        return stateProperty;
    }

    public ReadOnlyObjectProperty<Phase> statePhaseProperty() {
        return statePhaseProperty;
    }

    public ReadOnlyObjectProperty<DisputeState> disputeStateProperty() {
        return disputeStateProperty;
    }

    public ReadOnlyObjectProperty<MediationResultState> mediationResultStateProperty() {
        return mediationResultStateProperty;
    }

    public ReadOnlyObjectProperty<RefundResultState> refundResultStateProperty() {
        return refundResultStateProperty;
    }

    public ReadOnlyObjectProperty<TradePeriodState> tradePeriodStateProperty() {
        return tradePeriodStateProperty;
    }

    public ReadOnlyObjectProperty<Coin> tradeAmountProperty() {
        return tradeAmountProperty;
    }

    public ReadOnlyObjectProperty<Volume> tradeVolumeProperty() {
        return tradeVolumeProperty;
    }

    public ReadOnlyStringProperty errorMessageProperty() {
        return errorMessageProperty;
    }

    @Override
    public Date getDate() {
        return getTakeOfferDate();
    }

    @Override
    public String getId() {
        return offer.getId();
    }

    @Override
    public String getShortId() {
        return offer.getShortId();
    }

    public Price getTradePrice() {
        return Price.valueOf(offer.getCurrencyCode(), tradePrice);
    }

    @Nullable
    public Coin getTradeAmount() {
        if (tradeAmount == null)
            tradeAmount = Coin.valueOf(tradeAmountAsLong);
        return tradeAmount;
    }

    @Nullable
    public Transaction getPayoutTx() {
        if (payoutTx == null)
            payoutTx = payoutTxId != null ? btcWalletService.getTransaction(payoutTxId) : null;
        return payoutTx;
    }

    @Nullable
    public String getErrorMessage() {
        return errorMessageProperty.get();
    }

    public byte[] getArbitratorBtcPubKey() {
        // In case we are already in a trade the arbitrator can have been revoked and we still can complete the trade
        // Only new trades cannot start without any arbitrator
        if (arbitratorBtcPubKey == null) {
            Arbitrator arbitrator = processModel.getUser().getAcceptedArbitratorByAddress(arbitratorNodeAddress);
            checkNotNull(arbitrator, "arbitrator must not be null");
            arbitratorBtcPubKey = arbitrator.getBtcPubKey();
        }

        checkNotNull(arbitratorBtcPubKey, "ArbitratorPubKey must not be null");
        return arbitratorBtcPubKey;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Private
    ///////////////////////////////////////////////////////////////////////////////////////////

    // lazy initialization
    private ObjectProperty<Coin> getTradeAmountProperty() {
        if (tradeAmountProperty == null)
            tradeAmountProperty = getTradeAmount() != null ? new SimpleObjectProperty<>(getTradeAmount()) : new SimpleObjectProperty<>();

        return tradeAmountProperty;
    }

    // lazy initialization
    private ObjectProperty<Volume> getTradeVolumeProperty() {
        if (tradeVolumeProperty == null)
            tradeVolumeProperty = getTradeVolume() != null ? new SimpleObjectProperty<>(getTradeVolume()) : new SimpleObjectProperty<>();
        return tradeVolumeProperty;
    }

    private void setupConfidenceListener() {
        if (getDepositTx() != null) {
            TransactionConfidence transactionConfidence = getDepositTx().getConfidence();
            if (transactionConfidence.getConfidenceType() == TransactionConfidence.ConfidenceType.BUILDING) {
                setConfirmedState();
            } else {
                ListenableFuture<TransactionConfidence> future = transactionConfidence.getDepthFuture(1);
                Futures.addCallback(future, new FutureCallback<TransactionConfidence>() {
                    @Override
                    public void onSuccess(TransactionConfidence result) {
                        setConfirmedState();
                    }

                    @Override
                    public void onFailure(@NotNull Throwable t) {
                        t.printStackTrace();
                        log.error(t.getMessage());
                        throw new RuntimeException(t);
                    }
                });
            }
        } else {
            log.error("depositTx == null. That must not happen.");
        }
    }

    private void setConfirmedState() {
        // we only apply the state if we are not already further in the process
        if (!isDepositConfirmed())
            setState(State.DEPOSIT_CONFIRMED_IN_BLOCK_CHAIN);
    }

    @Override
    public String toString() {
        return "Trade{" +
                "\n     offer=" + offer +
                ",\n     isCurrencyForTakerFeeBtc=" + isCurrencyForTakerFeeBtc +
                ",\n     txFeeAsLong=" + txFeeAsLong +
                ",\n     takerFeeAsLong=" + takerFeeAsLong +
                ",\n     takeOfferDate=" + takeOfferDate +
                ",\n     processModel=" + processModel +
                ",\n     takerFeeTxId='" + takerFeeTxId + '\'' +
                ",\n     depositTxId='" + depositTxId + '\'' +
                ",\n     payoutTxId='" + payoutTxId + '\'' +
                ",\n     tradeAmountAsLong=" + tradeAmountAsLong +
                ",\n     tradePrice=" + tradePrice +
                ",\n     tradingPeerNodeAddress=" + tradingPeerNodeAddress +
                ",\n     state=" + state +
                ",\n     disputeState=" + disputeState +
                ",\n     tradePeriodState=" + tradePeriodState +
                ",\n     contract=" + contract +
                ",\n     contractAsJson='" + contractAsJson + '\'' +
                ",\n     contractHash=" + Utilities.bytesAsHexString(contractHash) +
                ",\n     takerContractSignature='" + takerContractSignature + '\'' +
                ",\n     makerContractSignature='" + makerContractSignature + '\'' +
                ",\n     arbitratorNodeAddress=" + arbitratorNodeAddress +
                ",\n     arbitratorBtcPubKey=" + Utilities.bytesAsHexString(arbitratorBtcPubKey) +
                ",\n     arbitratorPubKeyRing=" + arbitratorPubKeyRing +
                ",\n     mediatorNodeAddress=" + mediatorNodeAddress +
                ",\n     mediatorPubKeyRing=" + mediatorPubKeyRing +
                ",\n     takerPaymentAccountId='" + takerPaymentAccountId + '\'' +
                ",\n     errorMessage='" + errorMessage + '\'' +
                ",\n     counterCurrencyTxId='" + counterCurrencyTxId + '\'' +
                ",\n     chatMessages=" + chatMessages +
                ",\n     txFee=" + txFee +
                ",\n     takerFee=" + takerFee +
                ",\n     storage=" + storage +
                ",\n     btcWalletService=" + btcWalletService +
                ",\n     stateProperty=" + stateProperty +
                ",\n     statePhaseProperty=" + statePhaseProperty +
                ",\n     disputeStateProperty=" + disputeStateProperty +
                ",\n     tradePeriodStateProperty=" + tradePeriodStateProperty +
                ",\n     errorMessageProperty=" + errorMessageProperty +
                ",\n     tradeProtocol=" + tradeProtocol +
                ",\n     depositTx=" + depositTx +
                ",\n     delayedPayoutTx=" + delayedPayoutTx +
                ",\n     payoutTx=" + payoutTx +
                ",\n     tradeAmount=" + tradeAmount +
                ",\n     tradeAmountProperty=" + tradeAmountProperty +
                ",\n     tradeVolumeProperty=" + tradeVolumeProperty +
                ",\n     decryptedMessageWithPubKeySet=" + decryptedMessageWithPubKeySet +
                ",\n     mediationResultState=" + mediationResultState +
                ",\n     mediationResultStateProperty=" + mediationResultStateProperty +
                ",\n     lockTime=" + lockTime +
                ",\n     delayedPayoutTxBytes=" + Utilities.bytesAsHexString(delayedPayoutTxBytes) +
                ",\n     refundAgentNodeAddress=" + refundAgentNodeAddress +
                ",\n     refundAgentPubKeyRing=" + refundAgentPubKeyRing +
                ",\n     refundResultState=" + refundResultState +
                ",\n     refundResultStateProperty=" + refundResultStateProperty +
                "\n}";
    }
}
