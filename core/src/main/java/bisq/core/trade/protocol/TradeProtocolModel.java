package bisq.core.trade.protocol;

import bisq.core.offer.Offer;
import bisq.core.trade.messages.TradeMessage;
import bisq.core.trade.model.TradeManager;

import bisq.network.p2p.NodeAddress;
import bisq.network.p2p.P2PService;

public interface TradeProtocolModel<T extends TradePeer> {
    void applyTransient(Provider provider, TradeManager tradeManager, Offer offer);

    P2PService getP2PService();

    T getTradingPeer();

    void setTempTradingPeerNodeAddress(NodeAddress nodeAddress);

    NodeAddress getTempTradingPeerNodeAddress();

    TradeManager getTradeManager();

    void setTradeMessage(TradeMessage tradeMessage);

    NodeAddress getMyNodeAddress();
}
