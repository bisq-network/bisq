package io.bitsquare.trade.protocol.offerer;

import io.bitsquare.msg.MessageFacade;
import io.bitsquare.msg.listeners.OutgoingTradeMessageListener;
import io.bitsquare.trade.Trade;
import io.bitsquare.trade.handlers.ExceptionHandler;
import net.tomp2p.peers.PeerAddress;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class HandleTakeOfferRequest
{
    private static final Logger log = LoggerFactory.getLogger(HandleTakeOfferRequest.class);

    public static void run(ResultHandler resultHandler, ExceptionHandler exceptionHandler, PeerAddress peerAddress, MessageFacade messageFacade, Trade.State tradeState, String tradeId)
    {
        log.trace("Run task");
        boolean takeOfferRequestAccepted = tradeState == Trade.State.OPEN;
        if (!takeOfferRequestAccepted)
        {
            log.info("Received take offer request but the offer not marked as open anymore.");
        }
        messageFacade.sendTradeMessage(peerAddress, new RespondToTakeOfferRequestMessage(tradeId, takeOfferRequestAccepted), new OutgoingTradeMessageListener()
        {
            @Override
            public void onResult()
            {
                log.trace("RespondToTakeOfferRequestMessage successfully arrived at peer");
                resultHandler.onResult(takeOfferRequestAccepted);
            }

            @Override
            public void onFailed()
            {
                log.error("AcceptTakeOfferRequestMessage  did not arrive at peer");
                exceptionHandler.onError(new Exception("AcceptTakeOfferRequestMessage did not arrive at peer"));
            }
        });
    }

    public interface ResultHandler
    {
        void onResult(boolean takeOfferRequestAccepted);
    }
}
