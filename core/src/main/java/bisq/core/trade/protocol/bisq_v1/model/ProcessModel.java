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

package bisq.core.trade.protocol.bisq_v1.model;

import bisq.core.account.witness.AccountAgeWitnessService;
import bisq.core.btc.model.RawTransactionInput;
import bisq.core.btc.wallet.BsqWalletService;
import bisq.core.btc.wallet.BtcWalletService;
import bisq.core.btc.wallet.TradeWalletService;
import bisq.core.dao.DaoFacade;
import bisq.core.dao.burningman.BtcFeeReceiverService;
import bisq.core.dao.burningman.DelayedPayoutTxReceiverService;
import bisq.core.filter.FilterManager;
import bisq.core.network.MessageState;
import bisq.core.offer.Offer;
import bisq.core.offer.OpenOfferManager;
import bisq.core.payment.PaymentAccount;
import bisq.core.payment.payload.PaymentAccountPayload;
import bisq.core.proto.CoreProtoResolver;
import bisq.core.support.dispute.arbitration.arbitrator.ArbitratorManager;
import bisq.core.support.dispute.mediation.mediator.MediatorManager;
import bisq.core.support.dispute.refund.refundagent.RefundAgentManager;
import bisq.core.trade.TradeManager;
import bisq.core.trade.model.MakerTrade;
import bisq.core.trade.model.bisq_v1.Trade;
import bisq.core.trade.protocol.ProtocolModel;
import bisq.core.trade.protocol.Provider;
import bisq.core.trade.protocol.TradeMessage;
import bisq.core.trade.statistics.ReferralIdService;
import bisq.core.trade.statistics.TradeStatisticsManager;
import bisq.core.user.User;

import bisq.network.p2p.AckMessage;
import bisq.network.p2p.NodeAddress;
import bisq.network.p2p.P2PService;

import bisq.common.crypto.Hash;
import bisq.common.crypto.KeyRing;
import bisq.common.crypto.PubKeyRing;
import bisq.common.proto.ProtoUtil;

import com.google.protobuf.ByteString;

import org.bitcoinj.core.Coin;
import org.bitcoinj.core.Transaction;

import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import javax.annotation.Nullable;

import static com.google.common.base.Preconditions.checkNotNull;

// Fields marked as transient are only used during protocol execution which are based on directMessages so we do not
// persist them.

/**
 * This is the base model for the trade protocol. It is persisted with the trade (non-transient fields).
 * It uses the {@link Provider} for access to domain services.
 */

@Getter
@Slf4j
public class ProcessModel implements ProtocolModel<TradingPeer> {

    public static byte[] hashOfPaymentAccountPayload(PaymentAccountPayload paymentAccountPayload) {
        return Hash.getRipemd160hash(checkNotNull(paymentAccountPayload).serializeForHash());
    }

    // Transient/Immutable (net set in constructor so they are not final, but at init)
    transient protected Provider provider;
    transient protected TradeManager tradeManager;
    transient protected Offer offer;

    // Transient/Mutable
    transient protected Transaction takeOfferFeeTx;
    @Setter
    transient protected TradeMessage tradeMessage;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Added in v 1.9.13 for trade protocol 5
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Setter
    transient private String warningTxFeeBumpAddress;
    @Setter
    transient private String redirectTxFeeBumpAddress;

    @Setter
    transient private Transaction warningTx;
    @Setter
    transient private byte[] warningTxSellerSignature;
    @Setter
    transient private byte[] warningTxBuyerSignature;
    @Setter
    @Nullable
    private byte[] finalizedWarningTx;

    @Setter
    transient private Transaction redirectTx;
    @Setter
    transient private byte[] redirectTxSellerSignature;
    @Setter
    transient private byte[] redirectTxBuyerSignature;
    @Setter
    @Nullable
    byte[] finalizedRedirectTx;

    @Setter
    @Nullable
    private byte[] signedClaimTx;


    // Added in v1.2.0
    @Setter
    @Nullable
    transient protected byte[] delayedPayoutTxSignature;
    @Setter
    @Nullable
    transient protected Transaction preparedDelayedPayoutTx;


    // Added in v1.4.0
    // MessageState of the last message sent from the seller to the buyer in the take offer process.
    // It is used only in a task which would not be executed after restart, so no need to persist it.
    @Setter
    transient protected ObjectProperty<MessageState> depositTxMessageStateProperty = new SimpleObjectProperty<>(MessageState.UNDEFINED);
    @Setter
    transient protected Transaction depositTx;

    // Persistable Immutable
    protected final String offerId;
    protected final PubKeyRing pubKeyRing;

    // Persistable Mutable
    protected TradingPeer tradingPeer;
    @Nullable
    @Setter
    protected String takeOfferFeeTxId;
    @Nullable
    @Setter
    protected byte[] payoutTxSignature;
    @Nullable
    @Setter
    protected byte[] preparedDepositTx;
    @Nullable
    @Setter
    protected List<RawTransactionInput> rawTransactionInputs;
    @Setter
    protected long changeOutputValue;
    @Nullable
    @Setter
    protected String changeOutputAddress;
    @Setter
    protected boolean useSavingsWallet;
    @Setter
    protected long fundsNeededForTradeAsLong;
    @Nullable
    @Setter
    protected byte[] myMultiSigPubKey;
    // that is used to store temp. the peers address when we get an incoming message before the message is verified.
    // After successful verified we copy that over to the trade.tradingPeerAddress
    @Nullable
    @Setter
    protected NodeAddress tempTradingPeerNodeAddress;

    // Added in v.1.1.6
    @Nullable
    @Setter
    protected byte[] mediatedPayoutTxSignature;
    @Setter
    protected long buyerPayoutAmountFromMediation;
    @Setter
    protected long sellerPayoutAmountFromMediation;

    // Was changed at v1.9.2 from immutable to mutable
    @Setter
    protected String accountId;

    // Was added at v1.9.2
    @Setter
    @Nullable
    protected PaymentAccount paymentAccount;


    // We want to indicate the user the state of the message delivery of the
    // CounterCurrencyTransferStartedMessage. As well we do an automatic re-send in case it was not ACKed yet.
    // To enable that even after restart we persist the state.
    @Setter
    protected ObjectProperty<MessageState> paymentStartedMessageStateProperty = new SimpleObjectProperty<>(MessageState.UNDEFINED);

    // Added in v 1.9.7
    @Setter
    protected int burningManSelectionHeight;

    public ProcessModel(String offerId, String accountId, PubKeyRing pubKeyRing) {
        this(offerId, accountId, pubKeyRing, new TradingPeer());
    }

    public ProcessModel(String offerId, String accountId, PubKeyRing pubKeyRing, TradingPeer tradingPeer) {
        this.offerId = offerId;
        this.accountId = accountId;
        this.pubKeyRing = pubKeyRing;
        // If tradingPeer was null in persisted data from some error cases we set a new one to not cause nullPointers
        this.tradingPeer = tradingPeer != null ? tradingPeer : new TradingPeer();
    }

    public void applyTransient(Provider provider, TradeManager tradeManager, Offer offer) {
        this.offer = offer;
        this.provider = provider;
        this.tradeManager = tradeManager;
    }

    public void applyPaymentAccount(Trade trade) {
        paymentAccount = trade instanceof MakerTrade ?
                getUser().getPaymentAccount(offer.getMakerPaymentAccountId()) :
                getUser().getPaymentAccount(trade.getTakerPaymentAccountId());
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // PROTO BUFFER
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public protobuf.ProcessModel toProtoMessage() {
        protobuf.ProcessModel.Builder builder = protobuf.ProcessModel.newBuilder()
                .setTradingPeer((protobuf.TradingPeer) tradingPeer.toProtoMessage())
                .setOfferId(offerId)
                .setAccountId(accountId)
                .setPubKeyRing(pubKeyRing.toProtoMessage())
                .setChangeOutputValue(changeOutputValue)
                .setUseSavingsWallet(useSavingsWallet)
                .setFundsNeededForTradeAsLong(fundsNeededForTradeAsLong)
                .setPaymentStartedMessageState(paymentStartedMessageStateProperty.get().name())
                .setBuyerPayoutAmountFromMediation(buyerPayoutAmountFromMediation)
                .setSellerPayoutAmountFromMediation(sellerPayoutAmountFromMediation)
                .setBurningManSelectionHeight(burningManSelectionHeight);

        Optional.ofNullable(takeOfferFeeTxId).ifPresent(builder::setTakeOfferFeeTxId);
        Optional.ofNullable(payoutTxSignature).ifPresent(e -> builder.setPayoutTxSignature(ByteString.copyFrom(payoutTxSignature)));
        Optional.ofNullable(preparedDepositTx).ifPresent(e -> builder.setPreparedDepositTx(ByteString.copyFrom(preparedDepositTx)));
        Optional.ofNullable(finalizedWarningTx).ifPresent(e -> builder.setFinalizedWarningTx(ByteString.copyFrom(finalizedWarningTx)));
        Optional.ofNullable(finalizedRedirectTx).ifPresent(e -> builder.setFinalizedRedirectTx(ByteString.copyFrom(finalizedRedirectTx)));
        Optional.ofNullable(signedClaimTx).ifPresent(e -> builder.setSignedClaimTx(ByteString.copyFrom(signedClaimTx)));
        Optional.ofNullable(rawTransactionInputs).ifPresent(e -> builder.addAllRawTransactionInputs(
                ProtoUtil.collectionToProto(rawTransactionInputs, protobuf.RawTransactionInput.class)));
        Optional.ofNullable(changeOutputAddress).ifPresent(builder::setChangeOutputAddress);
        Optional.ofNullable(myMultiSigPubKey).ifPresent(e -> builder.setMyMultiSigPubKey(ByteString.copyFrom(myMultiSigPubKey)));
        Optional.ofNullable(tempTradingPeerNodeAddress).ifPresent(e -> builder.setTempTradingPeerNodeAddress(tempTradingPeerNodeAddress.toProtoMessage()));
        Optional.ofNullable(mediatedPayoutTxSignature).ifPresent(e -> builder.setMediatedPayoutTxSignature(ByteString.copyFrom(e)));
        Optional.ofNullable(paymentAccount).ifPresent(e -> builder.setPaymentAccount(e.toProtoMessage()));

        return builder.build();
    }

    public static ProcessModel fromProto(protobuf.ProcessModel proto, CoreProtoResolver coreProtoResolver) {
        TradingPeer tradingPeer = TradingPeer.fromProto(proto.getTradingPeer(), coreProtoResolver);
        PubKeyRing pubKeyRing = PubKeyRing.fromProto(proto.getPubKeyRing());
        ProcessModel processModel = new ProcessModel(proto.getOfferId(), proto.getAccountId(), pubKeyRing, tradingPeer);
        processModel.setChangeOutputValue(proto.getChangeOutputValue());
        processModel.setUseSavingsWallet(proto.getUseSavingsWallet());
        processModel.setFundsNeededForTradeAsLong(proto.getFundsNeededForTradeAsLong());
        processModel.setBuyerPayoutAmountFromMediation(proto.getBuyerPayoutAmountFromMediation());
        processModel.setSellerPayoutAmountFromMediation(proto.getSellerPayoutAmountFromMediation());

        // nullable
        processModel.setTakeOfferFeeTxId(ProtoUtil.stringOrNullFromProto(proto.getTakeOfferFeeTxId()));
        processModel.setPayoutTxSignature(ProtoUtil.byteArrayOrNullFromProto(proto.getPayoutTxSignature()));
        processModel.setPreparedDepositTx(ProtoUtil.byteArrayOrNullFromProto(proto.getPreparedDepositTx()));
        processModel.setFinalizedWarningTx(ProtoUtil.byteArrayOrNullFromProto(proto.getFinalizedWarningTx()));
        processModel.setFinalizedRedirectTx(ProtoUtil.byteArrayOrNullFromProto(proto.getFinalizedRedirectTx()));
        processModel.setSignedClaimTx(ProtoUtil.byteArrayOrNullFromProto(proto.getSignedClaimTx()));
        List<RawTransactionInput> rawTransactionInputs = proto.getRawTransactionInputsList().isEmpty() ?
                null : proto.getRawTransactionInputsList().stream()
                .map(RawTransactionInput::fromProto).collect(Collectors.toList());
        processModel.setRawTransactionInputs(rawTransactionInputs);
        processModel.setChangeOutputAddress(ProtoUtil.stringOrNullFromProto(proto.getChangeOutputAddress()));
        processModel.setMyMultiSigPubKey(ProtoUtil.byteArrayOrNullFromProto(proto.getMyMultiSigPubKey()));
        processModel.setTempTradingPeerNodeAddress(proto.hasTempTradingPeerNodeAddress() ? NodeAddress.fromProto(proto.getTempTradingPeerNodeAddress()) : null);
        processModel.setMediatedPayoutTxSignature(ProtoUtil.byteArrayOrNullFromProto(proto.getMediatedPayoutTxSignature()));

        String paymentStartedMessageStateString = ProtoUtil.stringOrNullFromProto(proto.getPaymentStartedMessageState());
        MessageState paymentStartedMessageState = ProtoUtil.enumFromProto(MessageState.class, paymentStartedMessageStateString);
        processModel.setPaymentStartedMessageState(paymentStartedMessageState);
        processModel.setBurningManSelectionHeight(proto.getBurningManSelectionHeight());

        if (proto.hasPaymentAccount()) {
            processModel.setPaymentAccount(PaymentAccount.fromProto(proto.getPaymentAccount(), coreProtoResolver));
        }
        return processModel;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // API
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void onComplete() {
    }

    @Override
    public TradingPeer getTradePeer() {
        return tradingPeer;
    }

    public void setTakeOfferFeeTx(Transaction takeOfferFeeTx) {
        this.takeOfferFeeTx = takeOfferFeeTx;
        takeOfferFeeTxId = takeOfferFeeTx.getTxId().toString();
    }

    @Nullable
    public PaymentAccountPayload getPaymentAccountPayload(Trade trade) {
        if (paymentAccount == null) {
            // Persisted trades pre v 1.9.2 have no paymentAccount set, so it will be null.
            // We do not need to persist it (though it would not hurt as well).
            applyPaymentAccount(trade);
        }

        return paymentAccount.getPaymentAccountPayload();
    }

    public Coin getFundsNeededForTrade() {
        return Coin.valueOf(fundsNeededForTradeAsLong);
    }

    public Transaction resolveTakeOfferFeeTx(Trade trade) {
        if (takeOfferFeeTx == null) {
            if (!trade.isCurrencyForTakerFeeBtc())
                takeOfferFeeTx = getBsqWalletService().getTransaction(takeOfferFeeTxId);
            else
                takeOfferFeeTx = getBtcWalletService().getTransaction(takeOfferFeeTxId);
        }
        return takeOfferFeeTx;
    }

    @Override
    public NodeAddress getMyNodeAddress() {
        return getP2PService().getAddress();
    }

    public void setPaymentStartedAckMessage(AckMessage ackMessage) {
        MessageState messageState = ackMessage.isSuccess() ?
                MessageState.ACKNOWLEDGED :
                MessageState.FAILED;
        setPaymentStartedMessageState(messageState);
    }

    public void setPaymentStartedMessageState(MessageState paymentStartedMessageStateProperty) {
        this.paymentStartedMessageStateProperty.set(paymentStartedMessageStateProperty);
        if (tradeManager != null) {
            tradeManager.requestPersistence();
        }
    }

    public void setDepositTxSentAckMessage(AckMessage ackMessage) {
        MessageState messageState = ackMessage.isSuccess() ?
                MessageState.ACKNOWLEDGED :
                MessageState.FAILED;
        setDepositTxMessageState(messageState);
    }

    public void setDepositTxMessageState(MessageState messageState) {
        this.depositTxMessageStateProperty.set(messageState);
        if (tradeManager != null) {
            tradeManager.requestPersistence();
        }
    }

    public boolean maybeClearSensitiveData(boolean keepStagedTxs) {
        boolean changed = false;
        if (tradingPeer.getPaymentAccountPayload() != null || tradingPeer.getContractAsJson() != null) {
            // If tradingPeer was null in persisted data from some error cases we set a new one to not cause nullPointers
            var newTradingPeer = new TradingPeer();
            // Try to keep peer's staged txs (which are sensitive due to fee bump outputs) if any staged tx was broadcast.
            // (They might have been deleted anyway if the trade was un-failed, but are not essential.)
            if (keepStagedTxs) {
                newTradingPeer.setFinalizedWarningTx(tradingPeer.getFinalizedWarningTx());
                newTradingPeer.setFinalizedRedirectTx(tradingPeer.getFinalizedRedirectTx());
                newTradingPeer.setClaimTx(tradingPeer.getClaimTx());
            }
            this.tradingPeer = newTradingPeer;
            changed = true;
        }
        return changed;
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Delegates
    ///////////////////////////////////////////////////////////////////////////////////////////

    public BtcWalletService getBtcWalletService() {
        return provider.getBtcWalletService();
    }

    public AccountAgeWitnessService getAccountAgeWitnessService() {
        return provider.getAccountAgeWitnessService();
    }

    public P2PService getP2PService() {
        return provider.getP2PService();
    }

    public BsqWalletService getBsqWalletService() {
        return provider.getBsqWalletService();
    }

    public TradeWalletService getTradeWalletService() {
        return provider.getTradeWalletService();
    }

    public BtcFeeReceiverService getBtcFeeReceiverService() {
        return provider.getBtcFeeReceiverService();
    }

    public DelayedPayoutTxReceiverService getDelayedPayoutTxReceiverService() {
        return provider.getDelayedPayoutTxReceiverService();
    }

    public User getUser() {
        return provider.getUser();
    }

    public OpenOfferManager getOpenOfferManager() {
        return provider.getOpenOfferManager();
    }

    public ReferralIdService getReferralIdService() {
        return provider.getReferralIdService();
    }

    public FilterManager getFilterManager() {
        return provider.getFilterManager();
    }

    public TradeStatisticsManager getTradeStatisticsManager() {
        return provider.getTradeStatisticsManager();
    }

    public ArbitratorManager getArbitratorManager() {
        return provider.getArbitratorManager();
    }

    public MediatorManager getMediatorManager() {
        return provider.getMediatorManager();
    }

    public RefundAgentManager getRefundAgentManager() {
        return provider.getRefundAgentManager();
    }

    public KeyRing getKeyRing() {
        return provider.getKeyRing();
    }

    public DaoFacade getDaoFacade() {
        return provider.getDaoFacade();
    }
}
