package io.bitsquare.trade.payment.offerer.tasks;

import io.nucleo.scheduler.worker.WorkerFaultHandler;
import io.nucleo.scheduler.worker.WorkerResultHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class VerifyTakeOfferFeePayment extends AbstractOffererAsBuyerTask
{
    private static final Logger log = LoggerFactory.getLogger(VerifyTakeOfferFeePayment.class);

    public VerifyTakeOfferFeePayment(WorkerResultHandler resultHandler, WorkerFaultHandler faultHandler)
    {
        super(resultHandler, faultHandler);
    }

    @Override
    public void execute()
    {
        log.trace("execute");
        //TODO mocked yet, need a confidence listeners
        int numOfPeersSeenTx = sharedModel.getWalletFacade().getNumOfPeersSeenTx(sharedModel.getTakeOfferFeeTxId());
        if (numOfPeersSeenTx > 2)
        {
            complete();
        }
    }

}
