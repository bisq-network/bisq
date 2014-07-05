package io.bitsquare.trade.protocol.offerer;

import io.bitsquare.msg.MessageFacade;
import io.bitsquare.msg.listeners.OutgoingTradeMessageListener;
import io.bitsquare.trade.Trade;
import io.bitsquare.trade.protocol.FaultHandler;
import net.tomp2p.peers.PeerAddress;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class HandleTakeOfferRequest
{
    private static final Logger log = LoggerFactory.getLogger(HandleTakeOfferRequest.class);

    public static void run(ResultHandler resultHandler, FaultHandler faultHandler, PeerAddress peerAddress, MessageFacade messageFacade, Trade.State tradeState, String tradeId)
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
                log.error("AcceptTakeOfferRequestMessage faultHandler.onFault to arrive at peer");
                faultHandler.onFault(new Exception("AcceptTakeOfferRequestMessage faultHandler.onFault to arrive at peer"));
            }
        });
    }

    public interface ResultHandler
    {
        void onResult(boolean takeOfferRequestAccepted);
    }
}
