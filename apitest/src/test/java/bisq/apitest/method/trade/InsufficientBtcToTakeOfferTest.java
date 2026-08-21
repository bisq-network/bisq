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

import lombok.extern.slf4j.Slf4j;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static bisq.apitest.config.ApiTestConfig.BSQ;
import static bisq.apitest.config.ApiTestConfig.USD;
import static bisq.cli.CurrencyFormat.formatBtc;
import static java.lang.Math.abs;
import static java.lang.String.format;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static protobuf.OfferDirection.BUY;

/**
 * Fresh-stack test: drains Bob's BTC wallet, then asserts takeOffer fails with
 * UNAVAILABLE. The drain mutates global state irreversibly within the run, hence
 * the fresh-stack requirement.
 */
@Slf4j
@Tag("freshstack")
public class InsufficientBtcToTakeOfferTest extends DockerTradeTest {

    @Test
    public void testTakeOfferWithInsufficientBTC() {
        ensureF2FAccounts("US");

        // Drain Bob's BTC wallet to below the deposit + amount + fee requirement
        // (offer is 0.0125 BTC, needs ~0.0145 BTC available to take). Leave 0.001 BTC.
        long bobsAvailable = bobClient.getBtcBalances().getAvailableBalance();
        long toLeave = 100_000L;
        String sendAmount = formatBtc(abs(toLeave - bobsAvailable));
        bobClient.sendBtc(aliceClient.getUnusedBtcAddress(), sendAmount, "", "");
        mineBlocks(1);
        // Gate on Bob's available balance dropping below the required amount instead of
        // sleeping for the wallet to register the send.
        awaitCond(() -> bobClient.getBtcBalances().getAvailableBalance() < 1_450_000L,
                "bob's available BTC drops below take-offer threshold");

        OfferInfo offer = DaoTestUtils.placeV1OfferWhenReady(() -> aliceClient.createFixedPricedOffer(BUY.name(),
                USD, 1_250_000L, 1_250_000L, "50000",
                defaultBuyerSecurityDepositPct.get(),
                alicesF2F.getId(), BSQ));
        assertFalse(offer.getIsCurrencyForMakerFeeBtc());
        awaitCond(() -> aliceClient.getMyOffersSortedByDate(BUY.name(), USD).size() == 1,
                "alice's USD offer book has the new offer");
        awaitBobSeesOffer(offer.getId(), USD);

        // Try 5 times back-to-back; each must fail with UNAVAILABLE. Daemon state is
        // already deterministic at this point (offer activated, bob underfunded), so
        // no inter-attempt wait is needed.
        for (int i = 0; i < 5; i++) {
            Throwable ex = assertThrows(StatusRuntimeException.class,
                    () -> bobClient.takeOffer(offer.getId(), bobsF2F.getId(), BSQ, 1_250_000L));
            assertEquals(format("UNAVAILABLE: wallet has insufficient btc to take offer with id '%s'",
                    offer.getId()), ex.getMessage());
            assertEquals(1, aliceClient.getMyOffersSortedByDate(BUY.name(), USD).size());
            assertEquals(offer.getId(), bobClient.getOffer(offer.getId()).getId());
        }
    }
}
