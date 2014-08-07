package io.bitsquare.trade.protocol.createoffer.tasks;

import io.bitsquare.msg.MessageFacade;
import io.bitsquare.trade.Offer;
import io.bitsquare.trade.handlers.FaultHandler;
import io.bitsquare.trade.handlers.ResultHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PublishOfferToDHT
{
    private static final Logger log = LoggerFactory.getLogger(PublishOfferToDHT.class);

    public static void run(ResultHandler resultHandler, FaultHandler faultHandler, MessageFacade messageFacade, Offer offer)
    {
        messageFacade.addOffer(offer, new MessageFacade.AddOfferListener()
        {
            @Override
            public void onComplete()
            {
                resultHandler.onResult();
            }

            @Override
            public void onFailed(String reason, Throwable throwable)
            {
                faultHandler.onFault("Publish offer to DHT failed.", throwable);
            }
        });
    }
}
