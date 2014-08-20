package io.bitsquare.trade.protocol.createoffer.tasks;

import com.google.bitcoin.core.Coin;
import io.bitsquare.btc.FeePolicy;
import io.bitsquare.btc.Restritions;
import io.bitsquare.trade.Offer;
import io.bitsquare.trade.handlers.FaultHandler;
import io.bitsquare.trade.handlers.ResultHandler;
import javax.annotation.concurrent.Immutable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

@Immutable
public class ValidateOffer
{
    private static final Logger log = LoggerFactory.getLogger(ValidateOffer.class);

    public static void run(ResultHandler resultHandler, FaultHandler faultHandler, Offer offer)
    {
        try
        {
            checkNotNull(offer.getAcceptedCountries());
            checkNotNull(offer.getAcceptedLanguageLocales());
            checkNotNull(offer.getAmount());
            checkNotNull(offer.getArbitrator());
            checkNotNull(offer.getBankAccountCountry());
            checkNotNull(offer.getBankAccountId());
            checkNotNull(offer.getCollateral());
            checkNotNull(offer.getCreationDate());
            checkNotNull(offer.getCurrency());
            checkNotNull(offer.getDirection());
            checkNotNull(offer.getId());
            checkNotNull(offer.getMessagePublicKey());
            checkNotNull(offer.getMinAmount());
            checkNotNull(offer.getPrice());

            checkArgument(offer.getAcceptedCountries().size() > 0);
            checkArgument(offer.getAcceptedLanguageLocales().size() > 0);
            checkArgument(offer.getMinAmount().compareTo(Restritions.MIN_TRADE_AMOUNT) >= 0);
            checkArgument(offer.getAmount().compareTo(Restritions.MIN_TRADE_AMOUNT) >= 0);
            checkArgument(offer.getAmount().compareTo(offer.getMinAmount()) >= 0);
            checkArgument(offer.getCollateral() > 0);
            checkArgument(offer.getPrice() > 0);

            // TODO check balance
            Coin collateralAsCoin = offer.getAmount().divide((long) (1d / offer.getCollateral()));
            Coin totalsToFund = collateralAsCoin.add(FeePolicy.CREATE_OFFER_FEE.add(FeePolicy.TX_FEE));
            // getAddressInfoByTradeID(offerId)
            // TODO when offer is flattened continue here...

            resultHandler.onResult();
        } catch (Throwable t)
        {
            faultHandler.onFault("Offer validation failed.", t);
        }
    }
}
