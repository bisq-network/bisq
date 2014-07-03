package io.bitsquare.trade.payment.offerer.tasks;

import io.bitsquare.msg.listeners.TradeMessageListener;
import io.bitsquare.trade.Trade;
import io.bitsquare.trade.payment.offerer.messages.AcceptTakeOfferRequestMessage;
import io.bitsquare.trade.payment.offerer.messages.RejectTakeOfferRequestMessage;
import io.nucleo.scheduler.worker.WorkerFaultHandler;
import io.nucleo.scheduler.worker.WorkerResultHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class HandleTakeOfferRequest extends AbstractOffererAsBuyerTask
{
    private static final Logger log = LoggerFactory.getLogger(HandleTakeOfferRequest.class);

    public HandleTakeOfferRequest(WorkerResultHandler resultHandler, WorkerFaultHandler faultHandler)
    {
        super(resultHandler, faultHandler);
    }

    @Override
    public void execute()
    {
        log.trace("execute");
        if (sharedModel.getTrade().getState() == Trade.State.OPEN)
        {
            AcceptTakeOfferRequestMessage msg = new AcceptTakeOfferRequestMessage(sharedModel.getTrade().getId());
            sharedModel.getMessageFacade().sendTradeMessage(sharedModel.peerAddress, msg, new TradeMessageListener()
            {
                @Override
                public void onResult()
                {
                    log.trace("AcceptTakeOfferRequestMessage successfully arrived at peer");
                    sharedModel.getTrade().setState(Trade.State.ACCEPTED);
                    sharedModel.getMessageFacade().removeOffer(sharedModel.getTrade().getOffer());
                    complete();
                }

                @Override
                public void onFailed()
                {
                    log.error("AcceptTakeOfferRequestMessage failed to arrive at peer");
                    failed(new Exception("AcceptTakeOfferRequestMessage failed to arrive at peer"));
                }
            });
        }
        else
        {
            RejectTakeOfferRequestMessage msg = new RejectTakeOfferRequestMessage(sharedModel.getTrade().getId());
            sharedModel.getMessageFacade().sendTradeMessage(sharedModel.peerAddress, msg, new TradeMessageListener()
            {
                @Override
                public void onResult()
                {
                    log.trace("RejectTakeOfferRequestMessage successfully arrived at peer");
                }

                @Override
                public void onFailed()
                {
                    log.error("RejectTakeOfferRequestMessage failed to arrive at peer");
                }
            });

            log.error("Offer not marked as open.");
            failed(new Exception("Offer not marked as open."));
        }
    }

}
