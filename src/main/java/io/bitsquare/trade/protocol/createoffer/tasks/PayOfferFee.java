package io.bitsquare.trade.protocol.createoffer.tasks;

import com.google.bitcoin.core.InsufficientMoneyException;
import com.google.bitcoin.core.Transaction;
import com.google.common.util.concurrent.FutureCallback;
import io.bitsquare.btc.WalletFacade;
import io.bitsquare.trade.Offer;
import io.bitsquare.trade.handlers.FaultHandler;
import io.bitsquare.trade.handlers.PublishTransactionResultHandler;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PayOfferFee
{
    private static final Logger log = LoggerFactory.getLogger(PayOfferFee.class);

    public static void run(PublishTransactionResultHandler publishTransactionResultHandler, FaultHandler faultHandler, WalletFacade walletFacade, Offer offer)
    {
        try
        {
            walletFacade.payCreateOfferFee(offer.getId(), new FutureCallback<Transaction>()
            {
                @Override
                public void onSuccess(@javax.annotation.Nullable Transaction transaction)
                {
                    log.info("sendResult onSuccess:" + transaction);
                    if (transaction != null)
                    {
                        offer.setOfferFeePaymentTxID(transaction.getHashAsString());
                        try
                        {
                            publishTransactionResultHandler.onResult(transaction.getHashAsString());
                        } catch (Exception e)
                        {
                            faultHandler.onFault("Offer fee payment failed.", e);
                        }
                    }
                    else
                    {
                        faultHandler.onFault("Offer fee payment failed.", new Exception("Offer fee payment failed. Transaction = null."));
                    }
                }

                @Override
                public void onFailure(@NotNull Throwable t)
                {
                    faultHandler.onFault("Offer fee payment failed with an exception.", t);
                }
            });
        } catch (InsufficientMoneyException e)
        {
            faultHandler.onFault("Offer fee payment failed because there is insufficient money in the trade pocket. ", e);
        } catch (Throwable t)
        {
            faultHandler.onFault("Offer fee payment failed because of an exception occurred. ", t);
        }
    }
}
