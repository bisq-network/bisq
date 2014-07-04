package io.bitsquare.trade.protocol.tasks.offerer;

import com.google.bitcoin.core.Transaction;
import com.google.bitcoin.core.TransactionConfidence;
import io.bitsquare.trade.protocol.offerer.OffererAsBuyerProtocolListener;
import io.bitsquare.trade.protocol.tasks.FaultHandler;
import io.bitsquare.trade.protocol.tasks.ResultHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SetupListenerForBlockChainConfirmation
{
    private static final Logger log = LoggerFactory.getLogger(SetupListenerForBlockChainConfirmation.class);

    public static void run(ResultHandler resultHandler, FaultHandler faultHandler, Transaction depositTransaction, OffererAsBuyerProtocolListener listener)
    {      //TODO
        // sharedModel.offererPaymentProtocolListener.onDepositTxConfirmedInBlockchain();

        depositTransaction.getConfidence().addEventListener(new TransactionConfidence.Listener()
                                                            {
                                                                @Override
                                                                public void onConfidenceChanged(Transaction tx, ChangeReason reason)
                                                                {
                                                                    log.trace("onConfidenceChanged " + tx.getConfidence());
                                                                    if (reason == ChangeReason.SEEN_PEERS)
                                                                    {
                                                                        listener.onDepositTxConfirmedUpdate(tx.getConfidence());
                                                                    }
                                                                    if (reason == ChangeReason.TYPE && tx.getConfidence().getConfidenceType() == TransactionConfidence.ConfidenceType.BUILDING)
                                                                    {
                                                                        listener.onDepositTxConfirmedInBlockchain();
                                                                        depositTransaction.getConfidence().removeEventListener(this);
                                                                        log.trace("Tx is in blockchain");
                                                                        resultHandler.onResult();
                                                                    }
                                                                }
                                                            }
        );
    }
}
