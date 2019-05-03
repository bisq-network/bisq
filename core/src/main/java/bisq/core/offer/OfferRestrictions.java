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

package bisq.core.offer;

import bisq.core.payment.payload.PaymentMethod;
import bisq.core.trade.Trade;

import org.bitcoinj.core.Coin;

public class OfferRestrictions {
    public static Coin TOLERATED_SMALL_TRADE_AMOUNT = Coin.parseCoin("0.01");

    public static boolean isOfferRisky(Offer offer) {
        return offer != null &&
                offer.isBuyOffer() &&
                PaymentMethod.hasChargebackRisk(offer.getPaymentMethod()) &&
                isMinTradeAmountRisky(offer);
    }

    public static boolean isSellOfferRisky(Offer offer) {
        return offer != null &&
                PaymentMethod.hasChargebackRisk(offer.getPaymentMethod()) &&
                isMinTradeAmountRisky(offer);
    }

    public static boolean isTradeRisky(Trade trade) {
        if (trade == null)
            return false;

        Offer offer = trade.getOffer();
        return offer != null &&
                PaymentMethod.hasChargebackRisk(offer.getPaymentMethod()) &&
                trade.getTradeAmount() != null &&
                isAmountRisky(trade.getTradeAmount());
    }

    public static boolean isMinTradeAmountRisky(Offer offer) {
        return isAmountRisky(offer.getMinAmount());
    }

    public static boolean isAmountRisky(Coin amount) {
        return amount.isGreaterThan(TOLERATED_SMALL_TRADE_AMOUNT);
    }
}
