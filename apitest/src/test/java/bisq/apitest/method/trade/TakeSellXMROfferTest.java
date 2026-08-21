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

package bisq.apitest.method.trade;

import bisq.apitest.dao.DaoTestUtils;

import bisq.proto.grpc.OfferInfo;
import bisq.proto.grpc.TradeInfo;

import lombok.extern.slf4j.Slf4j;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static bisq.apitest.config.ApiTestConfig.BSQ;
import static bisq.apitest.config.ApiTestConfig.XMR;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static protobuf.OfferDirection.BUY;

/**
 * Fresh-stack test: full v1 trade where Alice (maker) buys BTC with XMR. Mirror
 * of {@link TakeBuyXMROfferTest} with opposite direction.
 */
@Slf4j
@Tag("freshstack")
public class TakeSellXMROfferTest extends DockerTradeTest {

    @Test
    public void testTakeAlicesBuyBTCForXMROffer() {
        ensureXmrAccounts();

        OfferInfo offer = DaoTestUtils.placeV1OfferWhenReady(() -> aliceClient.createFixedPricedOffer(BUY.name(),
                XMR, 1_250_000L, 1_000_000L, "0.00455500",
                defaultBuyerSecurityDepositPct.get(), alicesXmrAcct.getId(), BSQ));
        // Maker fee currency may be auto-converted by the daemon — don't assert.
        mineBlocks(1);
        awaitOfferActivated(offer.getId());
        assertEquals(1, aliceClient.getMyOffers(BUY.name(), XMR).size());

        TradeInfo trade = runV1Trade(offer, /* bobIsBtcBuyer = */ false,
                bobsXmrAcct.getId(), XMR, 1_250_000L);
        assertNotNull(trade);
    }
}
