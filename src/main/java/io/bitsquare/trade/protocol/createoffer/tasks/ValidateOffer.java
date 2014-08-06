package io.bitsquare.trade.protocol.createoffer.tasks;

import io.bitsquare.trade.Offer;
import io.bitsquare.trade.handlers.FaultHandler;
import io.bitsquare.trade.handlers.ResultHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ValidateOffer
{
    private static final Logger log = LoggerFactory.getLogger(ValidateOffer.class);

    public static void run(ResultHandler resultHandler, FaultHandler faultHandler, Offer offer)
    {
        boolean isValid = offer.getAmount().isGreaterThan(offer.getAmount());

        if (offer.getAmount().compareTo(offer.getMinAmount()) >= 0)
        {
            faultHandler.onFault("Offer validation failed: Min. amount is larger than amount.", new Exception("Offer validation failed: Min. amount is larger than amount."));

        }
        else if (offer.getAcceptedCountries() == null || offer.getAcceptedCountries().size() == 0)
        {
            faultHandler.onFault("Offer validation failed: No accepted countries are defined.", new Exception("Offer validation failed: No accepted countries are defined."));
        } //TODO...
        else
        {
            resultHandler.onResult();
        }
    }
}
