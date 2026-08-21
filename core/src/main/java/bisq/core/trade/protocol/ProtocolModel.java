package bisq.core.trade.protocol;

import bisq.core.offer.Offer;
import bisq.core.trade.TradeManager;

import bisq.network.p2p.NodeAddress;
import bisq.network.p2p.P2PService;

import bisq.common.proto.persistable.PersistablePayload;
import bisq.common.taskrunner.Model;

public interface ProtocolModel<T extends TradePeer> extends Model, PersistablePayload {
    void applyTransient(Provider provider, TradeManager tradeManager, Offer offer);

    P2PService getP2PService();

    T getTradePeer();

    void setTempTradingPeerNodeAddress(NodeAddress nodeAddress);

    NodeAddress getTempTradingPeerNodeAddress();

    TradeManager getTradeManager();

    void setTradeMessage(TradeMessage tradeMessage);

    NodeAddress getMyNodeAddress();
}
