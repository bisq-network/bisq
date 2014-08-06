package io.bitsquare.trade.protocol.offerer;

import com.google.bitcoin.core.Transaction;
import com.google.common.util.concurrent.FutureCallback;
import io.bitsquare.btc.WalletFacade;
import io.bitsquare.trade.handlers.ExceptionHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SignAndPublishDepositTx
{
    private static final Logger log = LoggerFactory.getLogger(SignAndPublishDepositTx.class);

    public static void run(ResultHandler resultHandler,
                           ExceptionHandler exceptionHandler,
                           WalletFacade walletFacade,
                           String preparedOffererDepositTxAsHex,
                           String signedTakerDepositTxAsHex,
                           String txConnOutAsHex,
                           String txScriptSigAsHex,
                           long offererTxOutIndex,
                           long takerTxOutIndex)
    {
        log.trace("Run task");
        try
        {
            walletFacade.offererSignAndPublishTx(preparedOffererDepositTxAsHex,
                                                 signedTakerDepositTxAsHex,
                                                 txConnOutAsHex,
                                                 txScriptSigAsHex,
                                                 offererTxOutIndex,
                                                 takerTxOutIndex,
                                                 new FutureCallback<Transaction>()
                                                 {
                                                     @Override
                                                     public void onSuccess(Transaction transaction)
                                                     {
                                                         log.trace("offererSignAndPublishTx succeeded " + transaction);
                                                         resultHandler.onResult(transaction);
                                                     }

                                                     @Override
                                                     public void onFailure(Throwable t)
                                                     {
                                                         log.error("offererSignAndPublishTx faultHandler.onFault:" + t);
                                                         exceptionHandler.onError(t);
                                                     }
                                                 });
        } catch (Exception e)
        {
            log.error("offererSignAndPublishTx faultHandler.onFault:" + e);
            exceptionHandler.onError(e);
        }
    }

    public interface ResultHandler
    {
        void onResult(Transaction depositTransaction);
    }

}
