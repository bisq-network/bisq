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
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import static bisq.proto.grpc.DaoPhaseEnum.DAO_PHASE_BREAK1;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.params.provider.Arguments.arguments;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DaoTestUtilsTest {
    private static final Supplier<DaoTestUtils.SeedBlindVotes> NO_SEED_CHECK = () -> {
        throw new AssertionError("unexpected blind-vote check");
    };
    private static final Runnable NO_PROPOSAL_PAUSE = () -> {
        throw new AssertionError("unexpected proposal pause");
    };

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

        var dao = miner(alice, bob, tip, NO_SEED_CHECK, NO_PROPOSAL_PAUSE);
        dao.generateBlocks(1);

        var reads = inOrder(bobAtBreak, aliceAtBreak);
        reads.verify(bobAtBreak).getChainHeight();
        reads.verify(aliceAtBreak).getNumProposalsInCurrentCycle();
        verify(bobAtBreak).getNumProposalsInCurrentCycle();
        assertEquals(116, tip.get());
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("proposalBreaks")
    void pausesInBreak1UnlessBothPeersAreInSyncWithoutProposals(String scenario,
                                                               GetCycleInfoReply aliceAtTip,
                                                               GetCycleInfoReply bobAtTip,
                                                               boolean expectPause) {
        var tip = new AtomicInteger(115);
        var pauses = new AtomicInteger();
        var alice = mock(GrpcClient.class);
        var bob = mock(GrpcClient.class);
        var afterBreak = GetCycleInfoReply.newBuilder().setChainHeight(116)
                .setPhase(DaoPhaseEnum.DAO_PHASE_BLIND_VOTE).build();
        // Bob first reports an earlier block, in sync and without proposals. His reply at the tip decides.
        var bobBeforeTip = GetCycleInfoReply.newBuilder().setChainHeight(114)
                .setPhase(DaoPhaseEnum.DAO_PHASE_PROPOSAL).setIsDaoStateInSync(true).build();
        when(alice.getCycleInfo()).thenAnswer(call -> tip.get() == 115 ? aliceAtTip : afterBreak);
        when(bob.getCycleInfo()).thenReturn(bobBeforeTip)
                .thenAnswer(call -> tip.get() == 115 ? bobAtTip : afterBreak);

        miner(alice, bob, tip, NO_SEED_CHECK, () -> {
            assertEquals(115, tip.get(), "pause after the block that leaves BREAK1");
            pauses.incrementAndGet();
        }).generateBlocks(1);

        assertEquals(expectPause ? 1 : 0, pauses.get());
        assertEquals(116, tip.get());
    }

    private static Stream<Arguments> proposalBreaks() {
        return Stream.of(
                arguments("empty BREAK1", proposalBreak(true, 0), proposalBreak(true, 0), false),
                arguments("Alice not in sync", proposalBreak(false, 0), proposalBreak(true, 0), true),
                arguments("Bob not in sync", proposalBreak(true, 0), proposalBreak(false, 0), true),
                arguments("proposal at Alice", proposalBreak(true, 1), proposalBreak(true, 0), true),
                arguments("proposal at Bob", proposalBreak(true, 0), proposalBreak(true, 1), true));
    }

    private static GetCycleInfoReply proposalBreak(boolean inSync, int proposals) {
        return GetCycleInfoReply.newBuilder().setChainHeight(115).setPhase(DAO_PHASE_BREAK1)
                .setIsDaoStateInSync(inSync).setNumProposalsInCurrentCycle(proposals).build();
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
        }, NO_PROPOSAL_PAUSE);

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
        var dao = miner(alice, bob, tip, () -> new DaoTestUtils.SeedBlindVotes(120, 2, 0), NO_PROPOSAL_PAUSE);

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
                                      Supplier<DaoTestUtils.SeedBlindVotes> seed, Runnable proposalPause) {
        return new DaoTestUtils(alice, bob, "bitcoind", Duration.ofSeconds(3)) {
            @Override
            SeedBlindVotes readSeedBlindVotes() {
                return seed.get();
            }

            @Override
            void allowProposalsToPropagate() {
                proposalPause.run();
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
