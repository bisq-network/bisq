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
                .withClosingStatus(capitalize(CANCELED.name().toLowerCase()))
                .build();
    }
}
