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

import io.grpc.StatusRuntimeException;

import lombok.extern.slf4j.Slf4j;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static bisq.apitest.config.ApiTestConfig.BSQ;
import static bisq.apitest.config.ApiTestConfig.BTC;
import static io.grpc.Status.Code.NOT_FOUND;
import static java.lang.String.format;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static protobuf.OfferDirection.BUY;

@Slf4j
public class BsqSwapOfferTest extends DockerOfferTest {

    @BeforeEach
    public void mineOneBlock() {
        // mineBlocks already waits until both alice and bob observe the new chain tip,
        // so confirmed-BSQ outputs become spendable before the next test's offer prep.
        mineBlocks(1);
    }

    @Test
    public void testCreateMultipleBsqSwapOffersAndListThem() {
        int n = 4;
        for (int i = 0; i < n; i++) {
            createAndVerifyBsqSwapOffer();
        }
        assertEquals(n, aliceClient.getMyBsqSwapBsqOffersSortedByDate().size());
        assertEquals(n, bobClient.getBsqSwapOffersSortedByDate().size());
    }

    @Test
    public void testCreateSingleBsqSwapOfferAndFetchOnBothSides() {
        createAndVerifyBsqSwapOffer();
    }

    private void createAndVerifyBsqSwapOffer() {
        OfferInfo offer = aliceClient.createBsqSwapOffer(BUY.name(),
                1_000_000L, 1_000_000L, "0.00005");
        assertNotEquals("", offer.getId());
        assertEquals(BUY.name(), offer.getDirection());
        assertEquals("0.00005000", offer.getPrice());
        assertEquals(1_000_000L, offer.getAmount());
        assertEquals(1_000_000L, offer.getMinAmount());
        assertEquals("200.00", offer.getVolume());
        assertEquals("200.00", offer.getMinVolume());
        assertEquals(BSQ, offer.getBaseCurrencyCode());
        assertEquals(BTC, offer.getCounterCurrencyCode());

        awaitOfferVisible(aliceClient, offer.getId(), "alice (maker)");
        awaitOfferVisible(bobClient, offer.getId(), "bob (taker)");
    }

    /**
     * Block until peer {@code c} can fetch the offer by id and reports it activated.
     * Gates on the daemon's own propagation signal — NOT on wall-clock — so this
     * passes the moment p2p delivers, regardless of CI box speed.
     */
    private void awaitOfferVisible(bisq.cli.GrpcClient c, String offerId, String label) {
        awaitCond(() -> {
            try {
                OfferInfo fetched = c.getOffer(offerId);
                return offerId.equals(fetched.getId()) && fetched.getIsActivated();
            } catch (StatusRuntimeException ex) {
                if (ex.getStatus().getCode() != NOT_FOUND) {
                    fail(format("%s saw unexpected gRPC error for offer %s", label, offerId), ex);
                }
                return false;
            }
        }, label + " sees activated bsq swap offer " + offerId);
    }
}
