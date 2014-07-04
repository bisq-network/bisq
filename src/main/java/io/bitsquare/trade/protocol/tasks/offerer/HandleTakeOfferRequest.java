package io.bitsquare.trade.protocol.tasks.offerer;

import io.bitsquare.msg.MessageFacade;
import io.bitsquare.msg.listeners.OutgoingTradeMessageListener;
import io.bitsquare.trade.Trade;
import io.bitsquare.trade.protocol.messages.offerer.AcceptTakeOfferRequestMessage;
import io.bitsquare.trade.protocol.messages.offerer.RejectTakeOfferRequestMessage;
import io.bitsquare.trade.protocol.tasks.FaultHandler;
import net.tomp2p.peers.PeerAddress;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class HandleTakeOfferRequest
{
    private static final Logger log = LoggerFactory.getLogger(HandleTakeOfferRequest.class);

    public static void run(ResultHandler resultHandler, FaultHandler faultHandler, PeerAddress peerAddress, MessageFacade messageFacade, Trade.State tradeState, String tradeId)
    {
        if (tradeState == Trade.State.OPEN)
        {
            messageFacade.sendTradeMessage(peerAddress, new AcceptTakeOfferRequestMessage(tradeId), new OutgoingTradeMessageListener()
            {
                @Override
                public void onResult()
                {
                    log.trace("AcceptTakeOfferRequestMessage successfully arrived at peer");
                    resultHandler.onResult(Trade.State.ACCEPTED);
                }

                @Override
                public void onFailed()
                {
                    log.error("AcceptTakeOfferRequestMessage faultHandler.onFault to arrive at peer");
                    faultHandler.onFault(new Exception("AcceptTakeOfferRequestMessage faultHandler.onFault to arrive at peer"));
                }
            });
        }
        else
        {
            RejectTakeOfferRequestMessage msg = new RejectTakeOfferRequestMessage(tradeId);
            messageFacade.sendTradeMessage(peerAddress, msg, new OutgoingTradeMessageListener()
            {
                @Override
                public void onResult()
                {
                    log.trace("RejectTakeOfferRequestMessage successfully arrived at peer");
                }

                @Override
                public void onFailed()
                {
                    log.error("RejectTakeOfferRequestMessage faultHandler.onFault to arrive at peer");
                }
            });

            log.error("Offer not marked as open.");
            faultHandler.onFault(new Exception("Offer not marked as open."));
        }
    }

    public interface ResultHandler
    {
        void onResult(Trade.State tradeState);
    }
}
