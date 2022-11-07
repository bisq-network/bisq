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
import bisq.core.dao.governance.proposal.ProposalService;
import bisq.core.dao.governance.proposal.storage.appendonly.ProposalPayload;
import bisq.core.dao.state.DaoStateService;
import bisq.core.dao.state.model.blockchain.Tx;
import bisq.core.dao.state.model.governance.Cycle;
import bisq.core.dao.state.model.governance.Issuance;
import bisq.core.dao.state.model.governance.IssuanceType;
import bisq.core.dao.state.model.governance.ReimbursementProposal;

import bisq.common.app.DevEnv;
import bisq.common.util.Hex;

import javax.inject.Inject;
import javax.inject.Singleton;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import lombok.extern.slf4j.Slf4j;

/**
 * Burn target related API. Not touching trade protocol aspects and parameters can be changed here without risking to
 * break trade protocol validations.
 */
@Slf4j
@Singleton
class BurnTargetService {
    // Number of cycles for accumulating reimbursement amounts. Used for the burn target.
    private static final int NUM_MONTHS_BURN_TARGET = 12;
    private static final int NUM_MONTHS_AVERAGE_DISTRIBUTION = 3;

    // Default value for the estimated BTC trade fees per month as BSQ sat value (100 sat = 1 BSQ).
    // Default is roughly average of last 12 months at Nov 2022.
    // Can be changed with DAO parameter voting.
    private static final long DEFAULT_ESTIMATED_BTC_FEES = DevEnv.isDevMode() ? 100000 : 6200000;

    private final DaoStateService daoStateService;
    private final CycleService cycleService;
    private final ProposalService proposalService;

    @Inject
    public BurnTargetService(DaoStateService daoStateService,
                             CycleService cycleService,
                             ProposalService proposalService) {
        this.daoStateService = daoStateService;
        this.cycleService = cycleService;
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
        int fromBlock = chainHeight - NUM_MONTHS_BURN_TARGET * BurningManService.MONTH_AS_BLOCKS;
        long accumulatedReimbursements = getAccumulatedReimbursements(chainHeight, fromBlock);
        long accumulatedEstimatedBtcTradeFees = getAccumulatedEstimatedBtcTradeFees(chainHeight, fromBlock);

        // Legacy BurningMan
        Set<Tx> proofOfBurnTxs = getProofOfBurnTxs(chainHeight, fromBlock);
        long burnedAmountFromLegacyBurningManDPT = getBurnedAmountFromLegacyBurningManDPT(proofOfBurnTxs, chainHeight, fromBlock);
        long burnedAmountFromLegacyBurningMansBtcFees = getBurnedAmountFromLegacyBurningMansBtcFees(proofOfBurnTxs, chainHeight, fromBlock);

        // Distributed BurningMen
        long burnedAmountFromBurningMen = getBurnedAmountFromBurningMen(burningManCandidates, chainHeight, fromBlock);


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
        int fromBlock = chainHeight - NUM_MONTHS_AVERAGE_DISTRIBUTION * BurningManService.MONTH_AS_BLOCKS;
        long reimbursements = getAccumulatedReimbursements(chainHeight, fromBlock);
        long btcTradeFees = getAccumulatedEstimatedBtcTradeFees(chainHeight, fromBlock);
        return Math.round((reimbursements + btcTradeFees) / (double) NUM_MONTHS_AVERAGE_DISTRIBUTION);
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

    private int getCycleIndex(int height) {
        return daoStateService.getCycle(height).map(cycleService::getCycleIndex).orElse(0);
    }

    private long getAccumulatedReimbursements(int chainHeight, int fromBlock) {
        return getReimbursements(chainHeight).stream()
                .filter(reimbursementModel -> reimbursementModel.getHeight() >= fromBlock)
                .mapToLong(ReimbursementModel::getAmount)
                .sum();
    }

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
        return value != 4320 ? value : DEFAULT_ESTIMATED_BTC_FEES;
    }

    private Set<Tx> getProofOfBurnTxs(int chainHeight, int fromBlock) {
        return daoStateService.getProofOfBurnTxs().stream()
                .filter(tx -> tx.getBlockHeight() >= fromBlock)
                .filter(tx -> tx.getBlockHeight() <= chainHeight)
                .collect(Collectors.toSet());
    }


    private long getBurnedAmountFromLegacyBurningManDPT(Set<Tx> proofOfBurnTxs, int chainHeight, int fromBlock) {
        // Burningman
        // Legacy burningman use those opReturn data to mark their burn transactions from delayed payout transaction cases.
        // opReturn data from delayed payout txs when BM traded with the refund agent: 1701e47e5d8030f444c182b5e243871ebbaeadb5e82f
        // opReturn data from delayed payout txs when BM traded with traders who got reimbursed by the DAO: 1701293c488822f98e70e047012f46f5f1647f37deb7
        return proofOfBurnTxs.stream()
                .filter(tx -> tx.getBlockHeight() >= fromBlock)
                .filter(tx -> tx.getBlockHeight() <= chainHeight)
                .filter(tx -> {
                    String hash = Hex.encode(tx.getLastTxOutput().getOpReturnData());
                    return "1701e47e5d8030f444c182b5e243871ebbaeadb5e82f".equals(hash) ||
                            "1701293c488822f98e70e047012f46f5f1647f37deb7".equals(hash);
                })
                .mapToLong(Tx::getBurntBsq)
                .sum();
    }

    private long getBurnedAmountFromLegacyBurningMansBtcFees(Set<Tx> proofOfBurnTxs, int chainHeight, int fromBlock) {
        // Burningman
        // Legacy burningman use those opReturn data to mark their burn transactions from delayed payout transaction cases.
        // opReturn data from delayed payout txs when BM traded with the refund agent: 1701e47e5d8030f444c182b5e243871ebbaeadb5e82f
        // opReturn data from delayed payout txs when BM traded with traders who got reimbursed by the DAO: 1701293c488822f98e70e047012f46f5f1647f37deb7
        return proofOfBurnTxs.stream()
                .filter(tx -> tx.getBlockHeight() >= fromBlock)
                .filter(tx -> tx.getBlockHeight() <= chainHeight)
                .filter(tx -> "1701721206fe6b40777763de1c741f4fd2706d94775d".equals(Hex.encode(tx.getLastTxOutput().getOpReturnData())))
                .mapToLong(Tx::getBurntBsq)
                .sum();
    }

    private long getBurnedAmountFromBurningMen(Collection<BurningManCandidate> burningManCandidates,
                                               int chainHeight,
                                               int fromBlock) {
        return burningManCandidates.stream()
                .map(burningManCandidate -> burningManCandidate.getBurnOutputModels().stream()
                        .filter(burnOutputModel -> burnOutputModel.getHeight() >= fromBlock)
                        .filter(burnOutputModel -> burnOutputModel.getHeight() <= chainHeight)
                        .mapToLong(BurnOutputModel::getAmount)
                        .sum())
                .mapToLong(e -> e)
                .sum();
    }
}
