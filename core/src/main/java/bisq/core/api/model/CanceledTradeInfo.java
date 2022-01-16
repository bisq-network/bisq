package bisq.core.api.model;

import bisq.core.api.model.builder.TradeInfoV1Builder;
import bisq.core.offer.Offer;
import bisq.core.offer.OpenOffer;

import static bisq.core.api.model.ContractInfo.emptyContract;
import static bisq.core.api.model.OfferInfo.toMyOfferInfo;
import static bisq.core.offer.OpenOffer.State.CANCELED;
import static java.lang.String.format;
import static org.apache.commons.lang3.StringUtils.capitalize;

/**
 * Builds a TradeInfo instance from an OpenOffer with State = CANCELED.
 */
public class CanceledTradeInfo {

    public static TradeInfo toCanceledTradeInfo(OpenOffer myCanceledOpenOffer) {
        if (!myCanceledOpenOffer.getState().equals(CANCELED))
            throw new IllegalArgumentException(format("offer '%s' is not canceled", myCanceledOpenOffer.getId()));

        Offer offer = myCanceledOpenOffer.getOffer();
        OfferInfo offerInfo = toMyOfferInfo(offer);

        return new TradeInfoV1Builder()  // TODO May need to use BsqSwapTradeInfoBuilder?
                .withOffer(offerInfo)
                .withTradeId(myCanceledOpenOffer.getId())
                .withShortId(myCanceledOpenOffer.getShortId())
                .withDate(myCanceledOpenOffer.getDate().getTime())
                .withRole("")
                .withIsCurrencyForTakerFeeBtc(offer.isCurrencyForMakerFeeBtc())
                .withTxFeeAsLong(offer.getTxFee().value)
                .withTakerFeeAsLong(offer.getMakerFee().value)
                .withTakerFeeTxId("")               // Ignored
                .withDepositTxId("")                // Ignored
                .withPayoutTxId("")                 // Ignored
                .withTradeAmountAsLong(0)           // Ignored
                .withTradePrice(offer.getPrice().getValue())
                .withTradeVolume(0)                 // Ignored
                .withBuyerDeposit(offer.getBuyerSecurityDeposit().value)
                .withSellerDeposit(offer.getSellerSecurityDeposit().value)
                .withTradingPeerNodeAddress("")     // Ignored
                .withState("")                      // Ignored
                .withPhase("")                      // Ignored
                .withTradePeriodState("")           // Ignored
                .withIsDepositPublished(false)      // Ignored
                .withIsDepositConfirmed(false)      // Ignored
                .withIsFiatSent(false)              // Ignored
                .withIsFiatReceived(false)          // Ignored
                .withIsPayoutPublished(false)       // Ignored
                .withIsWithdrawn(false)             // Ignored
                .withContractAsJson("")             // Ignored
                .withContract(emptyContract.get())  // Ignored
                .withStatusDescription(capitalize(CANCELED.name().toLowerCase()))
                .build();
    }
}
