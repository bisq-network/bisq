package io.bitsquare.trade.payment.taker.tasks;

import io.bitsquare.msg.listeners.TradeMessageListener;
import io.bitsquare.trade.payment.taker.messages.PayoutTxPublishedMessage;
import io.nucleo.scheduler.worker.WorkerFaultHandler;
import io.nucleo.scheduler.worker.WorkerResultHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SendPayoutTxToOfferer extends AbstractTakerAsSellerTask
{
    private static final Logger log = LoggerFactory.getLogger(SendPayoutTxToOfferer.class);

    public SendPayoutTxToOfferer(WorkerResultHandler resultHandler, WorkerFaultHandler faultHandler)
    {
        super(resultHandler, faultHandler);
    }

    @Override
    public void execute()
    {
        log.trace("execute");
        PayoutTxPublishedMessage tradeMessage = new PayoutTxPublishedMessage(sharedModel.getTrade().getId(), sharedModel.getPayoutTxAsHex());
        sharedModel.getMessageFacade().sendTradeMessage(sharedModel.getPeerAddress(), tradeMessage, new TradeMessageListener()
        {
            @Override
            public void onResult()
            {
                log.trace("PayoutTxPublishedMessage successfully arrived at peer");
                complete();
            }

            @Override
            public void onFailed()
            {
                log.error("PayoutTxPublishedMessage failed to arrive at peer");
                failed(new Exception("PayoutTxPublishedMessage failed to arrive at peer"));
            }
        });
    }

}
