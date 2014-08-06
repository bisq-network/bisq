package io.bitsquare.trade.protocol.createoffer.tasks;

import com.google.bitcoin.core.InsufficientMoneyException;
import io.bitsquare.btc.WalletFacade;
import io.bitsquare.trade.Offer;
import io.bitsquare.trade.handlers.TransactionResultHandler;
import io.bitsquare.trade.handlers.FaultHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CreateOfferFeeTx
{
    private static final Logger log = LoggerFactory.getLogger(CreateOfferFeeTx.class);

    public static void run(TransactionResultHandler resultHandler, FaultHandler faultHandler, WalletFacade walletFacade, Offer offer)
    {
        try
        {
            resultHandler.onResult(walletFacade.createOfferFeeTx(offer.getId()));
        } catch (InsufficientMoneyException e)
        {
            faultHandler.onFault("Offer fee payment failed because there is insufficient money in the trade pocket. ", e);
        } catch (Throwable t)
        {
            faultHandler.onFault("Offer fee payment failed because of an exception occurred. ", t);
        }
    }
}
