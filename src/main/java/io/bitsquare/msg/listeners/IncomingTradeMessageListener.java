package io.bitsquare.msg.listeners;

import io.bitsquare.trade.protocol.TradeMessage;
import net.tomp2p.peers.PeerAddress;

public interface IncomingTradeMessageListener
{
    void onMessage(TradeMessage tradeMessage, PeerAddress sender);
}
