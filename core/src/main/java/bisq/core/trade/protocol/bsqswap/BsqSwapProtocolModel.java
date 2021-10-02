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

package bisq.core.trade.protocol.bsqswap;

import bisq.core.btc.wallet.BsqWalletService;
import bisq.core.btc.wallet.BtcWalletService;
import bisq.core.btc.wallet.TradeWalletService;
import bisq.core.btc.wallet.WalletsManager;
import bisq.core.dao.DaoFacade;
import bisq.core.filter.FilterManager;
import bisq.core.offer.Offer;
import bisq.core.offer.OpenOfferManager;
import bisq.core.proto.CoreProtoResolver;
import bisq.core.trade.messages.TradeMessage;
import bisq.core.trade.model.TradeManager;
import bisq.core.trade.model.bsqswap.BsqSwapTrade;
import bisq.core.trade.protocol.Provider;
import bisq.core.trade.protocol.TradeProtocolModel;

import bisq.network.p2p.NodeAddress;
import bisq.network.p2p.P2PService;

import bisq.common.crypto.KeyRing;
import bisq.common.crypto.PubKeyRing;
import bisq.common.proto.persistable.PersistablePayload;
import bisq.common.taskrunner.Model;

import java.util.Optional;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import javax.annotation.Nullable;

import static com.google.common.base.Preconditions.checkNotNull;

// Fields marked as transient are only used during protocol execution which are based on directMessages so we do not
// persist them.

/**
 * This is the base model for the trade protocol. It is persisted with the trade (non transient fields).
 * It uses the {@link Provider} for access to domain services.
 */

@Getter
@Slf4j
public class BsqSwapProtocolModel implements TradeProtocolModel<BsqSwapTradePeer>, Model, PersistablePayload {
    transient private Provider provider;
    transient private TradeManager tradeManager;
    transient private Offer offer;
    @Setter
    transient private TradeMessage tradeMessage;

    private BsqSwapTrade trade;

    private final BsqSwapTradePeer tradePeer;
    private final PubKeyRing pubKeyRing;
    @Nullable
    @Setter
    private NodeAddress tempTradingPeerNodeAddress;

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
        protobuf.BsqSwapProtocolModel.Builder builder = protobuf.BsqSwapProtocolModel.newBuilder()
                .setTradingPeer((protobuf.TradingPeer) tradePeer.toProtoMessage())
                .setPubKeyRing(pubKeyRing.toProtoMessage());
        Optional.ofNullable(tempTradingPeerNodeAddress).
                ifPresent(e -> builder.setTempTradingPeerNodeAddress(e.toProtoMessage()));
        return builder.build();
    }

    public static BsqSwapProtocolModel fromProto(protobuf.BsqSwapProtocolModel proto,
                                                 CoreProtoResolver coreProtoResolver) {
        BsqSwapTradePeer tradePeer = BsqSwapTradePeer.fromProto(proto.getTradingPeer(), coreProtoResolver);
        PubKeyRing pubKeyRing = PubKeyRing.fromProto(proto.getPubKeyRing());
        BsqSwapProtocolModel model = new BsqSwapProtocolModel(pubKeyRing, tradePeer);
        model.setTempTradingPeerNodeAddress(proto.hasTempTradingPeerNodeAddress() ?
                NodeAddress.fromProto(proto.getTempTradingPeerNodeAddress()) : null);
        return model;
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

    public void initFromTrade(BsqSwapTrade trade) {
        this.trade = trade;
        var offer = trade.getOffer();
        checkNotNull(offer, "offer must not be null");

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
}
