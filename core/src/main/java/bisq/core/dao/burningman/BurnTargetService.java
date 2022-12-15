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
import bisq.core.dao.burningman.model.ReimbursementModel;
import bisq.core.dao.governance.param.Param;
import bisq.core.dao.governance.proposal.ProposalService;
import bisq.core.dao.governance.proposal.storage.appendonly.ProposalPayload;
import bisq.core.dao.state.DaoStateService;
import bisq.core.dao.state.model.blockchain.Tx;
import bisq.core.dao.state.model.governance.Cycle;
import bisq.core.dao.state.model.governance.Issuance;
import bisq.core.dao.state.model.governance.IssuanceType;
import bisq.core.dao.state.model.governance.ReimbursementProposal;

import bisq.common.config.Config;
import bisq.common.util.Hex;

import javax.inject.Inject;
import javax.inject.Singleton;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import lombok.extern.slf4j.Slf4j;

import static bisq.core.dao.burningman.BurningManPresentationService.OP_RETURN_DATA_LEGACY_BM_DPT;
import static bisq.core.dao.burningman.BurningManPresentationService.OP_RETURN_DATA_LEGACY_BM_FEES;

/**
 * Burn target related API. Not touching trade protocol aspects and parameters can be changed here without risking to
 * break trade protocol validations.
 */
@Slf4j
@Singleton
class BurnTargetService {
    // Number of cycles for accumulating reimbursement amounts. Used for the burn target.
    private static final int NUM_CYCLES_BURN_TARGET = 12;
    private static final int NUM_CYCLES_AVERAGE_DISTRIBUTION = 3;

    // Estimated block at activation date
    private static final int ACTIVATION_BLOCK = Config.baseCurrencyNetwork().isRegtest() ? 111 : 769845;

    // Default value for the estimated BTC trade fees per month as BSQ sat value (100 sat = 1 BSQ).
    // Default is roughly average of last 12 months at Nov 2022.
    // Can be changed with DAO parameter voting.
    private static final long DEFAULT_ESTIMATED_BTC_TRADE_FEE_REVENUE_PER_CYCLE = 6200000;

    private final DaoStateService daoStateService;
    private final CyclesInDaoStateService cyclesInDaoStateService;
    private final ProposalService proposalService;

    @Inject
    public BurnTargetService(DaoStateService daoStateService,
                             CyclesInDaoStateService cyclesInDaoStateService,
                             ProposalService proposalService) {
        this.daoStateService = daoStateService;
        this.cyclesInDaoStateService = cyclesInDaoStateService;
        this.proposalService = proposalService;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // API
    ///////////////////////////////////////////////////////////////////////////////////////////

    Set<ReimbursementModel> getReimbursements(int chainHeight) {
        Set<ReimbursementModel> reimbursements = new HashSet<>();
        daoStateService.getIssuanceSetForType(IssuanceType.REIMBURSEMENT).stream()
                .filter(issuance -> issuance.getChainHeight() <= chainHeight)
                .forEach(issuance -> getReimbursementProposalsForIssuance(issuance)
                        .forEach(reimbursementProposal -> {
                            int issuanceHeight = issuance.getChainHeight();
                            long issuanceAmount = issuance.getAmount();
                            long issuanceDate = daoStateService.getBlockTime(issuanceHeight);
                            int cycleIndex = cyclesInDaoStateService.getCycleIndexAtChainHeight(issuanceHeight);
                            reimbursements.add(new ReimbursementModel(
                                    issuanceAmount,
                                    issuanceHeight,
                                    issuanceDate,
                                    cycleIndex,
                                    reimbursementProposal.getTxId()));
                        }));
        return reimbursements;
    }

    long getBurnTarget(int chainHeight, Collection<BurningManCandidate> burningManCandidates) {
        // Reimbursements are taken into account at result vote block
        int chainHeightOfPastCycle = cyclesInDaoStateService.getChainHeightOfPastCycle(chainHeight, NUM_CYCLES_BURN_TARGET);
        long accumulatedReimbursements = getAdjustedAccumulatedReimbursements(chainHeight, chainHeightOfPastCycle);

        // Param changes are taken into account at first block at next cycle after voting
        int heightOfFirstBlockOfPastCycle = cyclesInDaoStateService.getHeightOfFirstBlockOfPastCycle(chainHeight, NUM_CYCLES_BURN_TARGET - 1);
        long accumulatedEstimatedBtcTradeFees = getAccumulatedEstimatedBtcTradeFees(chainHeight, heightOfFirstBlockOfPastCycle);

        // Legacy BurningMan
        Set<Tx> proofOfBurnTxs = getProofOfBurnTxs(chainHeight, chainHeightOfPastCycle);
        long burnedAmountFromLegacyBurningManDPT = getBurnedAmountFromLegacyBurningManDPT(proofOfBurnTxs, chainHeight, chainHeightOfPastCycle);
        long burnedAmountFromLegacyBurningMansBtcFees = getBurnedAmountFromLegacyBurningMansBtcFees(proofOfBurnTxs, chainHeight, chainHeightOfPastCycle);

        // Distributed BurningMen
        long burnedAmountFromBurningMen = getBurnedAmountFromBurningMen(burningManCandidates, chainHeight, chainHeightOfPastCycle);

        long burnTarget = accumulatedReimbursements
                + accumulatedEstimatedBtcTradeFees
                - burnedAmountFromLegacyBurningManDPT
                - burnedAmountFromLegacyBurningMansBtcFees
                - burnedAmountFromBurningMen;

        log.info("accumulatedReimbursements: {}\n" +
                        "+ accumulatedEstimatedBtcTradeFees: {}\n" +
                        "- burnedAmountFromLegacyBurningManDPT: {}\n" +
                        "- burnedAmountFromLegacyBurningMansBtcFees: {}\n" +
                        "- burnedAmountFromBurningMen: {}\n" +
                        "= burnTarget: {}\n",
                accumulatedReimbursements,
                accumulatedEstimatedBtcTradeFees,
                burnedAmountFromLegacyBurningManDPT,
                burnedAmountFromLegacyBurningMansBtcFees,
                burnedAmountFromBurningMen,
                burnTarget);
        return burnTarget;
    }

    long getAverageDistributionPerCycle(int chainHeight) {
        // Reimbursements are taken into account at result vote block
        int chainHeightOfPastCycle = cyclesInDaoStateService.getChainHeightOfPastCycle(chainHeight, NUM_CYCLES_AVERAGE_DISTRIBUTION);
        long reimbursements = getAdjustedAccumulatedReimbursements(chainHeight, chainHeightOfPastCycle);

        // Param changes are taken into account at first block at next cycle after voting
        int firstBlockOfPastCycle = cyclesInDaoStateService.getHeightOfFirstBlockOfPastCycle(chainHeight, NUM_CYCLES_AVERAGE_DISTRIBUTION - 1);
        long btcTradeFees = getAccumulatedEstimatedBtcTradeFees(chainHeight, firstBlockOfPastCycle);

        return Math.round((reimbursements + btcTradeFees) / (double) NUM_CYCLES_AVERAGE_DISTRIBUTION);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Private
    ///////////////////////////////////////////////////////////////////////////////////////////

    private Stream<ReimbursementProposal> getReimbursementProposalsForIssuance(Issuance issuance) {
        return proposalService.getProposalPayloads().stream()
                .map(ProposalPayload::getProposal)
                .filter(proposal -> issuance.getTxId().equals(proposal.getTxId()))
                .filter(proposal -> proposal instanceof ReimbursementProposal)
                .map(proposal -> (ReimbursementProposal) proposal);
    }

    private long getAdjustedAccumulatedReimbursements(int chainHeight, int fromBlock) {
        return getReimbursements(chainHeight).stream()
                .filter(reimbursementModel -> reimbursementModel.getHeight() > fromBlock)
                .filter(reimbursementModel -> reimbursementModel.getHeight() <= chainHeight)
                .mapToLong(reimbursementModel -> {
                    long amount = reimbursementModel.getAmount();
                    if (reimbursementModel.getHeight() > ACTIVATION_BLOCK) {
                        // As we do not pay out the losing party's security deposit we adjust this here.
                        // We use 15% as the min. security deposit as we do not have the detail data.
                        // A trade with 1 BTC has 1.3 BTC in the DPT which goes to BM. The reimbursement is
                        // only BSQ equivalent to 1.15 BTC. So we map back  the 1.15 BTC to 1.3 BTC to account for
                        // that what the BM received.
                        // There are multiple unknowns included:
                        // - Real security deposit can be higher
                        // - Refund agent can make a custom payout, paying out more or less than expected
                        // - BSQ/BTC volatility
                        // - Delay between DPT and reimbursement
                        long adjusted = Math.round(amount * 1.3 / 1.15);
                        return adjusted;
                    } else {
                        // For old reimbursements we do not apply the adjustment as we had a different policy for
                        // reimbursing out 100% of the DPT.
                        return amount;
                    }
                })
                .sum();
    }

    // The BTC fees are set by parameter and becomes active at first block of the next cycle after voting.
    private long getAccumulatedEstimatedBtcTradeFees(int chainHeight, int fromBlock) {
        return daoStateService.getCycles().stream()
                .filter(cycle -> cycle.getHeightOfFirstBlock() >= fromBlock)
                .filter(cycle -> cycle.getHeightOfFirstBlock() <= chainHeight)
                .mapToLong(this::getBtcTradeFeeFromParam)
                .sum();
    }

    private long getBtcTradeFeeFromParam(Cycle cycle) {
        int value = daoStateService.getParamValueAsBlock(Param.LOCK_TIME_TRADE_PAYOUT, cycle.getHeightOfFirstBlock());
        // Ignore default value (4320)
        return value != 4320 ? value : DEFAULT_ESTIMATED_BTC_TRADE_FEE_REVENUE_PER_CYCLE;
    }

    private Set<Tx> getProofOfBurnTxs(int chainHeight, int fromBlock) {
        return daoStateService.getProofOfBurnTxs().stream()
                .filter(tx -> tx.getBlockHeight() > fromBlock)
                .filter(tx -> tx.getBlockHeight() <= chainHeight)
                .collect(Collectors.toSet());
    }


    private long getBurnedAmountFromLegacyBurningManDPT(Set<Tx> proofOfBurnTxs, int chainHeight, int fromBlock) {
        // Legacy burningman use those opReturn data to mark their burn transactions from delayed payout transaction cases.
        // opReturn data from delayed payout txs when BM traded with the refund agent: 1701e47e5d8030f444c182b5e243871ebbaeadb5e82f
        // opReturn data from delayed payout txs when BM traded with traders who got reimbursed by the DAO: 1701293c488822f98e70e047012f46f5f1647f37deb7
        return proofOfBurnTxs.stream()
                .filter(tx -> tx.getBlockHeight() > fromBlock)
                .filter(tx -> tx.getBlockHeight() <= chainHeight)
                .filter(tx -> {
                    String hash = Hex.encode(tx.getLastTxOutput().getOpReturnData());
                    return OP_RETURN_DATA_LEGACY_BM_DPT.contains(hash);
                })
                .mapToLong(Tx::getBurntBsq)
                .sum();
    }

    private long getBurnedAmountFromLegacyBurningMansBtcFees(Set<Tx> proofOfBurnTxs, int chainHeight, int fromBlock) {
        // Legacy burningman use the below opReturn data to mark their burn transactions from Btc trade fees.
        return proofOfBurnTxs.stream()
                .filter(tx -> tx.getBlockHeight() > fromBlock)
                .filter(tx -> tx.getBlockHeight() <= chainHeight)
                .filter(tx -> OP_RETURN_DATA_LEGACY_BM_FEES.contains(Hex.encode(tx.getLastTxOutput().getOpReturnData())))
                .mapToLong(Tx::getBurntBsq)
                .sum();
    }

    private long getBurnedAmountFromBurningMen(Collection<BurningManCandidate> burningManCandidates,
                                               int chainHeight,
                                               int fromBlock) {
        return burningManCandidates.stream()
                .map(burningManCandidate -> burningManCandidate.getBurnOutputModels().stream()
                        .filter(burnOutputModel -> burnOutputModel.getHeight() > fromBlock)
                        .filter(burnOutputModel -> burnOutputModel.getHeight() <= chainHeight)
                        .mapToLong(BurnOutputModel::getAmount)
                        .sum())
                .mapToLong(e -> e)
                .sum();
    }

    long getAccumulatedDecayedBurnedAmount(Collection<BurningManCandidate> burningManCandidates, int chainHeight) {
        int fromBlock = cyclesInDaoStateService.getChainHeightOfPastCycle(chainHeight, NUM_CYCLES_BURN_TARGET);
        return getAccumulatedDecayedBurnedAmount(burningManCandidates, chainHeight, fromBlock);
    }

    private long getAccumulatedDecayedBurnedAmount(Collection<BurningManCandidate> burningManCandidates,
                                                   int chainHeight,
                                                   int fromBlock) {
        return burningManCandidates.stream()
                .map(burningManCandidate -> burningManCandidate.getBurnOutputModels().stream()
                        .filter(burnOutputModel -> burnOutputModel.getHeight() > fromBlock)
                        .filter(burnOutputModel -> burnOutputModel.getHeight() <= chainHeight)
                        .mapToLong(BurnOutputModel::getDecayedAmount)
                        .sum())
                .mapToLong(e -> e)
                .sum();
    }
}
