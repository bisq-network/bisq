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

import bisq.proto.grpc.OfferInfo;

import lombok.extern.slf4j.Slf4j;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static bisq.apitest.config.ApiTestConfig.BSQ;
import static bisq.apitest.config.ApiTestConfig.BTC;
import static bisq.apitest.config.ApiTestConfig.XMR;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static protobuf.OfferDirection.BUY;
import static protobuf.OfferDirection.SELL;

@Slf4j
public class CreateXMROffersTest extends DockerOfferTest {

    @BeforeAll
    public static void setupAccounts() {
        ensureXmrAccounts();
    }

    @Test
    public void testCreateBuyXmrOffer() {
        verifyXmrOffer(BUY.name(), 1_250_000L, 1_250_000L);
    }

    @Test
    public void testCreateSellXmrOffer() {
        verifyXmrOffer(SELL.name(), 1_250_000L, 1_250_000L);
    }

    @Test
    public void testCreateBuyXmrOfferWithMinBelowAmount() {
        verifyXmrOffer(BUY.name(), 1_250_000L, 1_000_000L);
    }

    private void verifyXmrOffer(String direction, long amount, long minAmount) {
        OfferInfo o = aliceClient.createFixedPricedOffer(direction,
                XMR, amount, minAmount, "0.005",
                defaultBuyerSecurityDepositPct.get(),
                alicesXmrAcct.getId(),
                BSQ);
        assertTrue(o.getIsMyOffer());
        assertTrue(o.getIsMyPendingOffer());
        assertFalse(o.getIsActivated());
        assertNotEquals("", o.getId());
        assertEquals(direction, o.getDirection());
        assertFalse(o.getUseMarketBasedPrice());
        assertEquals("0.00500000", o.getPrice());
        assertEquals(amount, o.getAmount());
        assertEquals(minAmount, o.getMinAmount());
        assertTrue(o.getBuyerSecurityDeposit() > 0);
        assertEquals(alicesXmrAcct.getId(), o.getPaymentAccountId());
        assertEquals(XMR, o.getBaseCurrencyCode());
        assertEquals(BTC, o.getCounterCurrencyCode());
        assertFalse(o.getIsCurrencyForMakerFeeBtc());

        mineBlocks(1);
        awaitOfferActivated(o.getId());

        OfferInfo refetched = aliceClient.getOffer(o.getId());
        assertTrue(refetched.getIsActivated());
        assertFalse(refetched.getIsMyPendingOffer());
    }
}
