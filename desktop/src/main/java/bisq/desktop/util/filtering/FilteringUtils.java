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

package bisq.desktop.util.filtering;

import bisq.core.offer.Offer;
import bisq.core.trade.model.bisq_v1.Contract;
import bisq.core.trade.model.bisq_v1.Trade;
import bisq.core.trade.model.bsq_swap.BsqSwapTrade;

import org.apache.commons.lang3.Strings;

public class FilteringUtils {
    public static boolean match(Offer offer, String filterString) {
        if (Strings.CI.contains(offer.getId(), filterString)) {
            return true;
        }
        if (Strings.CI.contains(offer.getPaymentMethod().getDisplayString(), filterString)) {
            return true;
        }
        return offer.getOfferFeePaymentTxId() != null && Strings.CI.contains(offer.getOfferFeePaymentTxId(), filterString);
    }

    public static boolean match(BsqSwapTrade bsqSwapTrade, String filterString) {
        if (bsqSwapTrade.getTxId() != null && Strings.CI.contains(bsqSwapTrade.getTxId(), filterString)) {
            return true;
        }
        return Strings.CI.contains(bsqSwapTrade.getTradingPeerNodeAddress().getFullAddress(), filterString);
    }

    public static boolean match(Trade trade, String filterString) {
        if (trade == null) {
            return false;
        }
        if (trade.getTakerFeeTxId() != null && Strings.CI.contains(trade.getTakerFeeTxId(), filterString)) {
            return true;
        }
        if (trade.getDepositTxId() != null && Strings.CI.contains(trade.getDepositTxId(), filterString)) {
            return true;
        }
        if (trade.getPayoutTxId() != null && Strings.CI.contains(trade.getPayoutTxId(), filterString)) {
            return true;
        }
        return match(trade.getContract(), filterString);
    }

    private static boolean match(Contract contract, String filterString) {
        boolean isBuyerOnion = false;
        boolean isSellerOnion = false;
        boolean matchesBuyersPaymentAccountData = false;
        boolean matchesSellersPaymentAccountData = false;
        if (contract != null) {
            isBuyerOnion = Strings.CI.contains(contract.getBuyerNodeAddress().getFullAddress(), filterString);
            isSellerOnion = Strings.CI.contains(contract.getSellerNodeAddress().getFullAddress(), filterString);
            matchesBuyersPaymentAccountData = contract.getBuyerPaymentAccountPayload() != null &&
                    Strings.CI.contains(contract.getBuyerPaymentAccountPayload().getPaymentDetails(), filterString);
            matchesSellersPaymentAccountData = contract.getSellerPaymentAccountPayload() != null &&
                    Strings.CI.contains(contract.getSellerPaymentAccountPayload().getPaymentDetails(), filterString);
        }
        return isBuyerOnion || isSellerOnion ||
                matchesBuyersPaymentAccountData || matchesSellersPaymentAccountData;
    }
}
