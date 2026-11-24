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

import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
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

        var dao = new DaoTestUtils(alice, bob, "bitcoind") {
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
        dao.generateBlocks(1);

        var reads = inOrder(bobAtBreak, aliceAtBreak);
        reads.verify(bobAtBreak).getChainHeight();
        reads.verify(aliceAtBreak).getNumProposalsInCurrentCycle();
        verify(bobAtBreak).getNumProposalsInCurrentCycle();
        assertEquals(116, tip.get());
    }
}
