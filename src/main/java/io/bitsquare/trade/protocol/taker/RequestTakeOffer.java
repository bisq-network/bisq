package io.bitsquare.trade.protocol.taker;

import io.bitsquare.msg.MessageFacade;
import io.bitsquare.msg.listeners.OutgoingTradeMessageListener;
import io.bitsquare.trade.handlers.ExceptionHandler;
import io.bitsquare.trade.handlers.ResultHandler;
import net.tomp2p.peers.PeerAddress;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RequestTakeOffer
{
    private static final Logger log = LoggerFactory.getLogger(RequestTakeOffer.class);

    public static void run(ResultHandler resultHandler, ExceptionHandler exceptionHandler, PeerAddress peerAddress, MessageFacade messageFacade, String tradeId)
    {
        log.trace("Run task");
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
                log.error("RequestTakeOfferMessage  did not arrive at peer");
                exceptionHandler.onError(new Exception("RequestTakeOfferMessage did not arrive at peer"));
            }
        });
    }
}
