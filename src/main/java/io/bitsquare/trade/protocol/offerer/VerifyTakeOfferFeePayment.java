package io.bitsquare.trade.protocol.offerer;

import io.bitsquare.btc.WalletFacade;
import io.bitsquare.trade.handlers.ExceptionHandler;
import io.bitsquare.trade.handlers.ResultHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class VerifyTakeOfferFeePayment
{
    private static final Logger log = LoggerFactory.getLogger(VerifyTakeOfferFeePayment.class);

    public static void run(ResultHandler resultHandler, ExceptionHandler exceptionHandler, WalletFacade walletFacade, String takeOfferFeeTxId)
    {
        log.trace("Run task");
        //TODO mocked yet, need a confidence listeners
        int numOfPeersSeenTx = walletFacade.getNumOfPeersSeenTx(takeOfferFeeTxId);
        if (numOfPeersSeenTx > 2)
        {
            resultHandler.onResult();
        }
    }

}
