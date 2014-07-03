package io.bitsquare.trade.payment.offerer.tasks;

import com.google.bitcoin.core.Utils;
import io.bitsquare.msg.listeners.TradeMessageListener;
import io.bitsquare.trade.payment.offerer.messages.DepositTxPublishedMessage;
import io.nucleo.scheduler.worker.WorkerFaultHandler;
import io.nucleo.scheduler.worker.WorkerResultHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SendDepositTxIdToTaker extends AbstractOffererAsBuyerTask
{
    private static final Logger log = LoggerFactory.getLogger(SendDepositTxIdToTaker.class);

    public SendDepositTxIdToTaker(WorkerResultHandler resultHandler, WorkerFaultHandler faultHandler)
    {
        super(resultHandler, faultHandler);
    }

    @Override
    public void execute()
    {
        log.trace("execute");
        DepositTxPublishedMessage tradeMessage = new DepositTxPublishedMessage(sharedModel.getTrade().getId(), Utils.bytesToHexString(sharedModel.getTrade().getDepositTransaction().bitcoinSerialize()));
        sharedModel.getMessageFacade().sendTradeMessage(sharedModel.peerAddress, tradeMessage, new TradeMessageListener()
        {
            @Override
            public void onResult()
            {
                log.trace("DepositTxPublishedMessage successfully arrived at peer");
                complete();
            }

            @Override
            public void onFailed()
            {
                log.error("DepositTxPublishedMessage failed to arrive at peer");
                failed(new Exception("DepositTxPublishedMessage failed to arrive at peer"));
            }
        });
    }

}
