package io.bitsquare.trade.protocol.tasks.taker;

import io.bitsquare.msg.MessageFacade;
import io.bitsquare.msg.listeners.OutgoingTradeMessageListener;
import io.bitsquare.trade.Trade;
import io.bitsquare.trade.protocol.messages.taker.PayoutTxPublishedMessage;
import io.bitsquare.trade.protocol.tasks.FaultHandler;
import io.bitsquare.trade.protocol.tasks.ResultHandler;
import net.tomp2p.peers.PeerAddress;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SendPayoutTxToOfferer
{
    private static final Logger log = LoggerFactory.getLogger(SendPayoutTxToOfferer.class);

    public static void run(ResultHandler resultHandler, FaultHandler faultHandler, PeerAddress peerAddress, MessageFacade messageFacade, Trade trade, String payoutTxAsHex)
    {
        PayoutTxPublishedMessage tradeMessage = new PayoutTxPublishedMessage(trade.getId(), payoutTxAsHex);
        messageFacade.sendTradeMessage(peerAddress, tradeMessage, new OutgoingTradeMessageListener()
        {
            @Override
            public void onResult()
            {
                log.trace("PayoutTxPublishedMessage successfully arrived at peer");
                resultHandler.onResult();
            }

            @Override
            public void onFailed()
            {
                log.error("PayoutTxPublishedMessage faultHandler.onFault to arrive at peer");
                faultHandler.onFault(new Exception("PayoutTxPublishedMessage faultHandler.onFault to arrive at peer"));
            }
        });

    }

}
