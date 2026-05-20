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

package bisq.apitest.method.offer;

import bisq.apitest.method.DockerMethodTest;
import protobuf.PaymentAccount;

import io.grpc.StatusRuntimeException;

import lombok.extern.slf4j.Slf4j;

import org.junit.jupiter.api.Test;

import static bisq.apitest.config.ApiTestConfig.BSQ;
import static bisq.apitest.config.ApiTestConfig.BTC;
import static java.lang.String.format;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static protobuf.OfferDirection.BUY;

/**
 * Pure negative-path tests: every call to {@code createFixedPricedOffer} is
 * expected to throw, so no real offers are persisted on Alice's daemon. Only
 * F2F payment accounts are added, which is additive and harmless.
 */
@Slf4j
public class ValidateCreateOfferTest extends DockerMethodTest {

    @Test
    public void testAmtTooLargeShouldThrowException() {
        PaymentAccount usdAccount = createDummyF2FAccount(aliceClient, "US");
        @SuppressWarnings("ResultOfMethodCallIgnored")
        Throwable exception = assertThrows(StatusRuntimeException.class, () ->
                aliceClient.createFixedPricedOffer(BUY.name(),
                        "usd",
                        100000000000L,
                        100000000000L,
                        "10000.0000",
                        defaultBuyerSecurityDepositPct.get(),
                        usdAccount.getId(),
                        BSQ));
        assertTrue(exception.getMessage().toLowerCase().contains("validateoffer"),
                "expected validateoffer rejection, got: " + exception.getMessage());
    }

    @Test
    public void testNoMatchingEURPaymentAccountShouldThrowException() {
        PaymentAccount chfAccount = createDummyF2FAccount(aliceClient, "ch");
        @SuppressWarnings("ResultOfMethodCallIgnored")
        Throwable exception = assertThrows(StatusRuntimeException.class, () ->
                aliceClient.createFixedPricedOffer(BUY.name(),
                        "eur",
                        1250000L,
                        1250000L,
                        "40000.0000",
                        defaultBuyerSecurityDepositPct.get(),
                        chfAccount.getId(),
                        BTC));
        String expectedError = format("UNKNOWN: cannot create EUR offer with payment account %s", chfAccount.getId());
        assertEquals(expectedError, exception.getMessage());
    }

    @Test
    public void testNoMatchingCADPaymentAccountShouldThrowException() {
        PaymentAccount audAccount = createDummyF2FAccount(aliceClient, "au");
        @SuppressWarnings("ResultOfMethodCallIgnored")
        Throwable exception = assertThrows(StatusRuntimeException.class, () ->
                aliceClient.createFixedPricedOffer(BUY.name(),
                        "cad",
                        1250000L,
                        1250000L,
                        "63000.0000",
                        defaultBuyerSecurityDepositPct.get(),
                        audAccount.getId(),
                        BTC));
        String expectedError = format("UNKNOWN: cannot create CAD offer with payment account %s", audAccount.getId());
        assertEquals(expectedError, exception.getMessage());
    }
}
