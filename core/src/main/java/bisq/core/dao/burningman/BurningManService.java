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

import bisq.core.dao.governance.param.Param;
import bisq.core.dao.governance.period.CycleService;
import bisq.core.dao.governance.proofofburn.ProofOfBurnConsensus;
import bisq.core.dao.governance.proposal.ProposalService;
import bisq.core.dao.governance.proposal.storage.appendonly.ProposalPayload;
import bisq.core.dao.state.DaoStateService;
import bisq.core.dao.state.model.blockchain.BaseTx;
import bisq.core.dao.state.model.blockchain.Tx;
import bisq.core.dao.state.model.blockchain.TxOutput;
import bisq.core.dao.state.model.governance.CompensationProposal;
import bisq.core.dao.state.model.governance.Cycle;
import bisq.core.dao.state.model.governance.Issuance;
import bisq.core.dao.state.model.governance.IssuanceType;
import bisq.core.dao.state.model.governance.ReimbursementProposal;

import bisq.network.p2p.storage.P2PDataStorage;

import bisq.common.util.Hex;

import javax.inject.Inject;
import javax.inject.Singleton;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Charsets;
import com.google.common.collect.ImmutableList;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Singleton
class BurningManService {
    private static final int DAY_AS_BLOCKS = 144;
    private static final int MONTH_AS_BLOCKS = 30 * DAY_AS_BLOCKS; // Ignore 31 days months, as the block time is anyway not exact.
    private static final int YEAR_AS_BLOCKS = 12 * MONTH_AS_BLOCKS;

    // Parameters
    // Cannot be changed after release as it would break trade protocol verification of DPT receivers.

    // Prefix for generic names for the genesis outputs. Appended with output index.
    // Used as pre-image for burning.
    static final String GENESIS_OUTPUT_PREFIX = "Bisq co-founder ";

    // Factor for weighting the genesis output amounts.
    private static final double GENESIS_OUTPUT_AMOUNT_FACTOR = 0.05;

    // The max. age in blocks for the decay function used for compensation request amounts.
    private static final int MAX_COMP_REQUEST_AGE = 2 * YEAR_AS_BLOCKS;

    // The max. age in blocks for the decay function used for burned amounts.
    private static final int MAX_BURN_AMOUNT_AGE = YEAR_AS_BLOCKS;

    // Number of cycles for accumulating reimbursement amounts. Used for the burn target.
    private static final int NUM_REIMBURSEMENT_CYCLES = 12;

    // Default value for the estimated BTC trade fees per month as BSQ sat value (100 sat = 1 BSQ).
    // Default is roughly average of last 12 months at Nov 2022.
    // Can be changed with DAO parameter voting.
    private static final long DEFAULT_ESTIMATED_BTC_FEES = 6200000;

    // Factor for boosting the issuance share (issuance is compensation requests + genesis output).
    // This will be used for increasing the allowed burn amount. The factor gives more flexibility
    // and compensates for those who do not burn. The burn share is capped by that factor as well.
    // E.g. a contributor with 10% issuance share will be able to receive max 20% of the BTC fees or DPT output
    // even if they would have burned more and had a higher burn share than 20%.
    static final double ISSUANCE_BOOST_FACTOR = 2;

    // Burn target gets increased by that amount to give more flexibility.
    // Burn target is calculated from reimbursements + estimated BTC fees - burned amounts.
    static final long BURN_TARGET_BOOST_AMOUNT = 10000000;

    private final DaoStateService daoStateService;
    private final CycleService cycleService;
    private final ProposalService proposalService;

    @Inject
    public BurningManService(DaoStateService daoStateService,
                             CycleService cycleService,
                             ProposalService proposalService) {
        this.daoStateService = daoStateService;
        this.cycleService = cycleService;
        this.proposalService = proposalService;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Package scope API
    ///////////////////////////////////////////////////////////////////////////////////////////

    Map<String, BurningManCandidate> getBurningManCandidatesByName(int chainHeight) {
        Map<String, BurningManCandidate> burningManCandidatesByName = new HashMap<>();
        Map<P2PDataStorage.ByteArray, Set<TxOutput>> proofOfBurnOpReturnTxOutputByHash = getProofOfBurnOpReturnTxOutputByHash(chainHeight);

        // Add contributors who made a compensation request
        daoStateService.getIssuanceSetForType(IssuanceType.COMPENSATION).stream()
                .filter(issuance -> issuance.getChainHeight() <= chainHeight)
                .forEach(issuance -> {
                    getCompensationProposalsForIssuance(issuance).forEach(compensationProposal -> {
                        String name = compensationProposal.getName();
                        burningManCandidatesByName.putIfAbsent(name, new BurningManCandidate());
                                BurningManCandidate candidate = burningManCandidatesByName.get(name);

                                // Issuance
                                compensationProposal.getBurningManReceiverAddress()
                                        .or(() -> daoStateService.getTx(compensationProposal.getTxId())
                                                .map(this::getAddressFromCompensationRequest))
                                        .ifPresent(address -> {
                                            int issuanceHeight = issuance.getChainHeight();
                                            long issuanceAmount = getIssuanceAmountForCompensationRequest(issuance);
                                            int cycleIndex = getCycleIndex(issuanceHeight);
                                            if (isValidReimbursement(name, cycleIndex, issuanceAmount)) {
                                                long decayedIssuanceAmount = getDecayedCompensationAmount(issuanceAmount, issuanceHeight, chainHeight);
                                                long issuanceDate = daoStateService.getBlockTime(issuanceHeight);
                                                candidate.addCompensationModel(CompensationModel.fromCompensationRequest(address,
                                                        issuanceAmount,
                                                        decayedIssuanceAmount,
                                                        issuanceHeight,
                                                        issuance.getTxId(),
                                                        issuanceDate,
                                                        cycleIndex));
                                            }
                                        });

                                addBurnOutputModel(chainHeight, proofOfBurnOpReturnTxOutputByHash, name, candidate);
                            });
                        }
                );

        // Add output receivers of genesis transaction
        daoStateService.getGenesisTx()
                .ifPresent(tx -> tx.getTxOutputs().forEach(txOutput -> {
                    String name = GENESIS_OUTPUT_PREFIX + txOutput.getIndex();
                    burningManCandidatesByName.putIfAbsent(name, new BurningManCandidate());
                    BurningManCandidate candidate = burningManCandidatesByName.get(name);

                    // Issuance
                    int issuanceHeight = txOutput.getBlockHeight();
                    long issuanceAmount = txOutput.getValue();
                    long decayedAmount = getDecayedGenesisOutputAmount(issuanceAmount);
                    long issuanceDate = daoStateService.getBlockTime(issuanceHeight);
                    candidate.addCompensationModel(CompensationModel.fromGenesisOutput(txOutput.getAddress(),
                            issuanceAmount,
                            decayedAmount,
                            issuanceHeight,
                            txOutput.getTxId(),
                            txOutput.getIndex(),
                            issuanceDate,
                            0));
                    addBurnOutputModel(chainHeight, proofOfBurnOpReturnTxOutputByHash, name, candidate);
                }));

        Collection<BurningManCandidate> burningManCandidates = burningManCandidatesByName.values();
        double totalDecayedCompensationAmounts = burningManCandidates.stream()
                .mapToDouble(BurningManCandidate::getAccumulatedDecayedCompensationAmount)
                .sum();
        double totalDecayedBurnAmounts = burningManCandidates.stream()
                .mapToDouble(BurningManCandidate::getAccumulatedDecayedBurnAmount)
                .sum();
        long averageDistributionPerCycle = getAverageDistributionPerCycle(chainHeight);
        long burnTarget = getBurnTarget(chainHeight, burningManCandidates);

        burningManCandidates.forEach(candidate -> {
                    candidate.calculateIssuanceShare(totalDecayedCompensationAmounts);
                    candidate.calculateBurnOutputShare(totalDecayedBurnAmounts);
                    candidate.calculateExpectedRevenue(averageDistributionPerCycle);
                    candidate.calculateAllowedBurnAmount(burnTarget);
                }
        );

        return burningManCandidatesByName;
    }

    Set<ReimbursementModel> getReimbursements(int chainHeight) {
        Set<ReimbursementModel> reimbursements = new HashSet<>();
        daoStateService.getIssuanceSetForType(IssuanceType.REIMBURSEMENT).stream()
                .filter(issuance -> issuance.getChainHeight() <= chainHeight)
                .forEach(issuance -> getReimbursementProposalsForIssuance(issuance)
                        .forEach(reimbursementProposal -> {
                            int issuanceHeight = issuance.getChainHeight();
                            long issuanceAmount = issuance.getAmount();
                            long issuanceDate = daoStateService.getBlockTime(issuanceHeight);
                            int cycleIndex = getCycleIndex(issuanceHeight);
                            reimbursements.add(new ReimbursementModel(
                                    issuanceAmount,
                                    issuanceHeight,
                                    issuanceDate,
                                    cycleIndex));
                        }));
        return reimbursements;
    }

    long getBurnTarget(int chainHeight, Collection<BurningManCandidate> burningManCandidates) {
        int fromBlock = getFirstBlockOfPastCycle(chainHeight, NUM_REIMBURSEMENT_CYCLES);
        long accumulatedReimbursements = getAccumulatedReimbursements(chainHeight, fromBlock);
        long accumulatedEstimatedBtcTradeFees = getAccumulatedEstimatedBtcTradeFees(chainHeight, NUM_REIMBURSEMENT_CYCLES);

        // Legacy BurningMan
        Set<Tx> proofOfBurnTxs = getProofOfBurnTxs(chainHeight, fromBlock);
        long burnedAmountFromLegacyBurningManDPT = getBurnedAmountFromLegacyBurningManDPT(proofOfBurnTxs);
        long burnedAmountFromLegacyBurningMansBtcFees = getBurnedAmountFromLegacyBurningMansBtcFees(proofOfBurnTxs);

        // Distributed BurningMen
        // burningManCandidates are already filtered with chainHeight
        long burnedAmountFromBurningMen = getBurnedAmountFromBurningMen(burningManCandidates, fromBlock);

        return accumulatedReimbursements
                + accumulatedEstimatedBtcTradeFees
                - burnedAmountFromLegacyBurningManDPT
                - burnedAmountFromLegacyBurningMansBtcFees
                - burnedAmountFromBurningMen;
    }

    long getAverageDistributionPerCycle(int chainHeight) {
        int fromBlock = getFirstBlockOfPastCycle(chainHeight, 3);
        long reimbursements = getAccumulatedReimbursements(chainHeight, fromBlock);
        long btcTradeFees = getAccumulatedEstimatedBtcTradeFees(chainHeight, 3);
        return Math.round((reimbursements + btcTradeFees) / 3d);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Private
    ///////////////////////////////////////////////////////////////////////////////////////////


    ///////////////////////////////////////////////////////////////////////////////////////////
    // BurningManCandidatesByName
    ///////////////////////////////////////////////////////////////////////////////////////////

    private Map<P2PDataStorage.ByteArray, Set<TxOutput>> getProofOfBurnOpReturnTxOutputByHash(int chainHeight) {
        Map<P2PDataStorage.ByteArray, Set<TxOutput>> map = new HashMap<>();
        daoStateService.getProofOfBurnOpReturnTxOutputs().stream()
                .filter(txOutput -> txOutput.getBlockHeight() <= chainHeight)
                .forEach(txOutput -> {
                    P2PDataStorage.ByteArray key = new P2PDataStorage.ByteArray(ProofOfBurnConsensus.getHashFromOpReturnData(txOutput.getOpReturnData()));
                    map.putIfAbsent(key, new HashSet<>());
                    map.get(key).add(txOutput);
                });
        return map;
    }

    private Stream<CompensationProposal> getCompensationProposalsForIssuance(Issuance issuance) {
        return proposalService.getProposalPayloads().stream()
                .map(ProposalPayload::getProposal)
                .filter(proposal -> issuance.getTxId().equals(proposal.getTxId()))
                .filter(proposal -> proposal instanceof CompensationProposal)
                .map(proposal -> (CompensationProposal) proposal);
    }


    private String getAddressFromCompensationRequest(Tx tx) {
        ImmutableList<TxOutput> txOutputs = tx.getTxOutputs();
        // The compensation request tx has usually 4 outputs. If there is no BTC change its 3 outputs.
        // BTC change output is at index 2 if present otherwise
        // we use the BSQ address of the compensation candidate output at index 1.
        // See https://docs.bisq.network/dao-technical-overview.html#compensation-request-txreimbursement-request-tx
        if (txOutputs.size() == 4) {
            return txOutputs.get(2).getAddress();
        } else {
            return txOutputs.get(1).getAddress();
        }
    }

    private long getIssuanceAmountForCompensationRequest(Issuance issuance) {
        // There was a reimbursement for a conference sponsorship with 44776 BSQ. We remove that as well.
        // See https://github.com/bisq-network/compensation/issues/498
        if (issuance.getTxId().equals("01455fc4c88fca0665a5f56a90ff03fb9e3e88c3430ffc5217246e32d180aa64")) {
            return 119400; // That was the compensation part
        } else {
            return issuance.getAmount();
        }
    }

    private int getCycleIndex(int height) {
        return daoStateService.getCycle(height).map(cycleService::getCycleIndex).orElse(0);
    }


    private boolean isValidReimbursement(String name, int cycleIndex, long issuanceAmount) {
        // Up to cycle 15 the RefundAgent made reimbursement requests as compensation requests. We filter out those entries.
        // As it is mixed with RefundAgents real compensation requests we take out all above 3500 BSQ.
        boolean isReimbursementOfRefundAgent = name.equals("RefundAgent") && cycleIndex <= 15 && issuanceAmount > 350000;
        return !isReimbursementOfRefundAgent;
    }

    private long getDecayedCompensationAmount(long amount, int issuanceHeight, int chainHeight) {
        return getDecayedAmount(amount, issuanceHeight, chainHeight, chainHeight - MAX_COMP_REQUEST_AGE, 0);
    }

    // Linear decay between currentBlockHeight (100% of amount) and issuanceHeight (firstBlockOffset % of amount)
    // Values below firstBlockHeight will use the firstBlockOffset as factor for the amount.
    // E.g. if firstBlockOffset is 0.1 the decay goes to 10% and earlier values stay at 10%.
    @VisibleForTesting
    static long getDecayedAmount(long amount,
                                 int issuanceHeight,
                                 int currentBlockHeight,
                                 int firstBlockHeight,
                                 double firstBlockOffset) {
        if (issuanceHeight > currentBlockHeight)
            throw new IllegalArgumentException("issuanceHeight must not be larger than currentBlockHeight. issuanceHeight=" + issuanceHeight + "; currentBlockHeight=" + currentBlockHeight);
        if (currentBlockHeight < 0)
            throw new IllegalArgumentException("currentBlockHeight must not be negative. currentBlockHeight=" + currentBlockHeight);
        if (amount < 0)
            throw new IllegalArgumentException("amount must not be negative. amount" + amount);
        if (issuanceHeight < 0)
            throw new IllegalArgumentException("issuanceHeight must not be negative. issuanceHeight=" + issuanceHeight);

        double factor = Math.max(0, (issuanceHeight - firstBlockHeight) / (double) (currentBlockHeight - firstBlockHeight));
        double factorWithOffset = firstBlockOffset + factor * (1 - firstBlockOffset);
        long weighted = Math.round(amount * factorWithOffset);
        return Math.max(0, weighted);
    }

    private void addBurnOutputModel(int chainHeight,
                                    Map<P2PDataStorage.ByteArray, Set<TxOutput>> proofOfBurnOpReturnTxOutputByHash,
                                    String name,
                                    BurningManCandidate candidate) {
        getProofOfBurnOpReturnTxOutputSetForName(proofOfBurnOpReturnTxOutputByHash, name)
                .forEach(burnOutput -> {
                    int burnOutputHeight = burnOutput.getBlockHeight();
                    Optional<Tx> optionalTx = daoStateService.getTx(burnOutput.getTxId());
                    long burnOutputAmount = optionalTx.map(Tx::getBurntBsq).orElse(0L);
                    long decayedBurnOutputAmount = getDecayedBurnedAmount(burnOutputAmount, burnOutputHeight, chainHeight);
                    long date = optionalTx.map(BaseTx::getTime).orElse(0L);
                    int cycleIndex = getCycleIndex(burnOutputHeight);
                    candidate.addBurnOutputModel(new BurnOutputModel(burnOutputAmount,
                            decayedBurnOutputAmount,
                            burnOutputHeight,
                            burnOutput.getTxId(),
                            date,
                            cycleIndex));
                });
    }

    private static Set<TxOutput> getProofOfBurnOpReturnTxOutputSetForName(Map<P2PDataStorage.ByteArray, Set<TxOutput>> proofOfBurnOpReturnTxOutputByHash,
                                                                          String name) {
        byte[] preImage = name.getBytes(Charsets.UTF_8);
        byte[] hash = ProofOfBurnConsensus.getHash(preImage);
        P2PDataStorage.ByteArray key = new P2PDataStorage.ByteArray(hash);
        if (proofOfBurnOpReturnTxOutputByHash.containsKey(key)) {
            return proofOfBurnOpReturnTxOutputByHash.get(key);
        } else {
            return new HashSet<>();
        }
    }

    private long getDecayedBurnedAmount(long amount, int issuanceHeight, int chainHeight) {
        return getDecayedAmount(amount,
                issuanceHeight,
                chainHeight,
                chainHeight - MAX_BURN_AMOUNT_AGE,
                0);
    }

    private long getDecayedGenesisOutputAmount(long amount) {
        return Math.round(amount * GENESIS_OUTPUT_AMOUNT_FACTOR);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Reimbursements
    ///////////////////////////////////////////////////////////////////////////////////////////

    private Stream<ReimbursementProposal> getReimbursementProposalsForIssuance(Issuance issuance) {
        return proposalService.getProposalPayloads().stream()
                .map(ProposalPayload::getProposal)
                .filter(proposal -> issuance.getTxId().equals(proposal.getTxId()))
                .filter(proposal -> proposal instanceof ReimbursementProposal)
                .map(proposal -> (ReimbursementProposal) proposal);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Burn target
    ///////////////////////////////////////////////////////////////////////////////////////////

    private int getFirstBlockOfPastCycle(int chainHeight, int numPastCycles) {
        Optional<Cycle> optionalCycle = daoStateService.getCycle(chainHeight);
        if (optionalCycle.isEmpty()) {
            return 0;
        }

        Cycle currentCycle = optionalCycle.get();
        return daoStateService.getPastCycle(currentCycle, numPastCycles)
                .map(Cycle::getHeightOfFirstBlock)
                .orElse(daoStateService.getGenesisBlockHeight());
    }

    private long getAccumulatedReimbursements(int chainHeight, int fromBlock) {
        return getReimbursements(chainHeight).stream()
                .filter(reimbursementModel -> reimbursementModel.getHeight() >= fromBlock)
                .mapToLong(ReimbursementModel::getAmount)
                .sum();
    }

    private long getAccumulatedEstimatedBtcTradeFees(int chainHeight, int numCycles) {
        Optional<Cycle> optionalCycle = daoStateService.getCycle(chainHeight);
        if (optionalCycle.isEmpty()) {
            return 0;
        }
        long accumulatedEstimatedBtcTradeFees = 0;
        Cycle candidateCycle = optionalCycle.get();
        for (int i = 0; i < numCycles; i++) {
            //  LOCK_TIME_TRADE_PAYOUT was never used. We re-purpose it as value for BTC fee revenue per cycle. This can be added as oracle data by DAO voting.
            // We cannot change the ParamType to BSQ as that would break consensus
            long estimatedBtcTradeFeesPerCycle = getBtcTradeFeeFromParam(candidateCycle);
            accumulatedEstimatedBtcTradeFees += estimatedBtcTradeFeesPerCycle;
            Optional<Cycle> previousCycle = daoStateService.getPreviousCycle(candidateCycle);
            if (previousCycle.isPresent()) {
                candidateCycle = previousCycle.get();
            } else {
                break;
            }
        }
        return accumulatedEstimatedBtcTradeFees;
    }

    private long getBtcTradeFeeFromParam(Cycle cycle) {
        int value = daoStateService.getParamValueAsBlock(Param.LOCK_TIME_TRADE_PAYOUT, cycle.getHeightOfFirstBlock());
        // Ignore default value (4320)
        return value != 4320 ? value : DEFAULT_ESTIMATED_BTC_FEES;
    }

    private Set<Tx> getProofOfBurnTxs(int chainHeight, int fromBlock) {
        return daoStateService.getProofOfBurnTxs().stream()
                .filter(tx -> tx.getBlockHeight() <= chainHeight)
                .filter(tx -> tx.getBlockHeight() >= fromBlock)
                .collect(Collectors.toSet());
    }


    private long getBurnedAmountFromLegacyBurningManDPT(Set<Tx> proofOfBurnTxs) {
        // Burningman
        // Legacy burningman use those opReturn data to mark their burn transactions from delayed payout transaction cases.
        // opReturn data from delayed payout txs when BM traded with the refund agent: 1701e47e5d8030f444c182b5e243871ebbaeadb5e82f
        // opReturn data from delayed payout txs when BM traded with traders who got reimbursed by the DAO: 1701293c488822f98e70e047012f46f5f1647f37deb7
        return proofOfBurnTxs.stream()
                .filter(e -> {
                    String hash = Hex.encode(e.getLastTxOutput().getOpReturnData());
                    return "1701e47e5d8030f444c182b5e243871ebbaeadb5e82f".equals(hash) ||
                            "1701293c488822f98e70e047012f46f5f1647f37deb7".equals(hash);
                })
                .mapToLong(Tx::getBurntBsq)
                .sum();
    }

    private long getBurnedAmountFromLegacyBurningMansBtcFees(Set<Tx> proofOfBurnTxs) {
        // Burningman
        // Legacy burningman use those opReturn data to mark their burn transactions from delayed payout transaction cases.
        // opReturn data from delayed payout txs when BM traded with the refund agent: 1701e47e5d8030f444c182b5e243871ebbaeadb5e82f
        // opReturn data from delayed payout txs when BM traded with traders who got reimbursed by the DAO: 1701293c488822f98e70e047012f46f5f1647f37deb7
        return proofOfBurnTxs.stream()
                .filter(e -> "1701721206fe6b40777763de1c741f4fd2706d94775d".equals(Hex.encode(e.getLastTxOutput().getOpReturnData())))
                .mapToLong(Tx::getBurntBsq)
                .sum();
    }

    private long getBurnedAmountFromBurningMen(Collection<BurningManCandidate> burningManCandidates, int fromBlock) {
        return burningManCandidates.stream()
                .map(burningManCandidate -> burningManCandidate.getBurnOutputModels().stream()
                        .filter(burnOutputModel -> burnOutputModel.getHeight() >= fromBlock)
                        .mapToLong(BurnOutputModel::getAmount)
                        .sum())
                .mapToLong(e -> e)
                .sum();
    }
}
