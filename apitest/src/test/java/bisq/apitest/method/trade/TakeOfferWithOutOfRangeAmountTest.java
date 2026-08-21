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

import io.grpc.StatusRuntimeException;

import org.bitcoinj.core.Coin;

import lombok.extern.slf4j.Slf4j;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static bisq.apitest.config.ApiTestConfig.BTC;
import static bisq.apitest.config.ApiTestConfig.USD;
import static java.lang.String.format;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static protobuf.OfferDirection.BUY;

/**
 * Fresh-stack negative-path test: takeOffer must reject amounts outside the
 * offer's min/max range. Does not complete a trade — offer is left open and the
 * stack reset before the next class handles cleanup.
 */
@Slf4j
@Tag("freshstack")
public class TakeOfferWithOutOfRangeAmountTest extends DockerTradeTest {

    @Test
    public void testTakeOfferWithInvalidAmountParam() {
        ensureF2FAccounts("US");

        OfferInfo offer = DaoTestUtils.placeV1OfferWhenReady(() -> aliceClient.createFixedPricedOffer(BUY.name(),
                USD, 1_250_000L, 1_000_000L, "50000",
                defaultBuyerSecurityDepositPct.get(),
                alicesF2F.getId(), BTC));

        awaitOfferActivated(offer.getId());
        awaitBobSeesOffer(offer.getId(), USD);

        assertOutOfRangeRejected(offer, 500_000L);     // below min (1.0M)
        assertOutOfRangeRejected(offer, 5_000_000L);   // above max (1.25M)
    }

    private void assertOutOfRangeRejected(OfferInfo offer, long invalidAmount) {
        Throwable ex = assertThrows(StatusRuntimeException.class,
                () -> bobClient.takeOffer(offer.getId(), bobsF2F.getId(), BTC, invalidAmount));
        String expected = format(
                "INVALID_ARGUMENT: intended trade amount %s is outside offer's min - max amount range of %s - %s",
                Coin.valueOf(invalidAmount).toPlainString(),
                Coin.valueOf(offer.getMinAmount()).toPlainString(),
                Coin.valueOf(offer.getAmount()).toPlainString());
        assertEquals(expected, ex.getMessage());
    }
}
