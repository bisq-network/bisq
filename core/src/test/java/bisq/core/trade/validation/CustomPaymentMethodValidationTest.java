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
import bisq.core.payment.payload.AliPayAccountPayload;
import bisq.core.payment.payload.CustomPaymentMethodAccountPayload;
import bisq.core.payment.payload.PaymentMethod;

import java.util.List;

import org.junit.jupiter.api.Test;

import static bisq.core.trade.validation.CustomPaymentMethodValidation.checkPeersPaymentAccountPayload;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CustomPaymentMethodValidationTest {
    @Test
    void acceptsCustomAccountWithTheNameOfTheOffer() {
        CustomPaymentMethodAccountPayload payload = customPayload("M-Pesa", "Jane Doe\n+254 700 000000");

        assertSame(payload, checkPeersPaymentAccountPayload(payload, customOffer("M-Pesa")));
        assertSame(payload, checkPeersPaymentAccountPayload(payload, customOffer("m pesa")));
    }

    // All custom payment methods share one payment method ID, so the IDs alone would accept any of them
    @Test
    void rejectsCustomAccountWithAnotherName() {
        CustomPaymentMethodAccountPayload payload = customPayload("PayPal", "jane@example.com");

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> checkPeersPaymentAccountPayload(payload, customOffer("M-Pesa")));

        assertEquals("The payment account of the peer is not for the custom payment method of the offer",
                exception.getMessage());
    }

    @Test
    void rejectsCustomAccountWithInvalidName() {
        String tooLong = "a".repeat(CustomPaymentMethodUtil.MAX_NAME_LENGTH + 1);

        assertThrows(IllegalArgumentException.class,
                () -> checkPeersPaymentAccountPayload(customPayload("", "data"), customOffer("M-Pesa")));
        assertThrows(IllegalArgumentException.class,
                () -> checkPeersPaymentAccountPayload(customPayload("M-Pesa\n", "data"), customOffer("M-Pesa")));
        assertThrows(IllegalArgumentException.class,
                () -> checkPeersPaymentAccountPayload(customPayload(tooLong, "data"), customOffer(tooLong)));
    }

    // An offer without a valid name has an empty name
    @Test
    void rejectsCustomAccountIfTheOfferHasNoName() {
        assertThrows(IllegalArgumentException.class,
                () -> checkPeersPaymentAccountPayload(customPayload("M-Pesa", "data"), customOffer("")));
        assertThrows(IllegalArgumentException.class,
                () -> checkPeersPaymentAccountPayload(customPayload("", "data"), customOffer("")));
    }

    @Test
    void rejectsCustomAccountWithInvalidAccountData() {
        String tooLong = "a".repeat(CustomPaymentMethodUtil.MAX_ACCOUNT_DATA_LENGTH + 1);

        for (String accountData : List.of("", "  ", tooLong, "abc\u0000def", "abc\u202Edef")) {
            IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                    () -> checkPeersPaymentAccountPayload(customPayload("M-Pesa", accountData), customOffer("M-Pesa")));
            assertEquals("The account data of the payment account of the peer is not valid", exception.getMessage());
        }
    }

    // The texts of the peer must not get into the message, as the error of a failed trade is shown in a popup
    @Test
    void doesNotReportTheTextsOfThePeer() {
        String name = "[HYPERLINK:evil.io]";
        assertTrue(CustomPaymentMethodUtil.isValidName(name));

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> checkPeersPaymentAccountPayload(customPayload(name, "data"), customOffer("M-Pesa")));

        assertFalse(exception.getMessage().contains("HYPERLINK"));
    }

    // An account of the custom payment method has no signing rule and a free text as account data, so it must not
    // stand in for the account of another payment method
    @Test
    void rejectsCustomAccountForAnOfferOfAnotherPaymentMethod() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> checkPeersPaymentAccountPayload(customPayload("SEPA", "data"), offer(PaymentMethod.SEPA_ID)));

        assertEquals("The payment account of the peer and the offer must both be of the custom payment method, " +
                "or none of them", exception.getMessage());
    }

    // The payment method ID in the account says nothing about the kind of the account
    @Test
    void rejectsCustomAccountWhichNamesThePaymentMethodOfAnOfferOfAnotherPaymentMethod() {
        CustomPaymentMethodAccountPayload payload = new CustomPaymentMethodAccountPayload(PaymentMethod.SEPA_ID, "id");
        payload.setPaymentMethodName("SEPA");
        payload.setAccountData("data");

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> checkPeersPaymentAccountPayload(payload, offer(PaymentMethod.SEPA_ID)));

        assertEquals("The payment account of the peer and the offer must both be of the custom payment method, " +
                "or none of them", exception.getMessage());
    }

    @Test
    void rejectsOtherAccountForACustomOffer() {
        AliPayAccountPayload payload = new AliPayAccountPayload(PaymentMethod.ALI_PAY_ID, "id");

        assertThrows(IllegalArgumentException.class,
                () -> checkPeersPaymentAccountPayload(payload, customOffer("AliPay HK")));
    }

    // The peer sets the payment method ID in the account independently of the kind of the account. The trade step
    // views choose the form for the account by that ID.
    @Test
    void rejectsCustomAccountWithThePaymentMethodIdOfAnotherPaymentMethod() {
        CustomPaymentMethodAccountPayload payload = new CustomPaymentMethodAccountPayload(PaymentMethod.SEPA_ID, "id");
        payload.setPaymentMethodName("M-Pesa");
        payload.setAccountData("data");

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> checkPeersPaymentAccountPayload(payload, customOffer("M-Pesa")));

        assertEquals("The payment method ID in the payment account of the peer does not match the offer",
                exception.getMessage());
    }

    @Test
    void rejectsOtherAccountWithThePaymentMethodIdOfTheCustomPaymentMethod() {
        AliPayAccountPayload payload = new AliPayAccountPayload(PaymentMethod.CUSTOM_ID, "id");

        assertThrows(IllegalArgumentException.class,
                () -> checkPeersPaymentAccountPayload(payload, offer(PaymentMethod.ALI_PAY_ID)));
        assertThrows(IllegalArgumentException.class,
                () -> checkPeersPaymentAccountPayload(payload, customOffer("AliPay HK")));
    }

    // Which account another payment method takes is not a rule of the custom payment method
    @Test
    void acceptsOtherAccountForAnOfferOfAnotherPaymentMethod() {
        AliPayAccountPayload aliPay = new AliPayAccountPayload(PaymentMethod.ALI_PAY_ID, "id");
        assertSame(aliPay, checkPeersPaymentAccountPayload(aliPay, offer(PaymentMethod.ALI_PAY_ID)));

        AliPayAccountPayload sepaInstant = new AliPayAccountPayload(PaymentMethod.SEPA_INSTANT_ID, "id");
        assertSame(sepaInstant, checkPeersPaymentAccountPayload(sepaInstant, offer(PaymentMethod.SEPA_ID)));
    }

    @Test
    void rejectsNull() {
        assertThrows(NullPointerException.class,
                () -> checkPeersPaymentAccountPayload(null, customOffer("M-Pesa")));
        assertThrows(NullPointerException.class,
                () -> checkPeersPaymentAccountPayload(customPayload("M-Pesa", "data"), null));
    }

    private static CustomPaymentMethodAccountPayload customPayload(String paymentMethodName, String accountData) {
        CustomPaymentMethodAccountPayload payload = new CustomPaymentMethodAccountPayload(PaymentMethod.CUSTOM_ID, "id");
        payload.setPaymentMethodName(paymentMethodName);
        payload.setAccountData(accountData);
        return payload;
    }

    private static Offer customOffer(String customPaymentMethodName) {
        Offer offer = offer(PaymentMethod.CUSTOM_ID);
        when(offer.getCustomPaymentMethodName()).thenReturn(customPaymentMethodName);
        return offer;
    }

    // Other tests replace static fields of PaymentMethod with mocks, so the payment method is taken from the list
    private static Offer offer(String paymentMethodId) {
        Offer offer = mock(Offer.class);
        when(offer.getPaymentMethod()).thenReturn(PaymentMethod.getPaymentMethod(paymentMethodId));
        when(offer.getCustomPaymentMethodName()).thenReturn("");
        return offer;
    }
}
