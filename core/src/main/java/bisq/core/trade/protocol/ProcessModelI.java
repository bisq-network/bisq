package bisq.core.trade.protocol;

import bisq.core.offer.Offer;
import bisq.core.trade.TradeManager;
import bisq.core.trade.messages.TradeMessage;

import bisq.network.p2p.AckMessage;
import bisq.network.p2p.NodeAddress;
import bisq.network.p2p.P2PService;

public interface ProcessModelI {
    void applyTransient(ProcessModelServiceProvider provider,
                        TradeManager tradeManager,
                        Offer offer);

    P2PService getP2PService();

    TradingPeer getTradingPeer();

    void setTempTradingPeerNodeAddress(NodeAddress nodeAddress);

    NodeAddress getTempTradingPeerNodeAddress();

    TradeManager getTradeManager();

    void setTradeMessage(TradeMessage tradeMessage);

    NodeAddress getMyNodeAddress();
}
