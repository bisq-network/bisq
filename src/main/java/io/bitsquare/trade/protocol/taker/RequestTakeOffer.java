package io.bitsquare.trade.protocol.taker;

import io.bitsquare.msg.MessageFacade;
import io.bitsquare.msg.listeners.OutgoingTradeMessageListener;
import io.bitsquare.trade.protocol.FaultHandler;
import io.bitsquare.trade.protocol.ResultHandler;
import net.tomp2p.peers.PeerAddress;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RequestTakeOffer
{
    private static final Logger log = LoggerFactory.getLogger(RequestTakeOffer.class);

    public static void run(ResultHandler resultHandler, FaultHandler faultHandler, PeerAddress peerAddress, MessageFacade messageFacade, String tradeId)
    {
        messageFacade.sendTradeMessage(peerAddress, new RequestTakeOfferMessage(tradeId), new OutgoingTradeMessageListener()
        {
            @Override
            public void onResult()
            {
                log.trace("RequestTakeOfferMessage successfully arrived at peer");
                resultHandler.onResult();
            }

            @Override
            public void onFailed()
            {
                log.error("RequestTakeOfferMessage faultHandler.onFault to arrive at peer");
                faultHandler.onFault(new Exception("RequestTakeOfferMessage faultHandler.onFault to arrive at peer"));
            }
        });
    }
}
