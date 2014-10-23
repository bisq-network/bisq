/*
 * This file is part of Bitsquare.
 *
 * Bitsquare is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * Bitsquare is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Bitsquare. If not, see <http://www.gnu.org/licenses/>.
 */

package io.bitsquare.trade.protocol.createoffer.tasks;

import io.bitsquare.btc.Restrictions;
import io.bitsquare.trade.Offer;
import io.bitsquare.trade.handlers.FaultHandler;
import io.bitsquare.trade.handlers.ResultHandler;

import javax.annotation.concurrent.Immutable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static com.google.common.base.Preconditions.*;

@Immutable
public class VerifyOffer {
    private static final Logger log = LoggerFactory.getLogger(VerifyOffer.class);

    public static void run(ResultHandler resultHandler, FaultHandler faultHandler, Offer offer) {
        try {
            checkNotNull(offer.getAcceptedCountries(), "AcceptedCountries is null");
            checkNotNull(offer.getAcceptedLanguageLocales(), "AcceptedLanguageLocales is null");
            checkNotNull(offer.getAmount(), "Amount is null");
            checkNotNull(offer.getArbitrators(), "Arbitrator is null");
            checkNotNull(offer.getBankAccountCountry(), "BankAccountCountry is null");
            checkNotNull(offer.getBankAccountId(), "BankAccountId is null");
            checkNotNull(offer.getSecurityDeposit(), "SecurityDeposit is null");
            checkNotNull(offer.getCreationDate(), "CreationDate is null");
            checkNotNull(offer.getCurrency(), "Currency is null");
            checkNotNull(offer.getDirection(), "Direction is null");
            checkNotNull(offer.getId(), "Id is null");
            checkNotNull(offer.getMessagePublicKey(), "MessagePublicKey is null");
            checkNotNull(offer.getMinAmount(), "MinAmount is null");
            checkNotNull(offer.getPrice(), "Price is null");

            checkArgument(!offer.getAcceptedCountries().isEmpty(), "AcceptedCountries is empty");
            checkArgument(!offer.getAcceptedLanguageLocales().isEmpty(), "AcceptedLanguageLocales is empty");
            checkArgument(offer.getMinAmount().compareTo(Restrictions.MIN_TRADE_AMOUNT) >= 0,
                    "MinAmount is less then " + Restrictions.MIN_TRADE_AMOUNT);
            checkArgument(offer.getAmount().compareTo(Restrictions.MIN_TRADE_AMOUNT) >= 0,
                    "Amount is less then " + Restrictions.MIN_TRADE_AMOUNT);
            checkArgument(offer.getAmount().compareTo(offer.getMinAmount()) >= 0, "MinAmount is larger then Amount");
            checkArgument(offer.getSecurityDeposit().isPositive(), "SecurityDeposit is not positive");
            checkArgument(offer.getPrice().isPositive(), "Price is 0 or negative");

            // TODO check balance
            // securityDeposit
            // Coin totalsToFund 
            // getAddressInfoByTradeID(offerId)
            // TODO when offer is flattened continue here...

            resultHandler.onResult();
        } catch (Throwable t) {
            faultHandler.onFault("Offer validation failed.", t);
        }
    }
}
