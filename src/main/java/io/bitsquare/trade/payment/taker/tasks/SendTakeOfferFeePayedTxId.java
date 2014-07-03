package io.bitsquare.trade.payment.taker.tasks;

import io.bitsquare.msg.listeners.TradeMessageListener;
import io.bitsquare.trade.payment.taker.messages.TakeOfferFeePayedMessage;
import io.nucleo.scheduler.worker.WorkerFaultHandler;
import io.nucleo.scheduler.worker.WorkerResultHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SendTakeOfferFeePayedTxId extends AbstractTakerAsSellerTask
{
    private static final Logger log = LoggerFactory.getLogger(SendTakeOfferFeePayedTxId.class);

    public SendTakeOfferFeePayedTxId(WorkerResultHandler resultHandler, WorkerFaultHandler faultHandler)
    {
        super(resultHandler, faultHandler);
    }

    @Override
    public void execute()
    {
        log.trace("execute");

        TakeOfferFeePayedMessage msg = new TakeOfferFeePayedMessage(sharedModel.getTrade().getId(),
                sharedModel.getTrade().getTakeOfferFeeTxId(),
                sharedModel.getTrade().getTradeAmount(),
                sharedModel.getWalletFacade().getAddressInfoByTradeID(sharedModel.getTrade().getId()).getPubKeyAsHexString());

        sharedModel.getMessageFacade().sendTradeMessage(sharedModel.getPeerAddress(), msg, new TradeMessageListener()
        {
            @Override
            public void onResult()
            {
                log.trace("TakeOfferFeePayedMessage successfully arrived at peer");
                complete();
            }

            @Override
            public void onFailed()
            {
                log.error("TakeOfferFeePayedMessage failed to arrive at peer");
                failed(new Exception("TakeOfferFeePayedMessage failed to arrive at peer"));
            }
        });
    }
}
