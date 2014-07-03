package io.bitsquare.trade.payment.taker.tasks;

import com.google.bitcoin.core.Transaction;
import com.google.bitcoin.core.Utils;
import com.google.common.util.concurrent.FutureCallback;
import io.nucleo.scheduler.worker.WorkerFaultHandler;
import io.nucleo.scheduler.worker.WorkerResultHandler;
import java.math.BigInteger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SignAndPublishPayoutTx extends AbstractTakerAsSellerTask
{
    private static final Logger log = LoggerFactory.getLogger(SignAndPublishPayoutTx.class);

    public SignAndPublishPayoutTx(WorkerResultHandler resultHandler, WorkerFaultHandler faultHandler)
    {
        super(resultHandler, faultHandler);
    }

    @Override
    public void execute()
    {
        log.trace("execute");
        try
        {
            String depositTxAsHex = sharedModel.getDepositTxAsHex();
            String offererSignatureR = sharedModel.getOffererSignatureR();
            String offererSignatureS = sharedModel.getOffererSignatureS();
            BigInteger offererPaybackAmount = sharedModel.getOffererPaybackAmount();
            BigInteger takerPaybackAmount = sharedModel.getTakerPaybackAmount();
            String offererPayoutAddress = sharedModel.getOffererPayoutAddress();

            sharedModel.getWalletFacade().takerSignsAndSendsTx(depositTxAsHex,
                    offererSignatureR,
                    offererSignatureS,
                    offererPaybackAmount,
                    takerPaybackAmount,
                    offererPayoutAddress,
                    sharedModel.getTrade().getId(),
                    new FutureCallback<Transaction>()
                    {
                        @Override
                        public void onSuccess(Transaction transaction)
                        {
                            log.debug("takerSignsAndSendsTx " + transaction);
                            sharedModel.getListener().onTradeCompleted(sharedModel.getTrade(), transaction.getHashAsString());
                            sharedModel.setPayoutTxAsHex(Utils.bytesToHexString(transaction.bitcoinSerialize()));
                            complete();
                        }

                        @Override
                        public void onFailure(Throwable t)
                        {
                            log.error("Exception at takerSignsAndSendsTx " + t);
                            failed(t);
                        }
                    });
        } catch (Exception e)
        {
            log.error("Exception at takerSignsAndSendsTx " + e);
            failed(e);
        }
    }

}
