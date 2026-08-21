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

package bisq.core.filter;

import bisq.core.payment.payload.PaymentMethod;
import bisq.core.payment.payload.PerfectMoneyAccountPayload;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class PaymentAccountFilterMatcherTests {
    private static final String PAYMENT_METHOD_ID = PaymentMethod.PERFECT_MONEY_ID;
    private static final String GET_METHOD_NAME = "getAccountNr";

    @Test
    void hashValueIsDeterministicAndCanonicalizesCaseAndWhitespace() {
        String hash = PaymentAccountFilterMatcher.hashValue(PAYMENT_METHOD_ID, GET_METHOD_NAME, " Account-123 ");

        assertTrue(PaymentAccountFilterMatcher.isValidHashValue(hash));
        assertEquals(hash, PaymentAccountFilterMatcher.hashValue(PAYMENT_METHOD_ID, GET_METHOD_NAME, "account-123"));
        assertEquals(hash, hash.toLowerCase());
    }

    @Test
    void paymentMethodAndGetterArePartOfHashDomain() {
        String hash = PaymentAccountFilterMatcher.hashValue(PAYMENT_METHOD_ID, GET_METHOD_NAME, "account-123");

        assertNotEquals(hash, PaymentAccountFilterMatcher.hashValue("OTHER", GET_METHOD_NAME, "account-123"));
        assertNotEquals(hash, PaymentAccountFilterMatcher.hashValue(PAYMENT_METHOD_ID, "getHolderName", "account-123"));
    }

    @Test
    void matchesHashedFilterValue() {
        PerfectMoneyAccountPayload payload = perfectMoneyPayload("Account-123");
        String hash = PaymentAccountFilterMatcher.hashValue(PAYMENT_METHOD_ID, GET_METHOD_NAME, " account-123 ");
        PaymentAccountFilter filter = new PaymentAccountFilter(PAYMENT_METHOD_ID, GET_METHOD_NAME, hash);

        assertTrue(PaymentAccountFilterMatcher.matches(payload, filter));
    }

    @Test
    void doesNotMatchWrongHashOrPaymentMethod() {
        PerfectMoneyAccountPayload payload = perfectMoneyPayload("Account-123");
        String hash = PaymentAccountFilterMatcher.hashValue(PAYMENT_METHOD_ID, GET_METHOD_NAME, "other-account");

        assertFalse(PaymentAccountFilterMatcher.matches(payload, new PaymentAccountFilter(PAYMENT_METHOD_ID, GET_METHOD_NAME, hash)));
        assertFalse(PaymentAccountFilterMatcher.matches(payload, new PaymentAccountFilter("OTHER", GET_METHOD_NAME, hash)));
    }

    @Test
    void rejectsMalformedTaggedHash() {
        PerfectMoneyAccountPayload payload = perfectMoneyPayload("Account-123");
        PaymentAccountFilter filter = new PaymentAccountFilter(PAYMENT_METHOD_ID, GET_METHOD_NAME, "sha256-v1:not-hex");

        assertFalse(PaymentAccountFilterMatcher.isValidHashValue(filter.getValue()));
        assertFalse(PaymentAccountFilterMatcher.matches(payload, filter));
    }

    @Test
    void keepsLegacyPlaintextFallback() {
        PerfectMoneyAccountPayload payload = perfectMoneyPayload("Account-123");
        PaymentAccountFilter filter = new PaymentAccountFilter(PAYMENT_METHOD_ID, GET_METHOD_NAME, "account-123");

        assertTrue(PaymentAccountFilterMatcher.matches(payload, filter));
    }

    @Test
    void unknownGetterDoesNotMatch() {
        PerfectMoneyAccountPayload payload = perfectMoneyPayload("Account-123");
        String hash = PaymentAccountFilterMatcher.hashValue(PAYMENT_METHOD_ID, "getMissing", "account-123");

        assertFalse(PaymentAccountFilterMatcher.matches(payload, new PaymentAccountFilter(PAYMENT_METHOD_ID, "getMissing", hash)));
    }

    private PerfectMoneyAccountPayload perfectMoneyPayload(String accountNr) {
        PerfectMoneyAccountPayload payload = new PerfectMoneyAccountPayload(PAYMENT_METHOD_ID, "id");
        payload.setAccountNr(accountNr);
        return payload;
    }
}
