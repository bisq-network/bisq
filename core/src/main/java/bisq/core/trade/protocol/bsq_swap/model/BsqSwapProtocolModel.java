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

package bisq.core.trade.protocol.bsq_swap.model;

import bisq.core.btc.model.RawTransactionInput;
import bisq.core.btc.wallet.BsqWalletService;
import bisq.core.btc.wallet.BtcWalletService;
import bisq.core.btc.wallet.TradeWalletService;
import bisq.core.btc.wallet.WalletsManager;
import bisq.core.dao.DaoFacade;
import bisq.core.filter.FilterManager;
import bisq.core.offer.Offer;
import bisq.core.offer.OpenOfferManager;
import bisq.core.trade.TradeManager;
import bisq.core.trade.protocol.ProtocolModel;
import bisq.core.trade.protocol.Provider;
import bisq.core.trade.protocol.TradeMessage;

import bisq.network.p2p.NodeAddress;
import bisq.network.p2p.P2PService;

import bisq.common.crypto.KeyRing;
import bisq.common.crypto.PubKeyRing;
import bisq.common.proto.ProtoUtil;
import bisq.common.util.Utilities;

import com.google.protobuf.ByteString;

import org.bitcoinj.core.Transaction;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import javax.annotation.Nullable;

// Fields marked as transient are only used during protocol execution which are based on directMessages so we do not
// persist them.

/**
 * This is the base model for the trade protocol. It is persisted with the trade (non transient fields).
 * It uses the {@link Provider} for access to domain services.
 */

@Getter
@Slf4j
public class BsqSwapProtocolModel implements ProtocolModel<BsqSwapTradePeer> {
    transient private Provider provider;
    transient private TradeManager tradeManager;
    transient private Offer offer;
    @Setter
    transient private TradeMessage tradeMessage;
    // TODO rename tradingPeerNodeAddress ?
    @Nullable
    @Setter
    transient private NodeAddress tempTradingPeerNodeAddress;
    @Nullable
    private transient Transaction transaction;

    private final BsqSwapTradePeer tradePeer;
    private final PubKeyRing pubKeyRing;

    @Setter
    @Nullable
    private String btcAddress;
    @Setter
    @Nullable
    private String bsqAddress;
    @Setter
    @Nullable
    private List<RawTransactionInput> inputs;
    @Setter
    private long change;
    @Setter
    private long payout;
    @Setter
    @Nullable
    private byte[] tx;
    @Setter
    private long txFee;

    public BsqSwapProtocolModel(PubKeyRing pubKeyRing) {
        this(pubKeyRing, new BsqSwapTradePeer());
    }

    public BsqSwapProtocolModel(PubKeyRing pubKeyRing, BsqSwapTradePeer tradePeer) {
        this.pubKeyRing = pubKeyRing;
        this.tradePeer = tradePeer != null ? tradePeer : new BsqSwapTradePeer();
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // PROTO BUFFER
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public protobuf.BsqSwapProtocolModel toProtoMessage() {
        final protobuf.BsqSwapProtocolModel.Builder builder = protobuf.BsqSwapProtocolModel.newBuilder()
                .setChange(change)
                .setPayout(payout)
                .setTradePeer(tradePeer.toProtoMessage())
                .setPubKeyRing(pubKeyRing.toProtoMessage())
                .setTxFee(txFee);
        Optional.ofNullable(btcAddress).ifPresent(builder::setBtcAddress);
        Optional.ofNullable(bsqAddress).ifPresent(builder::setBsqAddress);
        Optional.ofNullable(inputs).ifPresent(e -> builder.addAllInputs(
                ProtoUtil.collectionToProto(e, protobuf.RawTransactionInput.class)));
        Optional.ofNullable(tx).ifPresent(e -> builder.setTx(ByteString.copyFrom(e)));
        return builder.build();
    }

    public static BsqSwapProtocolModel fromProto(protobuf.BsqSwapProtocolModel proto) {
        BsqSwapProtocolModel bsqSwapProtocolModel = new BsqSwapProtocolModel(
                PubKeyRing.fromProto(proto.getPubKeyRing()),
                BsqSwapTradePeer.fromProto(proto.getTradePeer()));
        bsqSwapProtocolModel.setChange(proto.getChange());
        bsqSwapProtocolModel.setPayout(proto.getPayout());

        bsqSwapProtocolModel.setBtcAddress(ProtoUtil.stringOrNullFromProto(proto.getBtcAddress()));
        bsqSwapProtocolModel.setBsqAddress(ProtoUtil.stringOrNullFromProto(proto.getBsqAddress()));
        List<RawTransactionInput> inputs = proto.getInputsList().isEmpty() ?
                null :
                proto.getInputsList().stream()
                        .map(RawTransactionInput::fromProto)
                        .collect(Collectors.toList());
        bsqSwapProtocolModel.setInputs(inputs);
        bsqSwapProtocolModel.setTx(ProtoUtil.byteArrayOrNullFromProto(proto.getTx()));
        bsqSwapProtocolModel.setTxFee(proto.getTxFee());
        return bsqSwapProtocolModel;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // TradeProtocolModel
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void applyTransient(Provider provider,
                               TradeManager tradeManager,
                               Offer offer) {
        this.offer = offer;
        this.provider = provider;
        this.tradeManager = tradeManager;
    }

    @Override
    public P2PService getP2PService() {
        return provider.getP2PService();
    }

    @Override
    public NodeAddress getMyNodeAddress() {
        return getP2PService().getAddress();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // API
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void onComplete() {
    }

    public void applyTransaction(Transaction transaction) {
        this.transaction = transaction;
        tx = transaction.bitcoinSerialize();
    }

    @Nullable
    public Transaction getTransaction() {
        if (tx == null) {
            return null;
        }
        if (transaction == null) {
            Transaction deSerializedTx = getBsqWalletService().getTxFromSerializedTx(tx);
            transaction = getBsqWalletService().getTransaction(deSerializedTx.getTxId());
        }
        return transaction;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Delegates
    ///////////////////////////////////////////////////////////////////////////////////////////

    public BsqWalletService getBsqWalletService() {
        return provider.getBsqWalletService();
    }

    public BtcWalletService getBtcWalletService() {
        return provider.getBtcWalletService();
    }

    public TradeWalletService getTradeWalletService() {
        return provider.getTradeWalletService();
    }

    public WalletsManager getWalletsManager() {
        return provider.getWalletsManager();
    }

    public DaoFacade getDaoFacade() {
        return provider.getDaoFacade();
    }

    public KeyRing getKeyRing() {
        return provider.getKeyRing();
    }

    public FilterManager getFilterManager() {
        return provider.getFilterManager();
    }

    public OpenOfferManager getOpenOfferManager() {
        return provider.getOpenOfferManager();
    }

    public String getOfferId() {
        return offer.getId();
    }


    @Override
    public String toString() {
        return "BsqSwapProtocolModel{" +
                ",\r\n     offer=" + offer +
                ",\r\n     tradeMessage=" + tradeMessage +
                ",\r\n     tempTradingPeerNodeAddress=" + tempTradingPeerNodeAddress +
                ",\r\n     transaction=" + getTransaction() +
                ",\r\n     tradePeer=" + tradePeer +
                ",\r\n     pubKeyRing=" + pubKeyRing +
                ",\r\n     btcAddress='" + btcAddress + '\'' +
                ",\r\n     bsqAddress='" + bsqAddress + '\'' +
                ",\r\n     inputs=" + inputs +
                ",\r\n     change=" + change +
                ",\r\n     payout=" + payout +
                ",\r\n     tx=" + Utilities.encodeToHex(tx) +
                ",\r\n     txFee=" + txFee +
                "\r\n}";
    }
}
