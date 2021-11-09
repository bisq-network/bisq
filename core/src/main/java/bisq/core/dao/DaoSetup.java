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

package bisq.core.dao;

import bisq.core.dao.governance.asset.AssetService;
import bisq.core.dao.governance.ballot.BallotListService;
import bisq.core.dao.governance.blindvote.BlindVoteListService;
import bisq.core.dao.governance.blindvote.MyBlindVoteListService;
import bisq.core.dao.governance.bond.reputation.BondedReputationRepository;
import bisq.core.dao.governance.bond.reputation.MyBondedReputationRepository;
import bisq.core.dao.governance.bond.reputation.MyReputationListService;
import bisq.core.dao.governance.bond.role.BondedRolesRepository;
import bisq.core.dao.governance.period.CycleService;
import bisq.core.dao.governance.proofofburn.ProofOfBurnService;
import bisq.core.dao.governance.proposal.ProposalListPresentation;
import bisq.core.dao.governance.proposal.ProposalService;
import bisq.core.dao.governance.voteresult.MissingDataRequestService;
import bisq.core.dao.governance.voteresult.VoteResultService;
import bisq.core.dao.governance.votereveal.VoteRevealService;
import bisq.core.dao.monitoring.BlindVoteStateMonitoringService;
import bisq.core.dao.monitoring.DaoStateMonitoringService;
import bisq.core.dao.monitoring.ProposalStateMonitoringService;
import bisq.core.dao.node.BsqNode;
import bisq.core.dao.node.BsqNodeProvider;
import bisq.core.dao.node.explorer.ExportJsonFilesService;
import bisq.core.dao.state.DaoStateService;
import bisq.core.dao.state.DaoStateSnapshotService;

import com.google.inject.Inject;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * High level entry point for Dao domain.
 * We initialize all main service classes here to be sure they are started.
 */
public class DaoSetup {
    private final BsqNode bsqNode;
    private final List<DaoSetupService> daoSetupServices = new ArrayList<>();

    @Inject
    public DaoSetup(BsqNodeProvider bsqNodeProvider,
                    DaoStateService daoStateService,
                    CycleService cycleService,
                    BallotListService ballotListService,
                    ProposalService proposalService,
                    ProposalListPresentation proposalListPresentation,
                    BlindVoteListService blindVoteListService,
                    MyBlindVoteListService myBlindVoteListService,
                    VoteRevealService voteRevealService,
                    VoteResultService voteResultService,
                    MissingDataRequestService missingDataRequestService,
                    BondedReputationRepository bondedReputationRepository,
                    BondedRolesRepository bondedRolesRepository,
                    MyReputationListService myReputationListService,
                    MyBondedReputationRepository myBondedReputationRepository,
                    AssetService assetService,
                    ProofOfBurnService proofOfBurnService,
                    DaoFacade daoFacade,
                    ExportJsonFilesService exportJsonFilesService,
                    DaoKillSwitch daoKillSwitch,
                    DaoStateMonitoringService daoStateMonitoringService,
                    ProposalStateMonitoringService proposalStateMonitoringService,
                    BlindVoteStateMonitoringService blindVoteStateMonitoringService,
                    DaoStateSnapshotService daoStateSnapshotService) {

        bsqNode = bsqNodeProvider.getBsqNode();

        // We need to take care of order of execution.
        daoSetupServices.add(daoStateService);
        daoSetupServices.add(cycleService);
        daoSetupServices.add(ballotListService);
        daoSetupServices.add(proposalService);
        daoSetupServices.add(proposalListPresentation);
        daoSetupServices.add(blindVoteListService);
        daoSetupServices.add(myBlindVoteListService);
        daoSetupServices.add(voteRevealService);
        daoSetupServices.add(voteResultService);
        daoSetupServices.add(missingDataRequestService);
        daoSetupServices.add(bondedReputationRepository);
        daoSetupServices.add(bondedRolesRepository);
        daoSetupServices.add(myReputationListService);
        daoSetupServices.add(myBondedReputationRepository);
        daoSetupServices.add(assetService);
        daoSetupServices.add(proofOfBurnService);
        daoSetupServices.add(daoFacade);
        daoSetupServices.add(exportJsonFilesService);
        daoSetupServices.add(daoKillSwitch);
        daoSetupServices.add(daoStateMonitoringService);
        daoSetupServices.add(proposalStateMonitoringService);
        daoSetupServices.add(blindVoteStateMonitoringService);
        daoSetupServices.add(daoStateSnapshotService);

        daoSetupServices.add(bsqNodeProvider.getBsqNode());
    }

    public void onAllServicesInitialized(Consumer<String> errorMessageHandler,
                                         Consumer<String> warnMessageHandler) {
        bsqNode.setErrorMessageHandler(errorMessageHandler);
        bsqNode.setWarnMessageHandler(warnMessageHandler);

        // We add first all listeners at all services and then call the start methods.
        // Some services are listening on others so we need to make sure that the
        // listeners are set before we call start as that might trigger state change
        // which triggers listeners.
        daoSetupServices.forEach(DaoSetupService::addListeners);
        daoSetupServices.forEach(DaoSetupService::start);
    }

    public void shutDown() {
        bsqNode.shutDown();
    }
}
