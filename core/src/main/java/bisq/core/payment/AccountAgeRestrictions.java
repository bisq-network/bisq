/*
 * This file is part of Bisq.
 *
 * Bisq is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * Bisq is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Bisq. If not, see <http://www.gnu.org/licenses/>.
 */

package bisq.core.payment;

import bisq.core.offer.Offer;
import bisq.core.offer.OfferPayload;
import bisq.core.offer.OfferRestrictions;
import bisq.core.payment.payload.PaymentMethod;
import bisq.core.trade.Trade;

import bisq.common.util.Utilities;

import java.util.Date;
import java.util.GregorianCalendar;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class AccountAgeRestrictions {
    public static final long SAFE_ACCOUNT_AGE_DATE = Utilities.getUTCDate(2019, GregorianCalendar.MARCH, 1).getTime();

    public static boolean isMakersAccountAgeImmature(AccountAgeWitnessService accountAgeWitnessService, Offer offer) {
        long accountCreationDate = new Date().getTime() - accountAgeWitnessService.getMakersAccountAge(offer, new Date());
        return accountCreationDate > SAFE_ACCOUNT_AGE_DATE;
    }

    public static boolean isTradePeersAccountAgeImmature(AccountAgeWitnessService accountAgeWitnessService, Trade trade) {
        long accountCreationDate = new Date().getTime() - accountAgeWitnessService.getTradingPeersAccountAge(trade);
        return accountCreationDate > SAFE_ACCOUNT_AGE_DATE;
    }

    public static boolean isMyAccountAgeImmature(AccountAgeWitnessService accountAgeWitnessService, PaymentAccount myPaymentAccount) {
        long accountCreationDate = new Date().getTime() - accountAgeWitnessService.getMyAccountAge(myPaymentAccount.getPaymentAccountPayload());
        return accountCreationDate > SAFE_ACCOUNT_AGE_DATE;
    }

    public static long getMyTradeLimitAtCreateOffer(AccountAgeWitnessService accountAgeWitnessService,
                                                    PaymentAccount paymentAccount,
                                                    String currencyCode,
                                                    OfferPayload.Direction direction) {
        if (direction == OfferPayload.Direction.BUY &&
                PaymentMethod.hasChargebackRisk(paymentAccount.getPaymentMethod()) &&
                AccountAgeRestrictions.isMyAccountAgeImmature(accountAgeWitnessService, paymentAccount)) {
            return OfferRestrictions.TOLERATED_SMALL_TRADE_AMOUNT.value;
        } else {
            return accountAgeWitnessService.getMyTradeLimit(paymentAccount, currencyCode);
        }
    }

    public static long getMyTradeLimitAtTakeOffer(AccountAgeWitnessService accountAgeWitnessService,
                                                  PaymentAccount paymentAccount,
                                                  Offer offer,
                                                  String currencyCode,
                                                  OfferPayload.Direction direction) {
        if (direction == OfferPayload.Direction.BUY && PaymentMethod.hasChargebackRisk(paymentAccount.getPaymentMethod()) &&
                AccountAgeRestrictions.isMakersAccountAgeImmature(accountAgeWitnessService, offer)) {
            // Taker is seller
            return OfferRestrictions.TOLERATED_SMALL_TRADE_AMOUNT.value;
        } else if (direction == OfferPayload.Direction.SELL && PaymentMethod.hasChargebackRisk(paymentAccount.getPaymentMethod()) &&
                AccountAgeRestrictions.isMyAccountAgeImmature(accountAgeWitnessService, paymentAccount)) {
            // Taker is buyer
            return OfferRestrictions.TOLERATED_SMALL_TRADE_AMOUNT.value;
        } else {
            return accountAgeWitnessService.getMyTradeLimit(paymentAccount, currencyCode);
        }
    }
}
