package bisq.desktop.util.filtering;

import bisq.core.offer.Offer;
import bisq.core.trade.model.bisq_v1.Contract;
import bisq.core.trade.model.bsq_swap.BsqSwapTrade;

public class FilteringUtils {
    public static boolean match(Contract contract, String filterString) {
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

    public static boolean match(Offer offer, String filterString) {
        if (offer.getId().contains(filterString)) {
            return true;
        }
        if (offer.getPaymentMethod().getDisplayString().contains(filterString)) {
            return true;
        }
        if (offer.getOfferFeePaymentTxId() != null && offer.getOfferFeePaymentTxId().contains(filterString)) {
            return true;
        }
        return false;
    }

    public static boolean match(BsqSwapTrade bsqSwapTrade, String filterString) {
        if (bsqSwapTrade.getTxId() != null && bsqSwapTrade.getTxId().contains(filterString)) {
            return true;
        }
        if (bsqSwapTrade.getTradingPeerNodeAddress().getFullAddress().contains(filterString)) {
            return true;
        }
        return false;
    }
}
