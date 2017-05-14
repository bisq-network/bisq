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

package io.bisq.core.trade.protocol;

import com.google.protobuf.ByteString;
import com.google.protobuf.Message;
import io.bisq.common.crypto.KeyRing;
import io.bisq.common.crypto.PubKeyRing;
import io.bisq.common.proto.ProtoUtil;
import io.bisq.common.proto.persistable.PersistablePayload;
import io.bisq.common.taskrunner.Model;
import io.bisq.core.btc.data.RawTransactionInput;
import io.bisq.core.btc.wallet.BsqWalletService;
import io.bisq.core.btc.wallet.BtcWalletService;
import io.bisq.core.btc.wallet.TradeWalletService;
import io.bisq.core.filter.FilterManager;
import io.bisq.core.filter.PaymentAccountFilter;
import io.bisq.core.offer.Offer;
import io.bisq.core.offer.OpenOfferManager;
import io.bisq.core.payment.PaymentAccount;
import io.bisq.core.payment.payload.PaymentAccountPayload;
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
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

@Getter
@Slf4j
public class ProcessModel implements Model, PersistablePayload {
    // Transient/Immutable
    @Getter
    transient private TradeManager tradeManager;
    @Getter
    transient private OpenOfferManager openOfferManager;
    transient private BtcWalletService btcWalletService;
    transient private BsqWalletService bsqWalletService;
    transient private TradeWalletService tradeWalletService;
    transient private Offer offer;
    @Getter
    transient private User user;
    transient private FilterManager filterManager;
    @Getter
    transient private KeyRing keyRing;
    @Getter
    transient private P2PService p2PService;

    // Immutable
    private final TradingPeer tradingPeer = new TradingPeer();
    private String offerId;
    private String accountId;
    private PubKeyRing pubKeyRing;

    // Transient/Mutable
    transient private Transaction takeOfferFeeTx;
    @Setter
    transient private TradeMessage tradeMessage;
    @Setter
    transient private DecryptedMessageWithPubKey decryptedMessageWithPubKey;

    // Mutable
    private String takeOfferFeeTxId;
    @Setter
    private byte[] payoutTxSignature;
    @Setter
    private List<NodeAddress> takerAcceptedArbitratorNodeAddresses;
    @Setter
    private List<NodeAddress> takerAcceptedMediatorNodeAddresses;
    @Setter
    private byte[] preparedDepositTx;
    @Setter
    private ArrayList<RawTransactionInput> rawTransactionInputs;
    @Setter
    private long changeOutputValue;
    @Nullable
    @Setter
    private String changeOutputAddress;
    @Setter
    private boolean useSavingsWallet;
    @Setter
    private long fundsNeededForTradeAsLong;
    @Setter
    private byte[] myMultiSigPubKey;
    // that is used to store temp. the peers address when we get an incoming message before the message is verified.
    // After successful verified we copy that over to the trade.tradingPeerAddress
    @Setter
    private NodeAddress tempTradingPeerNodeAddress;


    public ProcessModel() {
    }

    public void onAllServicesInitialized(Offer offer,
                                         TradeManager tradeManager,
                                         OpenOfferManager openOfferManager,
                                         P2PService p2PService,
                                         BtcWalletService walletService,
                                         BsqWalletService bsqWalletService,
                                         TradeWalletService tradeWalletService,
                                         User user,
                                         FilterManager filterManager,
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
        this.keyRing = keyRing;
        this.p2PService = p2PService;
        this.useSavingsWallet = useSavingsWallet;
        fundsNeededForTradeAsLong = fundsNeededForTrade.value;
        offerId = offer.getId();
        accountId = user.getAccountId();
        pubKeyRing = keyRing.getPubKeyRing();
    }

    // TODO need lazy access?
    public NodeAddress getMyNodeAddress() {
        return p2PService.getAddress();
    }

    @Override
    public void persist() {
    }

    @Override
    public void onComplete() {
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

    public boolean isPeersPaymentAccountDataAreBanned(PaymentAccountPayload paymentAccountPayload,
                                                      PaymentAccountFilter[] appliedPaymentAccountFilter) {
        return filterManager.getFilter() != null &&
                filterManager.getFilter().getBannedPaymentAccounts().stream()
                        .filter(paymentAccountFilter -> {
                            final boolean samePaymentMethodId = paymentAccountFilter.getPaymentMethodId().equals(
                                    paymentAccountPayload.getPaymentMethodId());
                            if (samePaymentMethodId) {
                                try {
                                    Method method = paymentAccountPayload.getClass().getMethod(paymentAccountFilter.getGetMethodName());
                                    String result = (String) method.invoke(paymentAccountPayload);
                                    appliedPaymentAccountFilter[0] = paymentAccountFilter;
                                    return result.equals(paymentAccountFilter.getValue());
                                } catch (Throwable e) {
                                    log.error(e.getMessage());
                                    return false;
                                }
                            } else {
                                return false;
                            }
                        })
                        .findAny()
                        .isPresent();
    }

    public Coin getFundsNeededForTradeAsLong() {
        return Coin.valueOf(fundsNeededForTradeAsLong);
    }

    public Transaction getTakeOfferFeeTx() {
        if (takeOfferFeeTx == null)
            takeOfferFeeTx = bsqWalletService.getTransaction(Sha256Hash.wrap(takeOfferFeeTxId));
        return takeOfferFeeTx;
    }

    public void setTakeOfferFeeTx(Transaction takeOfferFeeTx) {
        this.takeOfferFeeTx = takeOfferFeeTx;
        takeOfferFeeTxId = takeOfferFeeTx.getHashAsString();
    }

    @Override
    public Message toProtoMessage() {
        return PB.ProcessModel.newBuilder()
                .setTradingPeer((PB.TradingPeer) tradingPeer.toProtoMessage())
                .setOfferId(offerId)
                .setAccountId(accountId)
                .setPubKeyRing(pubKeyRing.toProtoMessage())
                .setTakeOfferFeeTxId(takeOfferFeeTxId)
                .setPayoutTxSignature(ByteString.copyFrom(payoutTxSignature))
                .addAllTakerAcceptedArbitratorNodeAddresses(ProtoUtil.collectionToProto(takerAcceptedArbitratorNodeAddresses))
                .addAllTakerAcceptedMediatorNodeAddresses(ProtoUtil.collectionToProto(takerAcceptedMediatorNodeAddresses))
                .setPreparedDepositTx(ByteString.copyFrom(preparedDepositTx))
                .addAllRawTransactionInputs(ProtoUtil.collectionToProto(rawTransactionInputs))
                .setChangeOutputValue(changeOutputValue)
                .setChangeOutputAddress(changeOutputAddress)
                .setUseSavingsWallet(useSavingsWallet)
                .setFundsNeededForTradeAsLong(fundsNeededForTradeAsLong)
                .setMyMultiSigPubKey(ByteString.copyFrom(myMultiSigPubKey))
                .setTempTradingPeerNodeAddress(tempTradingPeerNodeAddress.toProtoMessage())
                .build();
    }


    public void removeMailboxMessageAfterProcessing(Trade trade) {
        if (tradeMessage instanceof MailboxMessage &&
                decryptedMessageWithPubKey != null &&
                decryptedMessageWithPubKey.getWireEnvelope().equals(tradeMessage)) {
            log.debug("Remove decryptedMsgWithPubKey from P2P network. decryptedMsgWithPubKey = " + decryptedMessageWithPubKey);
            p2PService.removeEntryFromMailbox(decryptedMessageWithPubKey);
            trade.removeDecryptedMessageWithPubKey(decryptedMessageWithPubKey);
        }
    }
}
