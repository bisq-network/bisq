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

import bisq.apitest.method.offer.DockerOfferTest;

import bisq.proto.grpc.OfferInfo;

import org.junit.jupiter.api.Test;

import protobuf.PaymentAccount;

import static bisq.apitest.config.ApiTestConfig.BSQ;
import static bisq.apitest.config.ApiTestConfig.USD;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static protobuf.OfferDirection.BUY;

public class IgnoreDenyListAllowsCreateOfferTest extends DockerOfferTest {
    private static final long AMOUNT = 1_250_000L;

    @Test
    public void testIgnoreDenyListAllowsOfferThatFixtureWouldBlock() {
        PaymentAccount usdAccount = createDummyF2FAccount(aliceClient, "US");

        OfferInfo created = aliceClient.createFixedPricedOffer(BUY.name(),
                "usd",
                AMOUNT,
                AMOUNT,
                "30000.0000",
                defaultBuyerSecurityDepositPct.get(),
                usdAccount.getId(),
                BSQ);

        assertTrue(created.getIsMyOffer());
        assertTrue(created.getIsMyPendingOffer());
        assertFalse(created.getIsActivated());
        assertEquals(usdAccount.getId(), created.getPaymentAccountId());
        assertEquals(USD, created.getCounterCurrencyCode());
        awaitOfferActivated(created.getId());
    }
}
