package io.bitsquare.msg.listeners;

import io.bitsquare.msg.TradeMessage;
import net.tomp2p.peers.PeerAddress;

public interface TakeOfferRequestListener
{
    void onTakeOfferRequested(TradeMessage tradeMessage, PeerAddress sender);
}
