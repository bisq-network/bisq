package io.bitsquare.trade.payment.taker.tasks;

import io.bitsquare.msg.listeners.TradeMessageListener;
import io.bitsquare.trade.payment.taker.messages.RequestTakeOfferMessage;
import io.nucleo.scheduler.worker.WorkerFaultHandler;
import io.nucleo.scheduler.worker.WorkerResultHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RequestTakeOffer extends AbstractTakerAsSellerTask
{
    private static final Logger log = LoggerFactory.getLogger(RequestTakeOffer.class);

    public RequestTakeOffer(WorkerResultHandler resultHandler, WorkerFaultHandler faultHandler)
    {
        super(resultHandler, faultHandler);
    }

    @Override
    public void execute()
    {
        log.trace("execute");
        RequestTakeOfferMessage msg = new RequestTakeOfferMessage(sharedModel.getTrade().getId());
        sharedModel.getMessageFacade().sendTradingMessage(sharedModel.getPeerAddress(), msg, new TradeMessageListener()
        {
            @Override
            public void onResult()
            {
                log.trace("RequestTakeOfferMessage successfully arrived at peer");
                complete();
            }

            @Override
            public void onFailed()
            {
                log.error("RequestTakeOfferMessage failed to arrive at peer");
                failed(new Exception("RequestTakeOfferMessage failed to arrive at peer"));
            }
        });

    }

}
