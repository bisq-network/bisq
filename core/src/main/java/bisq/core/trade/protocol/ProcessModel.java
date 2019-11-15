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

package bisq.core.trade.protocol;

import bisq.core.account.witness.AccountAgeWitnessService;
import bisq.core.btc.model.RawTransactionInput;
import bisq.core.btc.wallet.BsqWalletService;
import bisq.core.btc.wallet.BtcWalletService;
import bisq.core.btc.wallet.TradeWalletService;
import bisq.core.dao.DaoFacade;
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
import bisq.core.trade.MakerTrade;
import bisq.core.trade.Trade;
import bisq.core.trade.TradeManager;
import bisq.core.trade.messages.TradeMessage;
import bisq.core.trade.statistics.ReferralIdService;
import bisq.core.trade.statistics.TradeStatisticsManager;
import bisq.core.user.User;

import bisq.network.p2p.AckMessage;
import bisq.network.p2p.DecryptedMessageWithPubKey;
import bisq.network.p2p.MailboxMessage;
import bisq.network.p2p.NodeAddress;
import bisq.network.p2p.P2PService;

import bisq.common.crypto.KeyRing;
import bisq.common.crypto.PubKeyRing;
import bisq.common.proto.ProtoUtil;
import bisq.common.proto.persistable.PersistablePayload;
import bisq.common.taskrunner.Model;

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

// Fields marked as transient are only used during protocol execution which are based on directMessages so we do not
// persist them.
//todo clean up older fields as well to make most transient

@Getter
@Slf4j
public class ProcessModel implements Model, PersistablePayload {
    // Transient/Immutable (net set in constructor so they are not final, but at init)
    transient private TradeManager tradeManager;
    transient private OpenOfferManager openOfferManager;
    transient private BtcWalletService btcWalletService;
    transient private BsqWalletService bsqWalletService;
    transient private TradeWalletService tradeWalletService;
    transient private DaoFacade daoFacade;
    transient private Offer offer;
    transient private User user;
    transient private FilterManager filterManager;
    transient private AccountAgeWitnessService accountAgeWitnessService;
    transient private TradeStatisticsManager tradeStatisticsManager;
    transient private ArbitratorManager arbitratorManager;
    transient private MediatorManager mediatorManager;
    transient private RefundAgentManager refundAgentManager;
    transient private KeyRing keyRing;
    transient private P2PService p2PService;
    transient private ReferralIdService referralIdService;

    // Transient/Mutable
    transient private Transaction takeOfferFeeTx;
    @Setter
    transient private TradeMessage tradeMessage;
    @Setter
    transient private DecryptedMessageWithPubKey decryptedMessageWithPubKey;

    // Added in v1.2.0
    @Setter
    @Nullable
    transient private byte[] delayedPayoutTxSignature;
    @Setter
    @Nullable
    transient private Transaction preparedDelayedPayoutTx;

    // Persistable Immutable (private setter only used by PB method)
    private TradingPeer tradingPeer = new TradingPeer();
    private String offerId;
    private String accountId;
    private PubKeyRing pubKeyRing;

    // Persistable Mutable
    @Nullable
    @Setter()
    private String takeOfferFeeTxId;
    @Nullable
    @Setter
    private byte[] payoutTxSignature;
    @Nullable
    @Setter
    private byte[] preparedDepositTx;
    @Nullable
    @Setter
    private List<RawTransactionInput> rawTransactionInputs;
    @Setter
    private long changeOutputValue;
    @Nullable
    @Setter
    private String changeOutputAddress;
    @Setter
    private boolean useSavingsWallet;
    @Setter
    private long fundsNeededForTradeAsLong;
    @Nullable
    @Setter
    private byte[] myMultiSigPubKey;
    // that is used to store temp. the peers address when we get an incoming message before the message is verified.
    // After successful verified we copy that over to the trade.tradingPeerAddress
    @Nullable
    @Setter
    private NodeAddress tempTradingPeerNodeAddress;

    // Added in v.1.1.6
    @Nullable
    @Setter
    private byte[] mediatedPayoutTxSignature;
    @Setter
    private long buyerPayoutAmountFromMediation;
    @Setter
    private long sellerPayoutAmountFromMediation;

    // The only trade message where we want to indicate the user the state of the message delivery is the
    // CounterCurrencyTransferStartedMessage. We persist the state with the processModel.
    @Setter
    private ObjectProperty<MessageState> paymentStartedMessageStateProperty = new SimpleObjectProperty<>(MessageState.UNDEFINED);

    public ProcessModel() {
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // PROTO BUFFER
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public protobuf.ProcessModel toProtoMessage() {
        final protobuf.ProcessModel.Builder builder = protobuf.ProcessModel.newBuilder()
                .setTradingPeer((protobuf.TradingPeer) tradingPeer.toProtoMessage())
                .setOfferId(offerId)
                .setAccountId(accountId)
                .setPubKeyRing(pubKeyRing.toProtoMessage())
                .setChangeOutputValue(changeOutputValue)
                .setUseSavingsWallet(useSavingsWallet)
                .setFundsNeededForTradeAsLong(fundsNeededForTradeAsLong)
                .setPaymentStartedMessageState(paymentStartedMessageStateProperty.get().name())
                .setBuyerPayoutAmountFromMediation(buyerPayoutAmountFromMediation)
                .setSellerPayoutAmountFromMediation(sellerPayoutAmountFromMediation);

        Optional.ofNullable(takeOfferFeeTxId).ifPresent(builder::setTakeOfferFeeTxId);
        Optional.ofNullable(payoutTxSignature).ifPresent(e -> builder.setPayoutTxSignature(ByteString.copyFrom(payoutTxSignature)));
        Optional.ofNullable(preparedDepositTx).ifPresent(e -> builder.setPreparedDepositTx(ByteString.copyFrom(preparedDepositTx)));
        Optional.ofNullable(rawTransactionInputs).ifPresent(e -> builder.addAllRawTransactionInputs(
                ProtoUtil.collectionToProto(rawTransactionInputs, protobuf.RawTransactionInput.class)));
        Optional.ofNullable(changeOutputAddress).ifPresent(builder::setChangeOutputAddress);
        Optional.ofNullable(myMultiSigPubKey).ifPresent(e -> builder.setMyMultiSigPubKey(ByteString.copyFrom(myMultiSigPubKey)));
        Optional.ofNullable(tempTradingPeerNodeAddress).ifPresent(e -> builder.setTempTradingPeerNodeAddress(tempTradingPeerNodeAddress.toProtoMessage()));
        Optional.ofNullable(mediatedPayoutTxSignature).ifPresent(e -> builder.setMediatedPayoutTxSignature(ByteString.copyFrom(e)));

        return builder.build();
    }

    public static ProcessModel fromProto(protobuf.ProcessModel proto, CoreProtoResolver coreProtoResolver) {
        ProcessModel processModel = new ProcessModel();
        processModel.setTradingPeer(proto.hasTradingPeer() ? TradingPeer.fromProto(proto.getTradingPeer(), coreProtoResolver) : null);
        processModel.setOfferId(proto.getOfferId());
        processModel.setAccountId(proto.getAccountId());
        processModel.setPubKeyRing(PubKeyRing.fromProto(proto.getPubKeyRing()));
        processModel.setChangeOutputValue(proto.getChangeOutputValue());
        processModel.setUseSavingsWallet(proto.getUseSavingsWallet());
        processModel.setFundsNeededForTradeAsLong(proto.getFundsNeededForTradeAsLong());
        processModel.setBuyerPayoutAmountFromMediation(proto.getBuyerPayoutAmountFromMediation());
        processModel.setSellerPayoutAmountFromMediation(proto.getSellerPayoutAmountFromMediation());

        // nullable
        processModel.setTakeOfferFeeTxId(ProtoUtil.stringOrNullFromProto(proto.getTakeOfferFeeTxId()));
        processModel.setPayoutTxSignature(ProtoUtil.byteArrayOrNullFromProto(proto.getPayoutTxSignature()));
        processModel.setPreparedDepositTx(ProtoUtil.byteArrayOrNullFromProto(proto.getPreparedDepositTx()));
        List<RawTransactionInput> rawTransactionInputs = proto.getRawTransactionInputsList().isEmpty() ?
                null : proto.getRawTransactionInputsList().stream()
                .map(RawTransactionInput::fromProto).collect(Collectors.toList());
        processModel.setRawTransactionInputs(rawTransactionInputs);
        processModel.setChangeOutputAddress(ProtoUtil.stringOrNullFromProto(proto.getChangeOutputAddress()));
        processModel.setMyMultiSigPubKey(ProtoUtil.byteArrayOrNullFromProto(proto.getMyMultiSigPubKey()));
        processModel.setTempTradingPeerNodeAddress(proto.hasTempTradingPeerNodeAddress() ? NodeAddress.fromProto(proto.getTempTradingPeerNodeAddress()) : null);
        String paymentStartedMessageState = proto.getPaymentStartedMessageState().isEmpty() ? MessageState.UNDEFINED.name() : proto.getPaymentStartedMessageState();
        ObjectProperty<MessageState> paymentStartedMessageStateProperty = processModel.getPaymentStartedMessageStateProperty();
        paymentStartedMessageStateProperty.set(ProtoUtil.enumFromProto(MessageState.class, paymentStartedMessageState));
        processModel.setMediatedPayoutTxSignature(ProtoUtil.byteArrayOrNullFromProto(proto.getMediatedPayoutTxSignature()));
        return processModel;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // API
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void onAllServicesInitialized(Offer offer,
                                         TradeManager tradeManager,
                                         OpenOfferManager openOfferManager,
                                         P2PService p2PService,
                                         BtcWalletService walletService,
                                         BsqWalletService bsqWalletService,
                                         TradeWalletService tradeWalletService,
                                         DaoFacade daoFacade,
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
        this.offer = offer;
        this.tradeManager = tradeManager;
        this.openOfferManager = openOfferManager;
        this.btcWalletService = walletService;
        this.bsqWalletService = bsqWalletService;
        this.tradeWalletService = tradeWalletService;
        this.daoFacade = daoFacade;
        this.referralIdService = referralIdService;
        this.user = user;
        this.filterManager = filterManager;
        this.accountAgeWitnessService = accountAgeWitnessService;
        this.tradeStatisticsManager = tradeStatisticsManager;
        this.arbitratorManager = arbitratorManager;
        this.mediatorManager = mediatorManager;
        this.refundAgentManager = refundAgentManager;
        this.keyRing = keyRing;
        this.p2PService = p2PService;
        this.useSavingsWallet = useSavingsWallet;

        fundsNeededForTradeAsLong = fundsNeededForTrade.value;
        offerId = offer.getId();
        accountId = user.getAccountId();
        pubKeyRing = keyRing.getPubKeyRing();
    }

    public void removeMailboxMessageAfterProcessing(Trade trade) {
        if (tradeMessage instanceof MailboxMessage &&
                decryptedMessageWithPubKey != null &&
                decryptedMessageWithPubKey.getNetworkEnvelope().equals(tradeMessage)) {
            log.debug("Remove decryptedMsgWithPubKey from P2P network. decryptedMsgWithPubKey = " + decryptedMessageWithPubKey);
            p2PService.removeEntryFromMailbox(decryptedMessageWithPubKey);
            trade.removeDecryptedMessageWithPubKey(decryptedMessageWithPubKey);
        }
    }

    @Override
    public void persist() {
        log.warn("persist is not implemented in that class");
    }

    @Override
    public void onComplete() {
    }

    public void setTakeOfferFeeTx(Transaction takeOfferFeeTx) {
        this.takeOfferFeeTx = takeOfferFeeTx;
        takeOfferFeeTxId = takeOfferFeeTx.getHashAsString();
    }

    @Nullable
    public PaymentAccountPayload getPaymentAccountPayload(Trade trade) {
        PaymentAccount paymentAccount;
        if (trade instanceof MakerTrade)
            paymentAccount = user.getPaymentAccount(offer.getMakerPaymentAccountId());
        else
            paymentAccount = user.getPaymentAccount(trade.getTakerPaymentAccountId());
        return paymentAccount != null ? paymentAccount.getPaymentAccountPayload() : null;
    }

    public Coin getFundsNeededForTradeAsLong() {
        return Coin.valueOf(fundsNeededForTradeAsLong);
    }

    public Transaction resolveTakeOfferFeeTx(Trade trade) {
        if (takeOfferFeeTx == null) {
            if (!trade.isCurrencyForTakerFeeBtc())
                takeOfferFeeTx = bsqWalletService.getTransaction(takeOfferFeeTxId);
            else
                takeOfferFeeTx = btcWalletService.getTransaction(takeOfferFeeTxId);
        }
        return takeOfferFeeTx;
    }

    public NodeAddress getMyNodeAddress() {
        return p2PService.getAddress();
    }

    public void setPaymentStartedAckMessage(AckMessage ackMessage) {
        if (ackMessage.isSuccess()) {
            setPaymentStartedMessageState(MessageState.ACKNOWLEDGED);
        } else {
            setPaymentStartedMessageState(MessageState.FAILED);
        }
    }

    public void setPaymentStartedMessageState(MessageState paymentStartedMessageStateProperty) {
        this.paymentStartedMessageStateProperty.set(paymentStartedMessageStateProperty);
    }

    private void setTradingPeer(TradingPeer tradingPeer) {
        this.tradingPeer = tradingPeer;
    }

    private void setOfferId(String offerId) {
        this.offerId = offerId;
    }

    private void setAccountId(String accountId) {
        this.accountId = accountId;
    }

    private void setPubKeyRing(PubKeyRing pubKeyRing) {
        this.pubKeyRing = pubKeyRing;
    }
}
