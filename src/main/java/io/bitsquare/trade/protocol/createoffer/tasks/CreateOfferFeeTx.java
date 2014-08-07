package io.bitsquare.trade.protocol.createoffer.tasks;

import com.google.bitcoin.core.InsufficientMoneyException;
import io.bitsquare.btc.WalletFacade;
import io.bitsquare.trade.handlers.FaultHandler;
import io.bitsquare.trade.handlers.TransactionResultHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CreateOfferFeeTx
{
    private static final Logger log = LoggerFactory.getLogger(CreateOfferFeeTx.class);

    public static void run(TransactionResultHandler resultHandler, FaultHandler faultHandler, WalletFacade walletFacade, String offerId)
    {
        try
        {
            resultHandler.onResult(walletFacade.createOfferFeeTx(offerId));
        } catch (InsufficientMoneyException e)
        {
            faultHandler.onFault("Offer fee payment failed because there is insufficient money in the trade pocket. ", e);
        } catch (Throwable t)
        {
            faultHandler.onFault("Offer fee payment failed because of an exception occurred. ", t);
        }
    }
}
