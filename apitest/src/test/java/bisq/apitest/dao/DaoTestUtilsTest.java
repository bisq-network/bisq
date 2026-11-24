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

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DaoTestUtilsTest {
    @Test
    void readsGovernanceCountsAfterBothPeersReachTheTip() {
        var alice = mock(GrpcClient.class);
        var bob = mock(GrpcClient.class);
        var atBreak = GetCycleInfoReply.newBuilder().setChainHeight(115)
                .setPhase(DaoPhaseEnum.DAO_PHASE_BREAK1).setIsDaoStateInSync(true).build();
        var aliceAtBreak = spy(atBreak);
        var bobAtBreak = spy(atBreak);
        var beforeBreak = atBreak.toBuilder().setChainHeight(114).setPhase(DaoPhaseEnum.DAO_PHASE_PROPOSAL).build();
        var afterBreak = atBreak.toBuilder().setChainHeight(116).setPhase(DaoPhaseEnum.DAO_PHASE_BLIND_VOTE).build();
        var tip = new AtomicInteger(115);
        when(alice.getCycleInfo()).thenAnswer(call -> tip.get() == 115 ? aliceAtBreak : afterBreak);
        when(bob.getCycleInfo()).thenReturn(beforeBreak)
                .thenAnswer(call -> tip.get() == 115 ? bobAtBreak : afterBreak);

        var dao = miner(alice, bob, tip, () -> { throw new AssertionError("unexpected blind-vote check"); });
        dao.generateBlocks(1);

        var reads = inOrder(bobAtBreak, aliceAtBreak);
        reads.verify(bobAtBreak).getChainHeight();
        reads.verify(aliceAtBreak).getNumProposalsInCurrentCycle();
        verify(bobAtBreak).getNumProposalsInCurrentCycle();
        assertEquals(116, tip.get());
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void waitsForBothBlindVoteReceiversAtTheCurrentHeight(boolean seedIsLast) {
        var tip = new AtomicInteger(120);
        var polls = new AtomicInteger();
        var alice = mock(GrpcClient.class);
        var bob = mock(GrpcClient.class);
        when(alice.getCycleInfo()).thenAnswer(call -> blindVoteBreak(tip.get(), 0));
        when(bob.getCycleInfo()).thenAnswer(call ->
                blindVoteBreak(tip.get(), seedIsLast || polls.get() >= 2 ? 2 : 0));
        var dao = miner(alice, bob, tip, () -> switch (polls.getAndIncrement()) {
            case 0 -> new DaoTestUtils.SeedBlindVotes(119, 0, 0);
            case 1 -> new DaoTestUtils.SeedBlindVotes(120, 2, seedIsLast ? 1 : 2);
            default -> new DaoTestUtils.SeedBlindVotes(120, 2, 2);
        });

        dao.generateBlocks(1);

        assertEquals(3, polls.get());
        assertEquals(121, tip.get());
    }

    @Test
    void missingBlindVotesStopMiningAndIdentifyBothReceivers() {
        var tip = new AtomicInteger(120);
        var alice = mock(GrpcClient.class);
        var bob = mock(GrpcClient.class);
        when(alice.getCycleInfo()).thenReturn(blindVoteBreak(120, 0));
        when(bob.getCycleInfo()).thenReturn(blindVoteBreak(120, 1));
        var dao = miner(alice, bob, tip, () -> new DaoTestUtils.SeedBlindVotes(120, 2, 0));

        AssertionError failure = assertThrows(AssertionError.class, () -> dao.generateBlocks(1));

        assertTrue(failure.getMessage().contains("expected 2 blind-vote payloads, bob has 1 and seed has 0"),
                failure::getMessage);
        assertEquals(120, tip.get());
    }

    private static GetCycleInfoReply blindVoteBreak(int height, int votes) {
        return GetCycleInfoReply.newBuilder().setChainHeight(height).setPhase(DaoPhaseEnum.DAO_PHASE_BREAK2)
                .setIsDaoStateInSync(true).setNumBlindVotesInCurrentCycle(votes).build();
    }

    private static DaoTestUtils miner(GrpcClient alice, GrpcClient bob, AtomicInteger tip,
                                      Supplier<DaoTestUtils.SeedBlindVotes> seed) {
        return new DaoTestUtils(alice, bob, "bitcoind", Duration.ofSeconds(3)) {
            @Override
            SeedBlindVotes readSeedBlindVotes() {
                return seed.get();
            }

            @Override
            public String runBitcoinCli(String... args) {
                return switch (args[0]) {
                    case "getnewaddress" -> "miner";
                    case "getblockcount" -> String.valueOf(tip.get());
                    case "generatetoaddress" -> {
                        tip.addAndGet(Integer.parseInt(args[1]));
                        yield "[]";
                    }
                    default -> throw new AssertionError(args[0]);
                };
            }
        };
    }
}
