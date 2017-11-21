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

package io.bisq.core.trade.protocol;

import com.google.protobuf.ByteString;
import io.bisq.common.crypto.KeyRing;
import io.bisq.common.crypto.PubKeyRing;
import io.bisq.common.proto.ProtoUtil;
import io.bisq.common.proto.persistable.PersistablePayload;
import io.bisq.common.taskrunner.Model;
import io.bisq.core.app.BisqEnvironment;
import io.bisq.core.btc.data.RawTransactionInput;
import io.bisq.core.btc.wallet.BsqWalletService;
import io.bisq.core.btc.wallet.BtcWalletService;
import io.bisq.core.btc.wallet.TradeWalletService;
import io.bisq.core.filter.FilterManager;
import io.bisq.core.offer.Offer;
import io.bisq.core.offer.OpenOfferManager;
import io.bisq.core.payment.AccountAgeWitnessService;
import io.bisq.core.payment.PaymentAccount;
import io.bisq.core.payment.payload.PaymentAccountPayload;
import io.bisq.core.proto.CoreProtoResolver;
import io.bisq.core.trade.MakerTrade;
import io.bisq.core.trade.Trade;
import io.bisq.core.trade.TradeManager;
import io.bisq.core.trade.messages.TradeMessage;
import io.bisq.core.user.User;
import io.bisq.generated.protobuffer.PB;
import io.bisq.network.p2p.DecryptedMessageWithPubKey;
import io.bisq.network.p2p.MailboxMessage;
import io.bisq.network.p2p.NodeAddress;
import io.bisq.network.p2p.P2PService;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.bitcoinj.core.Coin;
import org.bitcoinj.core.Sha256Hash;
import org.bitcoinj.core.Transaction;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Getter
@Slf4j
public class ProcessModel implements Model, PersistablePayload {
    // Transient/Immutable (net set in constructor so they are not final, but at init)
    transient private TradeManager tradeManager;
    transient private OpenOfferManager openOfferManager;
    transient private BtcWalletService btcWalletService;
    transient private BsqWalletService bsqWalletService;
    transient private TradeWalletService tradeWalletService;
    transient private Offer offer;
    transient private User user;
    transient private FilterManager filterManager;
    transient private AccountAgeWitnessService accountAgeWitnessService;
    transient private KeyRing keyRing;
    transient private P2PService p2PService;

    // Transient/Mutable
    transient private Transaction takeOfferFeeTx;
    @Setter
    transient private TradeMessage tradeMessage;
    @Setter
    transient private DecryptedMessageWithPubKey decryptedMessageWithPubKey;


    // Persistable Immutable (only set by PB)
    @Setter
    private TradingPeer tradingPeer = new TradingPeer();
    @Setter
    private String offerId;
    @Setter
    private String accountId;
    @Setter
    private PubKeyRing pubKeyRing;

    // Persistable Mutable
    @Nullable
    @Setter
    private String takeOfferFeeTxId;
    @Nullable
    @Setter
    private byte[] payoutTxSignature;
    @Nullable
    @Setter
    private List<NodeAddress> takerAcceptedArbitratorNodeAddresses;
    @Nullable
    @Setter
    private List<NodeAddress> takerAcceptedMediatorNodeAddresses;
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

    public ProcessModel() {
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // PROTO BUFFER
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public PB.ProcessModel toProtoMessage() {
        final PB.ProcessModel.Builder builder = PB.ProcessModel.newBuilder()
                .setTradingPeer((PB.TradingPeer) tradingPeer.toProtoMessage())
                .setOfferId(offerId)
                .setAccountId(accountId)
                .setPubKeyRing(pubKeyRing.toProtoMessage())
                .setChangeOutputValue(changeOutputValue)
                .setUseSavingsWallet(useSavingsWallet)
                .setFundsNeededForTradeAsLong(fundsNeededForTradeAsLong);

        Optional.ofNullable(takeOfferFeeTxId).ifPresent(builder::setTakeOfferFeeTxId);
        Optional.ofNullable(payoutTxSignature).ifPresent(e -> builder.setPayoutTxSignature(ByteString.copyFrom(payoutTxSignature)));
        Optional.ofNullable(takerAcceptedArbitratorNodeAddresses).ifPresent(e -> builder.addAllTakerAcceptedArbitratorNodeAddresses(ProtoUtil.collectionToProto(takerAcceptedArbitratorNodeAddresses)));
        Optional.ofNullable(takerAcceptedMediatorNodeAddresses).ifPresent(e -> builder.addAllTakerAcceptedMediatorNodeAddresses(ProtoUtil.collectionToProto(takerAcceptedMediatorNodeAddresses)));
        Optional.ofNullable(preparedDepositTx).ifPresent(e -> builder.setPreparedDepositTx(ByteString.copyFrom(preparedDepositTx)));
        Optional.ofNullable(rawTransactionInputs).ifPresent(e -> builder.addAllRawTransactionInputs(ProtoUtil.collectionToProto(rawTransactionInputs)));
        Optional.ofNullable(changeOutputAddress).ifPresent(builder::setChangeOutputAddress);
        Optional.ofNullable(myMultiSigPubKey).ifPresent(e -> builder.setMyMultiSigPubKey(ByteString.copyFrom(myMultiSigPubKey)));
        Optional.ofNullable(tempTradingPeerNodeAddress).ifPresent(e -> builder.setTempTradingPeerNodeAddress(tempTradingPeerNodeAddress.toProtoMessage()));
        return builder.build();
    }

    public static ProcessModel fromProto(PB.ProcessModel proto, CoreProtoResolver coreProtoResolver) {
        ProcessModel processModel = new ProcessModel();
        processModel.setTradingPeer(proto.hasTradingPeer() ? TradingPeer.fromProto(proto.getTradingPeer(), coreProtoResolver) : null);
        processModel.setOfferId(proto.getOfferId());
        processModel.setAccountId(proto.getAccountId());
        processModel.setPubKeyRing(PubKeyRing.fromProto(proto.getPubKeyRing()));
        processModel.setChangeOutputValue(proto.getChangeOutputValue());
        processModel.setUseSavingsWallet(proto.getUseSavingsWallet());
        processModel.setFundsNeededForTradeAsLong(proto.getFundsNeededForTradeAsLong());

        // nullable
        processModel.setTakeOfferFeeTxId(ProtoUtil.stringOrNullFromProto(proto.getTakeOfferFeeTxId()));
        processModel.setPayoutTxSignature(ProtoUtil.byteArrayOrNullFromProto(proto.getPayoutTxSignature()));
        List<NodeAddress> takerAcceptedArbitratorNodeAddresses = proto.getTakerAcceptedArbitratorNodeAddressesList().isEmpty() ?
                null : proto.getTakerAcceptedArbitratorNodeAddressesList().stream()
                .map(NodeAddress::fromProto).collect(Collectors.toList());
        List<NodeAddress> takerAcceptedMediatorNodeAddresses = proto.getTakerAcceptedMediatorNodeAddressesList().isEmpty() ?
                null : proto.getTakerAcceptedMediatorNodeAddressesList().stream()
                .map(NodeAddress::fromProto).collect(Collectors.toList());
        processModel.setTakerAcceptedArbitratorNodeAddresses(takerAcceptedArbitratorNodeAddresses);
        processModel.setTakerAcceptedMediatorNodeAddresses(takerAcceptedMediatorNodeAddresses);
        processModel.setPreparedDepositTx(ProtoUtil.byteArrayOrNullFromProto(proto.getPreparedDepositTx()));
        List<RawTransactionInput> rawTransactionInputs = proto.getRawTransactionInputsList().isEmpty() ?
                null : proto.getRawTransactionInputsList().stream()
                .map(RawTransactionInput::fromProto).collect(Collectors.toList());
        processModel.setRawTransactionInputs(rawTransactionInputs);
        processModel.setChangeOutputAddress(ProtoUtil.stringOrNullFromProto(proto.getChangeOutputAddress()));
        processModel.setMyMultiSigPubKey(ProtoUtil.byteArrayOrNullFromProto(proto.getMyMultiSigPubKey()));
        processModel.setTempTradingPeerNodeAddress(proto.hasTempTradingPeerNodeAddress() ? NodeAddress.fromProto(proto.getTempTradingPeerNodeAddress()) : null);
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
                                         User user,
                                         FilterManager filterManager,
                                         AccountAgeWitnessService accountAgeWitnessService,
                                         KeyRing keyRing,
                                         boolean useSavingsWallet,
                                         Coin fundsNeededForTrade) {
        this.offer = offer;
        this.tradeManager = tradeManager;
        this.openOfferManager = openOfferManager;
        this.btcWalletService = walletService;
        this.bsqWalletService = bsqWalletService;
        this.tradeWalletService = tradeWalletService;
        this.user = user;
        this.filterManager = filterManager;
        this.accountAgeWitnessService = accountAgeWitnessService;
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
            if (BisqEnvironment.isBaseCurrencySupportingBsq() && !trade.isCurrencyForTakerFeeBtc())
                takeOfferFeeTx = bsqWalletService.getTransaction(Sha256Hash.wrap(takeOfferFeeTxId));
            else
                takeOfferFeeTx = btcWalletService.getTransaction(Sha256Hash.wrap(takeOfferFeeTxId));
        }
        return takeOfferFeeTx;
    }

    public NodeAddress getMyNodeAddress() {
        return p2PService.getAddress();
    }
}
