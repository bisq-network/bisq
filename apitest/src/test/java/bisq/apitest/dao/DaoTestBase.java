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

import bisq.proto.grpc.DaoPhaseEnum;
import bisq.proto.grpc.GetCycleInfoReply;

import lombok.extern.slf4j.Slf4j;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;

/**
 * Base class for DAO governance API tests that run against a docker-compose stack
 * (bitcoind + seednode + arb + alice + bob, all on a shared bridge network).
 *
 * Configuration is supplied via system properties so the same suite can run locally,
 * in CI, or against any pre-existing stack:
 *
 *   -DapiHost.alice=alice            host of Alice's daemon (default "localhost")
 *   -DapiPort.alice=9998             gRPC port for Alice's daemon
 *   -DapiHost.bob=bob                host of Bob's daemon (default "localhost")
 *   -DapiPort.bob=9999               gRPC port for Bob's daemon
 *   -DapiPassword=xyz                shared --apiPassword
 *   -DbitcoindContainer=bitcoind     docker container name for the bitcoind node
 *
 * Tests should not be launched without -DrunApiTests=true (matches the existing
 * apitest convention).
 */
@Slf4j
public abstract class DaoTestBase {

    protected static GrpcClient alice;
    protected static GrpcClient bob;
    protected static DaoTestUtils dao;

    @BeforeAll
    public static void connect() {
        String pw = System.getProperty("apiPassword", "xyz");
        String aliceHost = System.getProperty("apiHost.alice", "localhost");
        int alicePort = Integer.parseInt(System.getProperty("apiPort.alice", "9998"));
        String bobHost = System.getProperty("apiHost.bob", "localhost");
        int bobPort = Integer.parseInt(System.getProperty("apiPort.bob", "9999"));
        String bitcoindContainer = System.getProperty("bitcoindContainer", "bitcoind");

        alice = new GrpcClient(aliceHost, alicePort, pw);
        bob = new GrpcClient(bobHost, bobPort, pw);
        dao = new DaoTestUtils(alice, bob, bitcoindContainer);

        // Wait for both daemons to report a parsed DAO state. Without this the very first
        // test would race the chain parser.
        dao.awaitDaoStateReady(alice, "alice");
        dao.awaitDaoStateReady(bob, "bob");
    }

    @AfterEach
    public void confirmPendingTxs() {
        // Mine 2 blocks after each test so any in-flight BSQ tx (proposal, blind vote)
        // confirms before the next test runs — frees BSQ change outputs back to
        // "spendable" state per Bisq's coin selector.
        if (dao != null) {
            try {
                dao.generateBlocks(2);
            } catch (RuntimeException ignored) {
                // Best-effort cleanup; don't mask test result with teardown failure.
            }
        }
    }

    protected static void inPhase(DaoPhaseEnum expected) {
        GetCycleInfoReply ci = alice.getCycleInfo();
        if (ci.getPhase() != expected) {
            throw new AssertionError("expected phase " + expected + " but daemon reports " + ci.getPhase()
                    + " at height " + ci.getChainHeight());
        }
    }
}
