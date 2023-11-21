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

package bisq.core.dao.burningman;

import bisq.core.dao.CyclesInDaoStateService;
import bisq.core.dao.burningman.model.BurningManCandidate;
import bisq.core.dao.governance.proofofburn.ProofOfBurnConsensus;
import bisq.core.dao.governance.proposal.ProposalService;
import bisq.core.dao.governance.proposal.storage.appendonly.ProposalPayload;
import bisq.core.dao.state.DaoStateService;
import bisq.core.dao.state.model.blockchain.Tx;
import bisq.core.dao.state.model.governance.CompensationProposal;
import bisq.core.dao.state.model.governance.Issuance;
import bisq.core.dao.state.model.governance.IssuanceType;

import bisq.common.util.Tuple2;

import protobuf.BaseTx;
import protobuf.BaseTxOutput;
import protobuf.TxOutput;
import protobuf.TxOutputType;
import protobuf.TxType;

import com.google.protobuf.ByteString;

import org.bitcoinj.core.Coin;

import javafx.collections.FXCollections;

import java.util.Arrays;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.mockito.stubbing.Answer;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

public class BurningManServiceTest {
    @Test
    public void testGetDecayedAmount() {
        long amount = 100;
        int currentBlockHeight = 1400;
        int fromBlockHeight = 1000;
        assertEquals(0, BurningManService.getDecayedAmount(amount, 1000, currentBlockHeight, fromBlockHeight));
        assertEquals(25, BurningManService.getDecayedAmount(amount, 1100, currentBlockHeight, fromBlockHeight));
        assertEquals(50, BurningManService.getDecayedAmount(amount, 1200, currentBlockHeight, fromBlockHeight));
        assertEquals(75, BurningManService.getDecayedAmount(amount, 1300, currentBlockHeight, fromBlockHeight));

        // cycles with 100 blocks, issuance at block 20, look-back period 3 cycles
        assertEquals(40, BurningManService.getDecayedAmount(amount, 120, 300, 0));
        assertEquals(33, BurningManService.getDecayedAmount(amount, 120, 320, 20));
        assertEquals(27, BurningManService.getDecayedAmount(amount, 120, 340, 40));
        assertEquals(20, BurningManService.getDecayedAmount(amount, 120, 360, 60));
        assertEquals(13, BurningManService.getDecayedAmount(amount, 120, 380, 80));
        assertEquals(7, BurningManService.getDecayedAmount(amount, 120, 399, 99));
        assertEquals(7, BurningManService.getDecayedAmount(amount, 120, 400, 100));
        assertEquals(3, BurningManService.getDecayedAmount(amount, 120, 410, 110));
        assertEquals(40, BurningManService.getDecayedAmount(amount, 220, 400, 100));
    }

    @Nested
    public class BurnShareTest {
        @Mock
        private DaoStateService daoStateService;
        @Mock
        private CyclesInDaoStateService cyclesInDaoStateService;
        @Mock
        private ProposalService proposalService;
        @InjectMocks
        private BurningManService burningManService;

        @BeforeEach
        public void setUp() {
            MockitoAnnotations.initMocks(this);
            when(cyclesInDaoStateService.getChainHeightOfPastCycle(800000, BurningManService.NUM_CYCLES_BURN_AMOUNT_DECAY))
                    .thenReturn(750000);
            when(cyclesInDaoStateService.getChainHeightOfPastCycle(800000, BurningManService.NUM_CYCLES_COMP_REQUEST_DECAY))
                    .thenReturn(700000);
        }

        private void addProofOfBurnTxs(Tx... txs) {
            var txsById = Arrays.stream(txs)
                    .collect(Collectors.toMap(Tx::getId, tx -> tx));
            when(daoStateService.getProofOfBurnOpReturnTxOutputs())
                    .thenReturn(Arrays.stream(txs).map(tx -> tx.getTxOutputs().get(0)).collect(Collectors.toSet()));
            when(daoStateService.getTx(Mockito.anyString()))
                    .thenAnswer((Answer<Optional<Tx>>) inv -> Optional.ofNullable(txsById.get(inv.getArgument(0, String.class))));
        }

        private void addCompensationIssuanceAndPayloads(Collection<Tuple2<Issuance, ProposalPayload>> tuples) {
            when(daoStateService.getIssuanceSetForType(IssuanceType.COMPENSATION))
                    .thenReturn(tuples.stream().map(t -> t.first).collect(Collectors.toSet()));
            when(proposalService.getProposalPayloads())
                    .thenReturn(tuples.stream().map(t -> t.second).collect(Collectors.toCollection(FXCollections::observableArrayList)));
        }

        @SafeVarargs
        private void addCompensationIssuanceAndPayloads(Tuple2<Issuance, ProposalPayload>... tuples) {
            addCompensationIssuanceAndPayloads(Arrays.asList(tuples));
        }

        @Test
        public void testGetBurningManCandidatesByName_capsSumToLessThanUnity_allCapped_oneCappingRoundNeeded() {
            addCompensationIssuanceAndPayloads(
                    compensationIssuanceAndPayload("alice", "0000", 760000, 10000),
                    compensationIssuanceAndPayload("bob", "0001", 770000, 20000)
            );
            addProofOfBurnTxs(
                    proofOfBurnTx("alice", "1000", 780000, 400000),
                    proofOfBurnTx("bob", "1001", 790000, 300000)
            );
            var candidateMap = burningManService.getBurningManCandidatesByName(800000);

            assertEquals(0.5, candidateMap.get("alice").getBurnAmountShare());
            assertEquals(0.5, candidateMap.get("bob").getBurnAmountShare());

            assertEquals(0.11, candidateMap.get("alice").getCappedBurnAmountShare());
            assertEquals(0.11, candidateMap.get("bob").getCappedBurnAmountShare());
        }

        @Test
        public void testGetBurningManCandidatesByName_capsSumToMoreThanUnity_noneCapped_oneCappingRoundNeeded() {
            addCompensationIssuanceAndPayloads(IntStream.range(0, 10).mapToObj(i ->
                    compensationIssuanceAndPayload("alice" + i, "000" + i, 710000, 100000)
            ).collect(Collectors.toList()));

            addProofOfBurnTxs(IntStream.range(0, 10).mapToObj(i ->
                    proofOfBurnTx("alice" + i, "100" + i, 760000, 400000)
            ).toArray(Tx[]::new));

            var candidateMap = burningManService.getBurningManCandidatesByName(800000);

            assertAll(IntStream.range(0, 10).mapToObj(i -> () -> {
                var candidate = candidateMap.get("alice" + i);
                assertEquals(0.11, candidate.getMaxBoostedCompensationShare());
                assertEquals(0.1, candidate.getBurnAmountShare());
                assertEquals(0.1, candidate.getCappedBurnAmountShare());
            }));
        }

        @Test
        public void testGetBurningManCandidatesByName_capsSumToMoreThanUnity_someCapped_twoCappingRoundsNeeded() {
            addCompensationIssuanceAndPayloads(IntStream.range(0, 10).mapToObj(i ->
                    compensationIssuanceAndPayload("alice" + i, "000" + i, 710000, 100000)
            ).collect(Collectors.toList()));

            addProofOfBurnTxs(IntStream.range(0, 10).mapToObj(i ->
                    proofOfBurnTx("alice" + i, "100" + i, 760000, i < 6 ? 400000 : 200000)
            ).toArray(Tx[]::new));

            var candidateMap = burningManService.getBurningManCandidatesByName(800000);

            // Note the expected rounding error below. To prevent DPT verification failures, the
            // capping algorithm output must be well defined to the nearest floating point ULP.
            assertAll(IntStream.range(0, 10).mapToObj(i -> () -> {
                var candidate = candidateMap.get("alice" + i);
                assertEquals(0.11, candidate.getMaxBoostedCompensationShare());
                assertEquals(i < 6 ? 0.125 : 0.0625, candidate.getBurnAmountShare());
                assertEquals(i < 6 ? 0.11 : 0.08499999999999999, candidate.getCappedBurnAmountShare());
            }));
            // Only two capping rounds were required to achieve a burn share total of 100%, so
            // nothing goes to the LBM in this case.
            double burnShareTotal = candidateMap.values().stream().mapToDouble(BurningManCandidate::getCappedBurnAmountShare).sum();
            assertEquals(1.0, burnShareTotal);
        }

        @Test
        public void testGetBurningManCandidatesByName_capsSumToMoreThanUnity_someCapped_threeCappingRoundsNeeded() {
            addCompensationIssuanceAndPayloads(IntStream.range(0, 10).mapToObj(i ->
                    compensationIssuanceAndPayload("alice" + i, "000" + i, 710000, i < 8 ? 123250 : 7000)
            ).collect(Collectors.toList()));

            addProofOfBurnTxs(IntStream.range(0, 10).mapToObj(i ->
                    proofOfBurnTx("alice" + i, "100" + i, 760000, i < 6 ? 400000 : 200000)
            ).toArray(Tx[]::new));

            var candidateMap = burningManService.getBurningManCandidatesByName(800000);

            // Note the expected rounding error below. To prevent DPT verification failures, the
            // capping algorithm output must be well defined to the nearest floating point ULP.
            assertAll(IntStream.range(0, 10).mapToObj(i -> () -> {
                var candidate = candidateMap.get("alice" + i);
                assertEquals(i < 8 ? 0.11 : 0.07, candidate.getMaxBoostedCompensationShare());
                assertEquals(i < 6 ? 0.125 : 0.0625, candidate.getBurnAmountShare());
                assertEquals(i < 6 ? 0.11 : i < 8 ? 0.08499999999999999 : 0.07, candidate.getCappedBurnAmountShare());
            }));
            // Three capping rounds would have been required to achieve a burn share total of
            // 100%, but our capping algorithm only applies two, so 3% ends up going to the LBM
            // in this case, instead of being distributed between `alice6` & `alice7`. The caps
            // sum to more than 100%, however, so we could have avoided giving him any.
            double capTotal = candidateMap.values().stream().mapToDouble(BurningManCandidate::getMaxBoostedCompensationShare).sum();
            double burnShareTotal = candidateMap.values().stream().mapToDouble(BurningManCandidate::getCappedBurnAmountShare).sum();
            assertEquals(1.02, capTotal);
            assertEquals(0.97, burnShareTotal);
        }

        @Test
        public void testGetBurningManCandidatesByName_capsSumToLessThanUnity_allShouldBeCapped_fourCappingRoundsNeeded() {
            addCompensationIssuanceAndPayloads(IntStream.range(0, 10).mapToObj(i ->
                    compensationIssuanceAndPayload("alice" + i, "000" + i, 710000,
                            i < 6 ? 483200 : i == 6 ? 31800 : i == 7 ? 27000 : 21000)
            ).collect(Collectors.toList()));

            addProofOfBurnTxs(IntStream.range(0, 10).mapToObj(i ->
                    proofOfBurnTx("alice" + i, "100" + i, 760000, i < 6 ? 400000 : 200000)
            ).toArray(Tx[]::new));

            var candidateMap = burningManService.getBurningManCandidatesByName(800000);

            // Note the expected rounding error below. To prevent DPT verification failures, the
            // capping algorithm output must be well defined to the nearest floating point ULP.
            assertAll(IntStream.range(0, 10).mapToObj(i -> () -> {
                var candidate = candidateMap.get("alice" + i);
                assertEquals(i < 6 ? 0.11 : i == 6 ? 0.106 : i == 7 ? 0.09 : 0.07, candidate.getMaxBoostedCompensationShare());
                assertEquals(i < 6 ? 0.125 : 0.0625, candidate.getBurnAmountShare());
                assertEquals(i < 6 ? 0.11 : i < 8 ? 0.08499999999999999 : 0.07, candidate.getCappedBurnAmountShare());
            }));
            // Four capping rounds would have been required to achieve a maximum possible burn
            // share total of 99.6%, with all the contributors being capped. But our capping
            // algorithm only applies two rounds, so 3% ends up going to the LBM instead of the
            // minimum possible amount of 0.4% (100% less the cap sum). Contributors `alice6` &
            // `alice7` therefore receive less than they could have done.
            double capTotal = candidateMap.values().stream().mapToDouble(BurningManCandidate::getMaxBoostedCompensationShare).sum();
            double burnShareTotal = candidateMap.values().stream().mapToDouble(BurningManCandidate::getCappedBurnAmountShare).sum();
            assertEquals(0.996, capTotal);
            assertEquals(0.97, burnShareTotal);
        }
    }

    // Returns a cut-down issuance and compensation proposal payload tuple for mocking.
    private static Tuple2<Issuance, ProposalPayload> compensationIssuanceAndPayload(String name,
                                                                                    String txId,
                                                                                    int chainHeight,
                                                                                    long amount) {
        var issuance = new Issuance(txId, chainHeight, amount, null, IssuanceType.COMPENSATION);
        var extraDataMap = Map.of(CompensationProposal.BURNING_MAN_RECEIVER_ADDRESS, "receiverAddress");
        var proposal = new CompensationProposal(name, "link", Coin.valueOf(amount), "bsqAddress", extraDataMap);
        return new Tuple2<>(issuance, new ProposalPayload(proposal.cloneProposalAndAddTxId(txId)));
    }

    // Returns a cut-down proof-of-burn tx for mocking. FIXME: Going via a protobuf object is a bit of a hack.
    private static Tx proofOfBurnTx(String candidateName, String txId, int blockHeight, long burntBsq) {
        byte[] opReturnData = ProofOfBurnConsensus.getOpReturnData(ProofOfBurnConsensus.getHash(candidateName.getBytes(UTF_8)));
        var txOutput = BaseTxOutput.newBuilder()
                .setTxOutput(TxOutput.newBuilder()
                        .setTxOutputType(TxOutputType.PROOF_OF_BURN_OP_RETURN_OUTPUT))
                .setOpReturnData(ByteString.copyFrom(opReturnData))
                .setTxId(txId)
                .setBlockHeight(blockHeight)
                .build();
        return Tx.fromProto(BaseTx.newBuilder()
                .setId(txId)
                .setTx(protobuf.Tx.newBuilder()
                        .addTxOutputs(txOutput)
                        .setTxType(TxType.PROOF_OF_BURN)
                        .setBurntBsq(burntBsq))
                .build());
    }
}
