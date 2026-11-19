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
import bisq.apitest.method.DockerMethodTest;

import bisq.proto.grpc.OfferInfo;

import protobuf.PaymentAccount;

import io.grpc.StatusRuntimeException;

import lombok.extern.slf4j.Slf4j;

import org.junit.jupiter.api.Test;

import static bisq.apitest.config.ApiTestConfig.BTC;
import static bisq.apitest.config.ApiTestConfig.USD;
import static java.lang.String.format;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static protobuf.OfferDirection.BUY;

/**
 * takeOffer must refuse a payment account which does not match the offer, as the desktop offers only matching
 * accounts. Bob is the maker and Alice the taker, so that the check runs on the node of the current build also in the
 * compat run, where Bob runs a release. No trade is started; Bob's offer is cancelled at the end.
 */
@Slf4j
public class TakeOfferWithMismatchedAccountTest extends DockerMethodTest {

    @Test
    public void testTakeOfferWithMismatchedAccount() {
        PaymentAccount bobsUsF2F = createDummyF2FAccount(bobClient, "US");
        PaymentAccount alicesDeF2F = createDummyF2FAccount(aliceClient, "DE");

        OfferInfo offer = DaoTestUtils.placeV1OfferWhenReady(() -> bobClient.createFixedPricedOffer(BUY.name(),
                USD, 1_000_000L, 1_000_000L, "50000",
                defaultBuyerSecurityDepositPct.get(),
                bobsUsF2F.getId(), BTC));
        try {
            mineBlocks(1);
            awaitCond(() -> bobClient.getOffer(offer.getId()).getIsActivated(),
                    "bob's offer " + offer.getId() + " activated");
            awaitCond(() -> {
                try {
                    return offer.getId().equals(aliceClient.getOffer(offer.getId()).getId());
                } catch (RuntimeException ex) {
                    return false;
                }
            }, "alice sees offer " + offer.getId());

            Throwable exception = assertThrows(StatusRuntimeException.class,
                    () -> aliceClient.takeOffer(offer.getId(), alicesDeF2F.getId(), BTC, 1_000_000L));
            String expected = format("INVALID_ARGUMENT: payment account with id '%s' does not match offer with id '%s'",
                    alicesDeF2F.getId(), offer.getId());
            assertEquals(expected, exception.getMessage());
        } finally {
            bobClient.cancelOffer(offer.getId());
        }
    }
}
