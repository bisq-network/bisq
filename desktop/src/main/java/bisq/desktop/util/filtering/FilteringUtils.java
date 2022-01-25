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

public class FilteringUtils {
    public static boolean match(Offer offer, String filterString) {
        if (offer.getId().contains(filterString)) {
            return true;
        }
        if (offer.getPaymentMethod().getDisplayString().contains(filterString)) {
            return true;
        }
        return offer.getOfferFeePaymentTxId() != null && offer.getOfferFeePaymentTxId().contains(filterString);
    }

    public static boolean match(BsqSwapTrade bsqSwapTrade, String filterString) {
        if (bsqSwapTrade.getTxId() != null && bsqSwapTrade.getTxId().contains(filterString)) {
            return true;
        }
        return bsqSwapTrade.getTradingPeerNodeAddress().getFullAddress().contains(filterString);
    }

    public static boolean match(Trade trade, String filterString) {
        if (trade == null) {
            return false;
        }
        if (trade.getTakerFeeTxId() != null && trade.getTakerFeeTxId().contains(filterString)) {
            return true;
        }
        if (trade.getDepositTxId() != null && trade.getDepositTxId().contains(filterString)) {
            return true;
        }
        if (trade.getPayoutTxId() != null && trade.getPayoutTxId().contains(filterString)) {
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
            isBuyerOnion = contract.getBuyerNodeAddress().getFullAddress().contains(filterString);
            isSellerOnion = contract.getSellerNodeAddress().getFullAddress().contains(filterString);
            matchesBuyersPaymentAccountData = contract.getBuyerPaymentAccountPayload() != null &&
                    contract.getBuyerPaymentAccountPayload().getPaymentDetails().contains(filterString);
            matchesSellersPaymentAccountData = contract.getSellerPaymentAccountPayload() != null &&
                    contract.getSellerPaymentAccountPayload().getPaymentDetails().contains(filterString);
        }
        return isBuyerOnion || isSellerOnion ||
                matchesBuyersPaymentAccountData || matchesSellersPaymentAccountData;
    }
}
