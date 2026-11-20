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

package bisq.core.trade.validation;

import bisq.core.offer.Offer;
import bisq.core.payment.CustomPaymentMethodUtil;
import bisq.core.payment.payload.CustomPaymentMethodAccountPayload;
import bisq.core.payment.payload.PaymentAccountPayload;
import bisq.core.payment.payload.PaymentMethod;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

public final class CustomPaymentMethodValidation {
    private CustomPaymentMethodValidation() {
    }

    /**
     * Checks the payment account which the peer has revealed. All custom payment methods share one payment
     * method ID and their accounts hold free texts of the peer. So the account must be for the custom payment
     * method which the offer names, and its texts must keep the rules for these texts. An account of the custom
     * payment method is not accepted in a trade of another payment method.
     * <p>
     * The messages do not contain the texts of the peer, as the error of a trade is shown to the user.
     */
    public static PaymentAccountPayload checkPeersPaymentAccountPayload(PaymentAccountPayload peersPaymentAccountPayload,
                                                                         Offer offer) {
        checkNotNull(peersPaymentAccountPayload, "peersPaymentAccountPayload must not be null");
        checkNotNull(offer, "offer must not be null");
        boolean isCustomPaymentMethodOffer = offer.getPaymentMethod().isCustom();
        // The peer sets the kind of the account and the payment method ID in it independently
        checkArgument(isCustomPaymentMethodOffer ==
                        peersPaymentAccountPayload instanceof CustomPaymentMethodAccountPayload,
                "The payment account of the peer and the offer must both be of the custom payment method, " +
                        "or none of them");
        checkArgument(isCustomPaymentMethodOffer ==
                        PaymentMethod.CUSTOM_ID.equals(peersPaymentAccountPayload.getPaymentMethodId()),
                "The payment method ID in the payment account of the peer does not match the offer");
        if (isCustomPaymentMethodOffer) {
            CustomPaymentMethodAccountPayload payload = (CustomPaymentMethodAccountPayload) peersPaymentAccountPayload;
            checkArgument(CustomPaymentMethodUtil.isSameName(offer.getCustomPaymentMethodName(),
                            payload.getPaymentMethodName()),
                    "The payment account of the peer is not for the custom payment method of the offer");
            checkArgument(CustomPaymentMethodUtil.isValidAccountData(payload.getAccountData()),
                    "The account data of the payment account of the peer is not valid");
        }
        return peersPaymentAccountPayload;
    }
}
