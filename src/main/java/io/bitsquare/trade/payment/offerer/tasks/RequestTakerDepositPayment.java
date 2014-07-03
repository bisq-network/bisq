package io.bitsquare.trade.payment.offerer.tasks;

import io.bitsquare.msg.listeners.TradeMessageListener;
import io.bitsquare.trade.payment.offerer.messages.RequestTakerDepositPaymentMessage;
import io.nucleo.scheduler.worker.WorkerFaultHandler;
import io.nucleo.scheduler.worker.WorkerResultHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RequestTakerDepositPayment extends AbstractOffererAsBuyerTask
{
    private static final Logger log = LoggerFactory.getLogger(RequestTakerDepositPayment.class);

    public RequestTakerDepositPayment(WorkerResultHandler resultHandler, WorkerFaultHandler faultHandler)
    {
        super(resultHandler, faultHandler);
    }

    @Override
    public void execute()
    {
        log.trace("execute");

        RequestTakerDepositPaymentMessage tradeMessage = new RequestTakerDepositPaymentMessage(sharedModel.getTrade().getId(),
                sharedModel.getUser().getBankAccount(sharedModel.getTrade().getOffer().getBankAccountUID()),
                sharedModel.getUser().getAccountID(),
                sharedModel.getOffererPubKey(),
                sharedModel.getPreparedOffererDepositTxAsHex(),
                sharedModel.getOffererTxOutIndex());
        sharedModel.getMessageFacade().sendTradeMessage(sharedModel.peerAddress, tradeMessage, new TradeMessageListener()
        {
            @Override
            public void onResult()
            {
                log.trace("RequestTakerDepositPaymentMessage successfully arrived at peer");
                complete();
            }

            @Override
            public void onFailed()
            {
                log.error("RequestTakerDepositPaymentMessage failed to arrive at peer");
                failed(new Exception("RequestTakerDepositPaymentMessage failed to arrive at peer"));
            }
        });
    }

}
