package io.bitsquare.trade.protocol.taker;

import com.google.bitcoin.core.InsufficientMoneyException;
import com.google.bitcoin.core.Transaction;
import com.google.common.util.concurrent.FutureCallback;
import io.bitsquare.btc.WalletFacade;
import io.bitsquare.trade.handlers.ExceptionHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PayTakeOfferFee
{
    private static final Logger log = LoggerFactory.getLogger(PayTakeOfferFee.class);

    public static void run(ResultHandler resultHandler, ExceptionHandler exceptionHandler, WalletFacade walletFacade, String tradeId)
    {
        log.trace("Run task");
        try
        {
            walletFacade.payTakeOfferFee(tradeId, new FutureCallback<Transaction>()
            {
                @Override
                public void onSuccess(Transaction transaction)
                {
                    log.debug("Take offer fee paid successfully. Transaction ID = " + transaction.getHashAsString());
                    resultHandler.onResult(transaction.getHashAsString());
                }

                @Override
                public void onFailure(Throwable t)
                {
                    log.error("Take offer fee paid faultHandler.onFault with exception: " + t);
                    exceptionHandler.onError(new Exception("Take offer fee paid faultHandler.onFault with exception: " + t));
                }
            });
        } catch (InsufficientMoneyException e)
        {
            log.error("Take offer fee paid faultHandler.onFault due InsufficientMoneyException " + e);
            exceptionHandler.onError(new Exception("Take offer fee paid faultHandler.onFault due InsufficientMoneyException " + e));
        }
    }

    public interface ResultHandler
    {
        void onResult(String takeOfferFeeTxId);
    }

}
