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

package bisq.apitest.method;

import bisq.apitest.dao.DaoTestUtils;

import bisq.cli.GrpcClient;
import protobuf.PaymentAccount;

import java.math.BigDecimal;
import java.util.function.Supplier;

import org.junit.jupiter.api.BeforeAll;

import static bisq.core.btc.wallet.Restrictions.getDefaultBuyerSecurityDepositAsPercent;
import static java.lang.String.format;

/**
 * Base class for method-level API tests that run against the docker-compose stack
 * (bitcoind + seednode + arb + alice + bob) brought up by
 * {@code apitest/docker/run-e2e-tests.sh}.
 *
 * <p>Unlike the legacy {@link bisq.apitest.ApiTestCase}, this base class does not
 * spawn local subprocesses via {@link bisq.apitest.Scaffold}; it only opens gRPC
 * clients against daemons that are already running and configured via system
 * properties (same convention used by {@link bisq.apitest.dao.DaoTestBase}):
 *
 * <pre>
 *   -DapiHost.alice=alice            host of Alice's daemon (default "localhost")
 *   -DapiPort.alice=9998             gRPC port for Alice's daemon
 *   -DapiHost.bob=bob                host of Bob's daemon (default "localhost")
 *   -DapiPort.bob=9999               gRPC port for Bob's daemon
 *   -DapiHost.arb=arb                host of Arb's daemon (default "localhost")
 *   -DapiPort.arb=9997               gRPC port for Arb's daemon
 *   -DapiPassword=xyz                shared --apiPassword
 * </pre>
 *
 * <p>Tests extending this base must be read-only or idempotent against the shared
 * stack — there is no per-test scaffold teardown that restores wallet/DAO state.
 */
public abstract class DockerMethodTest {

    protected static GrpcClient aliceClient;
    protected static GrpcClient bobClient;
    protected static GrpcClient arbClient;
    /** Bitcoin / chain helpers: mineBlocks, confirmTx, etc. Reuses DAO suite's utility. */
    protected static DaoTestUtils chain;

    @BeforeAll
    public static void connectGrpcClients() {
        String pw = System.getProperty("apiPassword", "xyz");
        aliceClient = client("alice", 9998, pw);
        bobClient = client("bob", 9999, pw);
        arbClient = client("arb", 9997, pw);
        String bitcoindContainer = System.getProperty("bitcoindContainer", "bitcoind");
        chain = new DaoTestUtils(aliceClient, bobClient, bitcoindContainer);
        // Make sure both nodes are caught up before any subclass test starts. Without
        // this, the very first test in a fresh-stack run races the chain parser.
        chain.awaitDaoStateReady(aliceClient, "alice");
        chain.awaitDaoStateReady(bobClient, "bob");
    }

    /** Mine N regtest blocks and wait until alice + bob both see the new tip. */
    protected static void mineBlocks(int n) {
        chain.generateBlocks(n);
    }

    /** Sleep helper preserved for parity with the old MethodTest API.
     *  Prefer {@link #awaitCond} or {@link #awaitOfferActivated} for state-gated waits —
     *  fixed sleeps are flaky and slow. Use only for testing time-bound behavior. */
    protected static void sleep(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException ex) { Thread.currentThread().interrupt(); }
    }

    /** Safety-net upper bound for any state-gated wait. NOT used to time the test —
     *  test passes the moment the predicate flips. Sized so a slow CI box still passes. */
    protected static final long WAIT_TIMEOUT_MS = 60_000;

    /** Block until {@code cond} holds. Polls every 250ms (see DaoTestUtils.await).
     *  Throws AssertionError after WAIT_TIMEOUT_MS as a safety net. */
    protected static void awaitCond(java.util.function.BooleanSupplier cond, String label) {
        bisq.apitest.dao.DaoTestUtils.await(cond, WAIT_TIMEOUT_MS, label);
    }

    /** Block until alice's offer {@code id} flips to activated. */
    protected static void awaitOfferActivated(String id) {
        awaitCond(() -> aliceClient.getOffer(id).getIsActivated(),
                "alice's offer " + id + " activated");
    }

    private static GrpcClient client(String name, int defaultPort, String pw) {
        String host = System.getProperty("apiHost." + name, "localhost");
        int port = Integer.parseInt(System.getProperty("apiPort." + name, String.valueOf(defaultPort)));
        return new GrpcClient(host, port, pw);
    }

    /**
     * Create an F2F payment account on the given daemon. Returns the proto directly
     * — converting to bisq.core.payment.PaymentAccount pulls in FiatCurrency which
     * requires JavaFX, and the test classpath here does not include JavaFX modules.
     * Tests only need {@code .getId()} from the returned account.
     */
    protected static PaymentAccount createDummyF2FAccount(GrpcClient grpcClient, String countryCode) {
        String json = "{\n" +
                "  \"_COMMENTS_\": \"This is a dummy account.\",\n" +
                "  \"paymentMethodId\": \"F2F\",\n" +
                "  \"accountName\": \"Dummy " + countryCode.toUpperCase() + " F2F Account " + System.nanoTime() + "\",\n" +
                "  \"city\": \"Anytown\",\n" +
                "  \"contact\": \"Morse Code\",\n" +
                "  \"country\": \"" + countryCode.toUpperCase() + "\",\n" +
                "  \"extraInfo\": \"Salt Lick #213\"\n" +
                "}\n";
        return grpcClient.createPaymentAccount(json);
    }

    public static final Supplier<Double> defaultBuyerSecurityDepositPct = () -> {
        // scale() — decimal places — is the right axis here. precision() (significant
        // digits) would reject valid percentage values like 0.05 / 0.10 / 0.20 even
        // though they're well-formed 2-decimal-place fractions.
        var defaultPct = BigDecimal.valueOf(getDefaultBuyerSecurityDepositAsPercent())
                .stripTrailingZeros();
        if (defaultPct.scale() > 2)
            throw new IllegalStateException(format(
                    "Unexpected decimal scale, expected <= 2 but actual is %d%n."
                            + "Check for changes to Restrictions.getDefaultBuyerSecurityDepositAsPercent()",
                    defaultPct.scale()));
        return defaultPct.movePointRight(2).doubleValue();
    };
}
