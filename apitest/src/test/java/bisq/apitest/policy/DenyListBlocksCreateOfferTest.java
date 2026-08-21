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

package bisq.apitest.policy;

import bisq.apitest.method.DockerMethodTest;

import io.grpc.StatusRuntimeException;

import java.util.Locale;

import org.junit.jupiter.api.Test;

import protobuf.PaymentAccount;

import static bisq.apitest.config.ApiTestConfig.BSQ;
import static bisq.apitest.config.ApiTestConfig.XMR;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static protobuf.OfferDirection.BUY;

public class DenyListBlocksCreateOfferTest extends DockerMethodTest {
    private static final long AMOUNT = 1_250_000L;

    @Test
    public void testDenyListBannedPaymentMethodBlocksCreateOffer() {
        PaymentAccount usdAccount = createDummyF2FAccount(aliceClient, "US");

        StatusRuntimeException exception = assertThrows(StatusRuntimeException.class, () ->
                aliceClient.createFixedPricedOffer(BUY.name(),
                        "usd",
                        AMOUNT,
                        AMOUNT,
                        "30000.0000",
                        defaultBuyerSecurityDepositPct.get(),
                        usdAccount.getId(),
                        BSQ));

        assertPolicyFailure(exception, "payment method");
    }

    @Test
    public void testDenyListBannedCurrencyBlocksCreateOffer() {
        PaymentAccount xmrAccount = aliceClient.createCryptoCurrencyPaymentAccount(
                "Alice's deny-list XMR account " + System.nanoTime(),
                XMR,
                "44G4jWmSvTEfifSUZzTDnJVLPvYATmq9XhhtDqUof1BGCLceG82EQsVYG9Q9GN4bJcjbAJEc1JD1m5G7iK4UPZqACubV4Mq",
                false);

        StatusRuntimeException exception = assertThrows(StatusRuntimeException.class, () ->
                aliceClient.createFixedPricedOffer(BUY.name(),
                        XMR,
                        AMOUNT,
                        AMOUNT,
                        "0.005",
                        defaultBuyerSecurityDepositPct.get(),
                        xmrAccount.getId(),
                        BSQ));

        assertPolicyFailure(exception, "currency");
    }

    private void assertPolicyFailure(StatusRuntimeException exception, String expectedPolicyTerm) {
        String message = exception.getMessage().toLowerCase(Locale.ROOT);
        assertTrue(message.contains(expectedPolicyTerm) && message.contains("blocked"),
                "expected deny-list policy failure mentioning " + expectedPolicyTerm + ", got: " + exception.getMessage());
    }
}
