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

import protobuf.PaymentAccount;
import bisq.proto.grpc.OfferInfo;

import java.util.ArrayList;
import java.util.List;

import lombok.extern.slf4j.Slf4j;

import org.junit.jupiter.api.Test;

import static bisq.apitest.config.ApiTestConfig.BSQ;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static protobuf.OfferDirection.BUY;

@Slf4j
public class CancelOfferTest extends DockerOfferTest {

    private static final String DIRECTION = BUY.name();
    private static final String CURRENCY_CODE = "CAD";
    private static final int N_OFFERS = 3;

    @Test
    public void testCancelOffer() {
        PaymentAccount cadAccount = getOrCreateF2F("CA");
        List<String> created = new ArrayList<>();
        for (int i = 0; i < N_OFFERS; i++) {
            OfferInfo o = aliceClient.createFixedPricedOffer(DIRECTION,
                    CURRENCY_CODE, 1_250_000L, 1_250_000L, "50000",
                    defaultBuyerSecurityDepositPct.get(),
                    cadAccount.getId(), BSQ);
            created.add(o.getId());
            // Wait for AddToOfferBook to land before creating the next, so the
            // post-create list count is deterministic.
            awaitOfferActivated(o.getId());
        }

        assertEquals(N_OFFERS,
                aliceClient.getMyOffersSortedByDate(DIRECTION, CURRENCY_CODE).size(),
                "all N offers must appear in alice's offer book");

        for (int i = 0; i < N_OFFERS; i++) {
            String id = created.remove(0);
            aliceClient.cancelOffer(id);
            int expectedRemaining = N_OFFERS - i - 1;
            awaitCond(
                    () -> aliceClient.getMyOffersSortedByDate(DIRECTION, CURRENCY_CODE).size() == expectedRemaining,
                    "alice's offer book shrinks to " + expectedRemaining + " after cancelling " + id);
        }

        assertEquals(0,
                aliceClient.getMyOffersSortedByDate(DIRECTION, CURRENCY_CODE).size(),
                "alice's offer book empty after all cancels");
    }
}
