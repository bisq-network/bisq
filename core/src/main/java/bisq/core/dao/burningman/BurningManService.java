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
import bisq.core.dao.burningman.model.BurnOutputModel;
import bisq.core.dao.burningman.model.BurningManCandidate;
import bisq.core.dao.burningman.model.CompensationModel;
import bisq.core.dao.governance.param.Param;
import bisq.core.dao.governance.proofofburn.ProofOfBurnConsensus;
import bisq.core.dao.governance.proposal.ProposalService;
import bisq.core.dao.governance.proposal.storage.appendonly.ProposalPayload;
import bisq.core.dao.state.DaoStateService;
import bisq.core.dao.state.model.blockchain.BaseTx;
import bisq.core.dao.state.model.blockchain.Tx;
import bisq.core.dao.state.model.blockchain.TxOutput;
import bisq.core.dao.state.model.governance.CompensationProposal;
import bisq.core.dao.state.model.governance.Issuance;
import bisq.core.dao.state.model.governance.IssuanceType;

import bisq.network.p2p.storage.P2PDataStorage;

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

/**
 * Methods are used by the DelayedPayoutTxReceiverService, which is used in the trade protocol for creating and
 * verifying the delayed payout transaction. As verification is done by trade peer it requires data to be deterministic.
 * Parameters listed here must not be changed as they could break verification of the peers
 * delayed payout transaction in case not both traders are using the same version.
 */
@Slf4j
@Singleton
public class BurningManService {
    // Parameters
    // Cannot be changed after release as it would break trade protocol verification of DPT receivers.

    // Prefix for generic names for the genesis outputs. Appended with output index.
    // Used as pre-image for burning.
    static final String GENESIS_OUTPUT_PREFIX = "Bisq co-founder ";

    // Factor for weighting the genesis output amounts.
    private static final double GENESIS_OUTPUT_AMOUNT_FACTOR = 0.1;

    // The number of cycles we go back for the decay function used for compensation request amounts.
    private static final int NUM_CYCLES_COMP_REQUEST_DECAY = 24;

    // The number of cycles we go back for the decay function used for burned amounts.
    private static final int NUM_CYCLES_BURN_AMOUNT_DECAY = 12;

    // Factor for boosting the issuance share (issuance is compensation requests + genesis output).
    // This will be used for increasing the allowed burn amount. The factor gives more flexibility
    // and compensates for those who do not burn. The burn share is capped by that factor as well.
    // E.g. a contributor with 1% issuance share will be able to receive max 10% of the BTC fees or DPT output
    // even if they had burned more and had a higher burn share than 10%.
    public static final double ISSUANCE_BOOST_FACTOR = 10;

    // The max amount the burn share can reach. This value is derived from the min. security deposit in a trade and
    // ensures that an attack where a BM would take all sell offers cannot be economically profitable as they would
    // lose their deposit and cannot gain more than 11% of the DPT payout. As the total amount in a trade is 2 times
    // that deposit plus the trade amount the limiting factor here is 11% (0.15 / 1.3).
    public static final double MAX_BURN_SHARE = 0.11;


    private final DaoStateService daoStateService;
    private final CyclesInDaoStateService cyclesInDaoStateService;
    private final ProposalService proposalService;

    @Inject
    public BurningManService(DaoStateService daoStateService,
                             CyclesInDaoStateService cyclesInDaoStateService,
                             ProposalService proposalService) {
        this.daoStateService = daoStateService;
        this.cyclesInDaoStateService = cyclesInDaoStateService;
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
                                Optional<String> customAddress = compensationProposal.getBurningManReceiverAddress();
                                boolean isCustomAddress = customAddress.isPresent();
                                Optional<String> receiverAddress;
                                if (isCustomAddress) {
                                    receiverAddress = customAddress;
                                } else {
                                    // We take change address from compensation request
                                    receiverAddress = daoStateService.getTx(compensationProposal.getTxId())
                                            .map(this::getAddressFromCompensationRequest);
                                }
                                if (receiverAddress.isPresent()) {
                                    int issuanceHeight = issuance.getChainHeight();
                                    long issuanceAmount = getIssuanceAmountForCompensationRequest(issuance);
                                    int cycleIndex = cyclesInDaoStateService.getCycleIndexAtChainHeight(issuanceHeight);
                                    if (isValidCompensationRequest(name, cycleIndex, issuanceAmount)) {
                                        long decayedIssuanceAmount = getDecayedCompensationAmount(issuanceAmount, issuanceHeight, chainHeight);
                                        long issuanceDate = daoStateService.getBlockTime(issuanceHeight);
                                        candidate.addCompensationModel(CompensationModel.fromCompensationRequest(receiverAddress.get(),
                                                isCustomAddress,
                                                issuanceAmount,
                                                decayedIssuanceAmount,
                                                issuanceHeight,
                                                issuance.getTxId(),
                                                issuanceDate,
                                                cycleIndex));
                                    }
                                }
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
                            issuanceDate));
                    addBurnOutputModel(chainHeight, proofOfBurnOpReturnTxOutputByHash, name, candidate);
                }));

        Collection<BurningManCandidate> burningManCandidates = burningManCandidatesByName.values();
        double totalDecayedCompensationAmounts = burningManCandidates.stream()
                .mapToDouble(BurningManCandidate::getAccumulatedDecayedCompensationAmount)
                .sum();
        double totalDecayedBurnAmounts = burningManCandidates.stream()
                .mapToDouble(BurningManCandidate::getAccumulatedDecayedBurnAmount)
                .sum();
        burningManCandidates.forEach(candidate -> candidate.calculateShares(totalDecayedCompensationAmounts, totalDecayedBurnAmounts));

        double sumAllCappedBurnAmountShares = burningManCandidates.stream()
                .filter(candidate -> candidate.getBurnAmountShare() >= candidate.getMaxBoostedCompensationShare())
                .mapToDouble(BurningManCandidate::getMaxBoostedCompensationShare)
                .sum();
        double sumAllNonCappedBurnAmountShares = burningManCandidates.stream()
                .filter(candidate -> candidate.getBurnAmountShare() < candidate.getMaxBoostedCompensationShare())
                .mapToDouble(BurningManCandidate::getBurnAmountShare)
                .sum();
        burningManCandidates.forEach(candidate -> candidate.calculateCappedAndAdjustedShares(sumAllCappedBurnAmountShares, sumAllNonCappedBurnAmountShares));

        return burningManCandidatesByName;
    }

    String getLegacyBurningManAddress(int chainHeight) {
        return daoStateService.getParamValue(Param.RECIPIENT_BTC_ADDRESS, chainHeight);
    }

    Set<BurningManCandidate> getActiveBurningManCandidates(int chainHeight) {
        return getBurningManCandidatesByName(chainHeight).values().stream()
                .filter(burningManCandidate -> burningManCandidate.getCappedBurnAmountShare() > 0)
                .filter(candidate -> candidate.getReceiverAddress().isPresent())
                .collect(Collectors.toSet());
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Private
    ///////////////////////////////////////////////////////////////////////////////////////////

    Map<P2PDataStorage.ByteArray, Set<TxOutput>> getProofOfBurnOpReturnTxOutputByHash(int chainHeight) {
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

    private boolean isValidCompensationRequest(String name, int cycleIndex, long issuanceAmount) {
        // Up to cycle 15 the RefundAgent made reimbursement requests as compensation requests. We filter out those entries.
        // As it is mixed with RefundAgents real compensation requests we take out all above 3500 BSQ.
        boolean isReimbursementOfRefundAgent = name.equals("RefundAgent") && cycleIndex <= 15 && issuanceAmount > 350000;
        return !isReimbursementOfRefundAgent;
    }

    private long getDecayedCompensationAmount(long amount, int issuanceHeight, int chainHeight) {
        int chainHeightOfPastCycle = cyclesInDaoStateService.getChainHeightOfPastCycle(chainHeight, NUM_CYCLES_COMP_REQUEST_DECAY);
        return getDecayedAmount(amount, issuanceHeight, chainHeight, chainHeightOfPastCycle);
    }

    // Linear decay between currentBlockHeight (100% of amount) and issuanceHeight
    // chainHeightOfPastCycle is currentBlockHeight - numCycles*cycleDuration. It changes with each block and
    // distance to currentBlockHeight is the same if cycle durations have not changed (possible via DAo voting but never done).
    @VisibleForTesting
    static long getDecayedAmount(long amount,
                                 int issuanceHeight,
                                 int currentBlockHeight,
                                 int chainHeightOfPastCycle) {
        if (issuanceHeight > currentBlockHeight)
            throw new IllegalArgumentException("issuanceHeight must not be larger than currentBlockHeight. issuanceHeight=" + issuanceHeight + "; currentBlockHeight=" + currentBlockHeight);
        if (currentBlockHeight < 0)
            throw new IllegalArgumentException("currentBlockHeight must not be negative. currentBlockHeight=" + currentBlockHeight);
        if (amount < 0)
            throw new IllegalArgumentException("amount must not be negative. amount" + amount);
        if (issuanceHeight < 0)
            throw new IllegalArgumentException("issuanceHeight must not be negative. issuanceHeight=" + issuanceHeight);

        if (currentBlockHeight <= chainHeightOfPastCycle) {
            return amount;
        }

        double factor = Math.max(0, (issuanceHeight - chainHeightOfPastCycle) / (double) (currentBlockHeight - chainHeightOfPastCycle));
        long weighted = Math.round(amount * factor);
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
                    int cycleIndex = cyclesInDaoStateService.getCycleIndexAtChainHeight(burnOutputHeight);
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
        int chainHeightOfPastCycle = cyclesInDaoStateService.getChainHeightOfPastCycle(chainHeight, NUM_CYCLES_BURN_AMOUNT_DECAY);
        return getDecayedAmount(amount,
                issuanceHeight,
                chainHeight,
                chainHeightOfPastCycle);
    }

    private long getDecayedGenesisOutputAmount(long amount) {
        return Math.round(amount * GENESIS_OUTPUT_AMOUNT_FACTOR);
    }
}
