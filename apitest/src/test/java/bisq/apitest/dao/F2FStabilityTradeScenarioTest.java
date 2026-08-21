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

package bisq.apitest.dao;

import bisq.cli.GrpcClient;

import bisq.proto.grpc.OfferInfo;

import protobuf.PaymentAccount;

import lombok.extern.slf4j.Slf4j;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Long-running stability soak: run the F2F v1 trade flow {@value #ITERATIONS} times,
 * alternating which peer is maker/taker and which is BTC buyer/seller each round, to
 * surface state leaks that only appear after many sequential trades — wallet UTXO
 * fragmentation, open/closed-trade list growth, DAO-sync drift, deposit/payout tx
 * confirmation races, or fee-output exhaustion.
 *
 * <p>Uses BTC maker/taker fees (not BSQ) so the rounds do not deplete either peer's BSQ.
 * Each round nets BTC roughly back (minus fees), so the funded regtest wallets sustain it.
 *
 * <p>Runs on a freshly reset stack (@Tag("freshstack")) for the maker-sync reason
 * documented on {@link TradeScenarioTest}, and is opt-in via @Tag("longrunning") — it runs
 * only when the runner is invoked with {@code RUN_LONG_RUNNING_TESTS=true}, not on every PR.
 *
 * <p>Runs {@value #ITERATIONS} full v1 trades back-to-back. An earlier version capped this
 * far lower because the maker's offer-availability guard intermittently NACK'd takes with
 * "chain is not synced" after a few dozen rounds. That was root-caused to a false negative
 * in {@code WalletsSetup.isChainHeightSyncedWithinTolerance}: under rapid block production
 * the wallet's own best-chain height advances past the height the {@code PeerGroup} last
 * tracked for the peer, and the old symmetric {@code Math.abs(peers - best) <= 3} check then
 * reported "not synced" even though the wallet was fully at the chain tip. With that guard
 * fixed (treat at-or-ahead-of-peer as synced) the soak sustains the full run.
 */
@Slf4j
@Tag("freshstack")
@Tag("longrunning")
public class F2FStabilityTradeScenarioTest extends DaoTestBase {

    private static final int ITERATIONS = 100;
    private static final String COUNTRY = "US";
    private static final String CURRENCY = "USD";
    private static final long BTC_AMOUNT_SATS = 1_250_000L; // 0.0125 BTC
    private static final String FIXED_FIAT_PRICE = "50000";  // USD/BTC
    private static final double SECURITY_DEPOSIT_PCT = 15.0;

    @Test
    public void f2fTradeSoak() {
        PaymentAccount aliceF2F = ensureF2F(alice, "alice-f2f-soak");
        PaymentAccount bobF2F = ensureF2F(bob, "bob-f2f-soak");

        for (int i = 1; i <= ITERATIONS; i++) {
            // Alternate maker/taker AND direction so every (role × BTC-side) combination
            // is hit roughly evenly across the soak rather than always the same pairing.
            boolean aliceIsMaker = (i % 2 == 0);
            String direction = (i % 4 < 2) ? "SELL" : "BUY";

            GrpcClient maker = aliceIsMaker ? alice : bob;
            GrpcClient taker = aliceIsMaker ? bob : alice;
            PaymentAccount makerAcct = aliceIsMaker ? aliceF2F : bobF2F;
            PaymentAccount takerAcct = aliceIsMaker ? bobF2F : aliceF2F;

            log.info("=== F2F soak iteration {}/{} maker={} direction={} ===",
                    i, ITERATIONS, aliceIsMaker ? "alice" : "bob", direction);

            OfferInfo offer = DaoTestUtils.placeV1OfferWhenReady(() -> maker.createFixedPricedOffer(
                    direction, CURRENCY, BTC_AMOUNT_SATS, BTC_AMOUNT_SATS, FIXED_FIAT_PRICE,
                    SECURITY_DEPOSIT_PCT, makerAcct.getId(), "BTC"));
            V1TradeFlow.runV1Trade(dao, maker, taker, offer, CURRENCY,
                    BTC_AMOUNT_SATS, takerAcct.getId());
        }
    }

    private PaymentAccount ensureF2F(GrpcClient c, String accountName) {
        for (PaymentAccount existing : c.getPaymentAccounts()) {
            if (accountName.equals(existing.getAccountName())) return existing;
        }
        String json = "{"
                + "\"paymentMethodId\":\"F2F\","
                + "\"accountName\":\"" + accountName + "\","
                + "\"city\":\"Anytown\","
                + "\"contact\":\"Morse Code\","
                + "\"country\":\"" + COUNTRY + "\","
                + "\"extraInfo\":\"f2f-soak\"}";
        return c.createPaymentAccount(json);
    }
}
