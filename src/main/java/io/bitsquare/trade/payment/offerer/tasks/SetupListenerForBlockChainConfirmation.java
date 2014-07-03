package io.bitsquare.trade.payment.offerer.tasks;

import com.google.bitcoin.core.Transaction;
import com.google.bitcoin.core.TransactionConfidence;
import io.nucleo.scheduler.worker.WorkerFaultHandler;
import io.nucleo.scheduler.worker.WorkerResultHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SetupListenerForBlockChainConfirmation extends AbstractOffererAsBuyerTask
{
    private static final Logger log = LoggerFactory.getLogger(SetupListenerForBlockChainConfirmation.class);

    public SetupListenerForBlockChainConfirmation(WorkerResultHandler resultHandler, WorkerFaultHandler faultHandler)
    {
        super(resultHandler, faultHandler);
    }

    @Override
    public void execute()
    {
        log.trace("execute");
        //TODO
        // sharedModel.offererPaymentProtocolListener.onDepositTxConfirmedInBlockchain();

        Transaction tx = sharedModel.getTrade().getDepositTransaction();
        tx.getConfidence().addEventListener(new TransactionConfidence.Listener()
                                            {
                                                @Override
                                                public void onConfidenceChanged(Transaction tx, ChangeReason reason)
                                                {
                                                    log.trace("onConfidenceChanged " + tx.getConfidence());
                                                    if (reason == ChangeReason.SEEN_PEERS)
                                                    {
                                                        sharedModel.getListener().onDepositTxConfirmedUpdate(tx.getConfidence());
                                                    }
                                                    if (reason == ChangeReason.TYPE && tx.getConfidence().getConfidenceType() == TransactionConfidence.ConfidenceType.BUILDING)
                                                    {
                                                        sharedModel.getListener().onDepositTxConfirmedInBlockchain();
                                                        tx.getConfidence().removeEventListener(this);
                                                        log.trace("Tx is in blockchain");
                                                        complete();
                                                    }
                                                }
                                            }
        );
    }
}
