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

import bisq.core.btc.wallet.BsqWalletService;
import bisq.core.dao.governance.proposal.MyProposalListService;
import bisq.core.dao.state.DaoStateListener;
import bisq.core.dao.state.DaoStateService;
import bisq.core.dao.state.model.blockchain.Block;
import bisq.core.dao.state.model.governance.CompensationProposal;
import bisq.core.dao.state.model.governance.Proposal;

import bisq.common.app.DevEnv;

import javax.inject.Inject;
import javax.inject.Singleton;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Singleton
public class BurningManPresentationService implements DaoStateListener {
    // Burn target gets increased by that amount to give more flexibility.
    // Burn target is calculated from reimbursements + estimated BTC fees - burned amounts.
    static final long BURN_TARGET_BOOST_AMOUNT = DevEnv.isDevTesting() ? 1000000 : 10000000;

    private final DaoStateService daoStateService;
    private final MyProposalListService myProposalListService;
    private final BsqWalletService bsqWalletService;
    private final BurningManService burningManService;
    private final BurnTargetService burnTargetService;

    private int currentChainHeight;
    private Optional<Long> burnTarget = Optional.empty();
    private final Map<String, BurningManCandidate> burningManCandidatesByName = new HashMap<>();
    private final Set<ReimbursementModel> reimbursements = new HashSet<>();
    private Optional<Long> averageDistributionPerCycle = Optional.empty();
    private Set<String> myCompensationRequestNames = null;
    private Optional<Set<String>> myGenesisOutputNames = null;

    @Inject
    public BurningManPresentationService(DaoStateService daoStateService,
                                         MyProposalListService myProposalListService,
                                         BsqWalletService bsqWalletService,
                                         BurningManService burningManService,
                                         BurnTargetService burnTargetService) {
        this.daoStateService = daoStateService;
        this.myProposalListService = myProposalListService;
        this.bsqWalletService = bsqWalletService;
        this.burningManService = burningManService;
        this.burnTargetService = burnTargetService;

        daoStateService.addDaoStateListener(this);
        daoStateService.getLastBlock().ifPresent(this::applyBlock);
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // DaoStateListener
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void onParseBlockCompleteAfterBatchProcessing(Block block) {
        applyBlock(block);
    }

    private void applyBlock(Block block) {
        currentChainHeight = block.getHeight();
        burningManCandidatesByName.clear();
        reimbursements.clear();
        burnTarget = Optional.empty();
        myCompensationRequestNames = null;
        averageDistributionPerCycle = Optional.empty();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // API
    ///////////////////////////////////////////////////////////////////////////////////////////

    public long getBurnTarget() {
        if (burnTarget.isPresent()) {
            return burnTarget.get();
        }

        burnTarget = Optional.of(burnTargetService.getBurnTarget(currentChainHeight, getBurningManCandidatesByName().values()));
        return burnTarget.get();
    }

    public long getAverageDistributionPerCycle() {
        if (averageDistributionPerCycle.isPresent()) {
            return averageDistributionPerCycle.get();
        }

        averageDistributionPerCycle = Optional.of(burnTargetService.getAverageDistributionPerCycle(currentChainHeight));
        return averageDistributionPerCycle.get();
    }

    public long getExpectedRevenue(BurningManCandidate burningManCandidate) {
        return Math.round(burningManCandidate.getCappedBurnAmountShare() * getAverageDistributionPerCycle());
    }

    public long getAllowedBurnAmount(BurningManCandidate burningManCandidate) {
        double maxBurnAmount = getBurnTarget() + BURN_TARGET_BOOST_AMOUNT;
        double issuanceShare = burningManCandidate.getCompensationShare();
        double boostedIssuanceShare = burningManCandidate.getBoostedCompensationShare();
        double effectiveBurnOutputShare = burningManCandidate.getCappedBurnAmountShare();
        if (issuanceShare > 0 && maxBurnAmount > 0 && effectiveBurnOutputShare < boostedIssuanceShare) {
            // We reduce it with what he had already burned
            long value = Math.round(boostedIssuanceShare * maxBurnAmount);
            // If below dust we set value to 0
            return value < 546 ? 0 : value;
        } else {
            return 0;
        }
    }

    public Set<ReimbursementModel> getReimbursements() {
        if (!reimbursements.isEmpty()) {
            return reimbursements;
        }

        reimbursements.addAll(burnTargetService.getReimbursements(currentChainHeight));
        return reimbursements;
    }

    public Optional<Set<String>> findMyGenesisOutputNames() {
        // Optional.empty is valid case, so we use null to detect if it was set.
        // As it does not change at new blocks its only set once.
        //noinspection OptionalAssignedToNull
        if (myGenesisOutputNames != null) {
            return myGenesisOutputNames;
        }

        myGenesisOutputNames = daoStateService.getGenesisTx()
                .flatMap(tx -> Optional.ofNullable(bsqWalletService.getTransaction(tx.getId()))
                        .map(genesisTransaction -> genesisTransaction.getOutputs().stream()
                                .filter(transactionOutput -> transactionOutput.isMine(bsqWalletService.getWallet()))
                                .map(transactionOutput -> BurningManService.GENESIS_OUTPUT_PREFIX + transactionOutput.getIndex())
                                .collect(Collectors.toSet())
                        )
                );
        return myGenesisOutputNames;
    }

    public Set<String> getMyCompensationRequestNames() {
        // Can be empty, so we compare with null and reset to null at new block
        if (myCompensationRequestNames != null) {
            return myCompensationRequestNames;
        }
        myCompensationRequestNames = myProposalListService.getList().stream()
                .filter(proposal -> proposal instanceof CompensationProposal)
                .map(Proposal::getName)
                .collect(Collectors.toSet());
        return myCompensationRequestNames;
    }

    public Map<String, BurningManCandidate> getBurningManCandidatesByName() {
        // Cached value is only used for currentChainHeight
        if (!burningManCandidatesByName.isEmpty()) {
            return burningManCandidatesByName;
        }

        burningManCandidatesByName.putAll(burningManService.getBurningManCandidatesByName(currentChainHeight));
        return burningManCandidatesByName;
    }
}
