package io.bitsquare.trade.payment.taker.tasks;

import com.google.bitcoin.core.InsufficientMoneyException;
import com.google.bitcoin.core.Transaction;
import com.google.common.util.concurrent.FutureCallback;
import io.nucleo.scheduler.worker.WorkerFaultHandler;
import io.nucleo.scheduler.worker.WorkerResultHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PayTakeOfferFee extends AbstractTakerAsSellerTask
{
    private static final Logger log = LoggerFactory.getLogger(PayTakeOfferFee.class);

    public PayTakeOfferFee(WorkerResultHandler resultHandler, WorkerFaultHandler faultHandler)
    {
        super(resultHandler, faultHandler);
    }

    @Override
    public void execute()
    {
        log.trace("execute");
        try
        {
            sharedModel.getWalletFacade().payTakeOfferFee(sharedModel.getTrade().getId(), new FutureCallback<Transaction>()
            {
                @Override
                public void onSuccess(Transaction transaction)
                {
                    log.debug("Take offer fee paid successfully. Transaction ID = " + transaction.getHashAsString());
                    sharedModel.getTrade().setTakeOfferFeeTxID(transaction.getHashAsString());
                    complete();
                }

                @Override
                public void onFailure(Throwable t)
                {
                    log.error("Take offer fee paid failed with exception: " + t);
                    failed(new Exception("Take offer fee paid failed with exception: " + t));
                }
            });
        } catch (InsufficientMoneyException e)
        {
            log.error("Take offer fee paid failed due InsufficientMoneyException " + e);
            failed(new Exception("Take offer fee paid failed due InsufficientMoneyException " + e));
        }
    }

}
