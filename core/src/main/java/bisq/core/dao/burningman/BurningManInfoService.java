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

import javax.inject.Inject;
import javax.inject.Singleton;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Singleton
public class BurningManInfoService implements DaoStateListener {
    private final DaoStateService daoStateService;
    private final MyProposalListService myProposalListService;
    private final BsqWalletService bsqWalletService;
    private final BurningManService burningManService;

    private int currentChainHeight;
    private Optional<Long> burnTarget;
    private final Map<String, BurningManCandidate> burningManCandidatesByName = new HashMap<>();
    private final Set<ReimbursementModel> reimbursements = new HashSet<>();
    private Optional<Long> averageDistributionPerCycle = Optional.empty();
    private Set<String> myCompensationRequestNames;
    private Optional<Set<String>> myGenesisOutputNames;

    @Inject
    public BurningManInfoService(DaoStateService daoStateService,
                                 MyProposalListService myProposalListService,
                                 BsqWalletService bsqWalletService,
                                 BurningManService burningManService) {
        this.daoStateService = daoStateService;
        this.myProposalListService = myProposalListService;
        this.bsqWalletService = bsqWalletService;
        this.burningManService = burningManService;

        daoStateService.addDaoStateListener(this);
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // DaoStateListener
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void onParseBlockCompleteAfterBatchProcessing(Block block) {
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

        burnTarget = Optional.of(burningManService.getBurnTarget(currentChainHeight, getBurningManCandidatesByName().values()));
        return burnTarget.get();
    }

    public long getAverageDistributionPerCycle() {
        if (averageDistributionPerCycle.isPresent()) {
            return averageDistributionPerCycle.get();
        }

        averageDistributionPerCycle = Optional.of(burningManService.getAverageDistributionPerCycle(currentChainHeight));
        return averageDistributionPerCycle.get();
    }

    public Set<ReimbursementModel> getReimbursements() {
        if (!reimbursements.isEmpty()) {
            return reimbursements;
        }

        reimbursements.addAll(burningManService.getReimbursements(currentChainHeight));
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
