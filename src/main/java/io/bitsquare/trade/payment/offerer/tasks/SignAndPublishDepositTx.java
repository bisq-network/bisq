package io.bitsquare.trade.payment.offerer.tasks;

import com.google.bitcoin.core.Transaction;
import com.google.common.util.concurrent.FutureCallback;
import io.nucleo.scheduler.worker.WorkerFaultHandler;
import io.nucleo.scheduler.worker.WorkerResultHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SignAndPublishDepositTx extends AbstractOffererAsBuyerTask
{
    private static final Logger log = LoggerFactory.getLogger(SignAndPublishDepositTx.class);

    public SignAndPublishDepositTx(WorkerResultHandler resultHandler, WorkerFaultHandler faultHandler)
    {
        super(resultHandler, faultHandler);
    }

    @Override
    public void execute()
    {
        log.trace("execute");
        try
        {
            sharedModel.getWalletFacade().offererSignAndPublishTx(sharedModel.getPreparedOffererDepositTxAsHex(),
                    sharedModel.getSignedTakerDepositTxAsHex(),
                    sharedModel.getTxConnOutAsHex(),
                    sharedModel.getTxScriptSigAsHex(),
                    sharedModel.getOffererTxOutIndex(),
                    sharedModel.getTakerTxOutIndex(),
                    new FutureCallback<Transaction>()
                    {
                        @Override
                        public void onSuccess(Transaction transaction)
                        {
                            log.trace("offererSignAndPublishTx succeeded " + transaction);
                            sharedModel.getTrade().setDepositTransaction(transaction);
                            sharedModel.getListener().onDepositTxPublished(transaction.getHashAsString());
                            complete();
                        }

                        @Override
                        public void onFailure(Throwable t)
                        {
                            log.error("offererSignAndPublishTx failed:" + t);
                            failed(t);
                        }
                    });
        } catch (Exception e)
        {
            log.error("offererSignAndPublishTx failed:" + e);
            failed(e);
        }
    }

}
