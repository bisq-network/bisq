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

import bisq.core.dao.bonding.lockup.LockupService;
import bisq.core.dao.bonding.unlock.UnlockService;
import bisq.core.dao.governance.asset.AssetService;
import bisq.core.dao.governance.ballot.BallotListPresentation;
import bisq.core.dao.governance.ballot.BallotListService;
import bisq.core.dao.governance.blindvote.BlindVoteListService;
import bisq.core.dao.governance.blindvote.BlindVoteValidator;
import bisq.core.dao.governance.blindvote.MyBlindVoteListService;
import bisq.core.dao.governance.blindvote.network.RepublishGovernanceDataHandler;
import bisq.core.dao.governance.blindvote.storage.BlindVoteStorageService;
import bisq.core.dao.governance.blindvote.storage.BlindVoteStore;
import bisq.core.dao.governance.myvote.MyVoteListService;
import bisq.core.dao.governance.proposal.MyProposalListService;
import bisq.core.dao.governance.proposal.ProposalListPresentation;
import bisq.core.dao.governance.proposal.ProposalService;
import bisq.core.dao.governance.proposal.ProposalValidator;
import bisq.core.dao.governance.proposal.compensation.CompensationProposalService;
import bisq.core.dao.governance.proposal.compensation.CompensationValidator;
import bisq.core.dao.governance.proposal.confiscatebond.ConfiscateBondProposalService;
import bisq.core.dao.governance.proposal.confiscatebond.ConfiscateBondValidator;
import bisq.core.dao.governance.proposal.generic.GenericProposalService;
import bisq.core.dao.governance.proposal.generic.GenericProposalValidator;
import bisq.core.dao.governance.proposal.param.ChangeParamProposalService;
import bisq.core.dao.governance.proposal.param.ChangeParamValidator;
import bisq.core.dao.governance.proposal.reimbursement.ReimbursementProposalService;
import bisq.core.dao.governance.proposal.reimbursement.ReimbursementValidator;
import bisq.core.dao.governance.proposal.removeAsset.RemoveAssetProposalService;
import bisq.core.dao.governance.proposal.removeAsset.RemoveAssetValidator;
import bisq.core.dao.governance.proposal.role.BondedRoleProposalService;
import bisq.core.dao.governance.proposal.role.BondedRoleValidator;
import bisq.core.dao.governance.proposal.storage.appendonly.ProposalStorageService;
import bisq.core.dao.governance.proposal.storage.appendonly.ProposalStore;
import bisq.core.dao.governance.proposal.storage.temp.TempProposalStorageService;
import bisq.core.dao.governance.proposal.storage.temp.TempProposalStore;
import bisq.core.dao.governance.role.BondedRolesService;
import bisq.core.dao.governance.voteresult.MissingDataRequestService;
import bisq.core.dao.governance.voteresult.VoteResultService;
import bisq.core.dao.governance.voteresult.issuance.IssuanceService;
import bisq.core.dao.governance.votereveal.VoteRevealService;
import bisq.core.dao.node.BsqNodeProvider;
import bisq.core.dao.node.full.FullNode;
import bisq.core.dao.node.full.RpcService;
import bisq.core.dao.node.full.network.FullNodeNetworkService;
import bisq.core.dao.node.json.ExportJsonFilesService;
import bisq.core.dao.node.lite.LiteNode;
import bisq.core.dao.node.lite.network.LiteNodeNetworkService;
import bisq.core.dao.node.parser.BlockParser;
import bisq.core.dao.node.parser.TxParser;
import bisq.core.dao.state.DaoState;
import bisq.core.dao.state.DaoStateService;
import bisq.core.dao.state.DaoStateSnapshotService;
import bisq.core.dao.state.DaoStateStorageService;
import bisq.core.dao.state.GenesisTxInfo;
import bisq.core.dao.state.period.CycleService;
import bisq.core.dao.state.period.PeriodService;

import bisq.common.app.AppModule;

import org.springframework.core.env.Environment;

import com.google.inject.Singleton;
import com.google.inject.name.Names;

import static com.google.inject.name.Names.named;

public class DaoModule extends AppModule {

    public DaoModule(Environment environment) {
        super(environment);
    }

    @Override
    protected void configure() {
        bind(DaoSetup.class).in(Singleton.class);
        bind(DaoFacade.class).in(Singleton.class);

        // Node, parser
        bind(BsqNodeProvider.class).in(Singleton.class);
        bind(FullNode.class).in(Singleton.class);
        bind(LiteNode.class).in(Singleton.class);
        bind(RpcService.class).in(Singleton.class);
        bind(BlockParser.class).in(Singleton.class);
        bind(FullNodeNetworkService.class).in(Singleton.class);
        bind(LiteNodeNetworkService.class).in(Singleton.class);

        // DaoState
        bind(GenesisTxInfo.class).in(Singleton.class);
        bind(DaoState.class).in(Singleton.class);
        bind(DaoStateService.class).in(Singleton.class);
        bind(DaoStateSnapshotService.class).in(Singleton.class);
        bind(DaoStateStorageService.class).in(Singleton.class);

        bind(ExportJsonFilesService.class).in(Singleton.class);

        // Period
        bind(CycleService.class).in(Singleton.class);
        bind(PeriodService.class).in(Singleton.class);

        // blockchain parser
        bind(TxParser.class).in(Singleton.class);

        // Proposal
        bind(ProposalService.class).in(Singleton.class);

        bind(MyProposalListService.class).in(Singleton.class);
        bind(ProposalListPresentation.class).in(Singleton.class);

        bind(ProposalStore.class).in(Singleton.class);
        bind(ProposalStorageService.class).in(Singleton.class);
        bind(TempProposalStore.class).in(Singleton.class);
        bind(TempProposalStorageService.class).in(Singleton.class);
        bind(ProposalValidator.class).in(Singleton.class);

        bind(CompensationValidator.class).in(Singleton.class);
        bind(CompensationProposalService.class).in(Singleton.class);

        bind(ReimbursementValidator.class).in(Singleton.class);
        bind(ReimbursementProposalService.class).in(Singleton.class);

        bind(ChangeParamValidator.class).in(Singleton.class);
        bind(ChangeParamProposalService.class).in(Singleton.class);

        bind(BondedRoleValidator.class).in(Singleton.class);
        bind(BondedRoleProposalService.class).in(Singleton.class);

        bind(ConfiscateBondValidator.class).in(Singleton.class);
        bind(ConfiscateBondProposalService.class).in(Singleton.class);

        bind(GenericProposalValidator.class).in(Singleton.class);
        bind(GenericProposalService.class).in(Singleton.class);

        bind(RemoveAssetValidator.class).in(Singleton.class);
        bind(RemoveAssetProposalService.class).in(Singleton.class);


        // Ballot
        bind(BallotListService.class).in(Singleton.class);
        bind(BallotListPresentation.class).in(Singleton.class);

        // MyVote
        bind(MyVoteListService.class).in(Singleton.class);

        // BlindVote
        bind(BlindVoteListService.class).in(Singleton.class);
        bind(BlindVoteStore.class).in(Singleton.class);
        bind(BlindVoteStorageService.class).in(Singleton.class);
        bind(BlindVoteValidator.class).in(Singleton.class);
        bind(MyBlindVoteListService.class).in(Singleton.class);

        // VoteReveal
        bind(VoteRevealService.class).in(Singleton.class);

        // VoteResult
        bind(VoteResultService.class).in(Singleton.class);
        bind(MissingDataRequestService.class).in(Singleton.class);
        bind(IssuanceService.class).in(Singleton.class);
        bind(RepublishGovernanceDataHandler.class).in(Singleton.class);

        // Genesis
        String genesisTxId = environment.getProperty(DaoOptionKeys.GENESIS_TX_ID, String.class, "");
        bind(String.class).annotatedWith(Names.named(DaoOptionKeys.GENESIS_TX_ID)).toInstance(genesisTxId);

        Integer genesisBlockHeight = environment.getProperty(DaoOptionKeys.GENESIS_BLOCK_HEIGHT, Integer.class, -1);
        bind(Integer.class).annotatedWith(Names.named(DaoOptionKeys.GENESIS_BLOCK_HEIGHT)).toInstance(genesisBlockHeight);

        // Bonds
        bind(LockupService.class).in(Singleton.class);
        bind(UnlockService.class).in(Singleton.class);
        bind(BondedRolesService.class).in(Singleton.class);

        // Asset
        bind(AssetService.class).in(Singleton.class);

        // Options
        bindConstant().annotatedWith(named(DaoOptionKeys.RPC_USER)).to(environment.getRequiredProperty(DaoOptionKeys.RPC_USER));
        bindConstant().annotatedWith(named(DaoOptionKeys.RPC_PASSWORD)).to(environment.getRequiredProperty(DaoOptionKeys.RPC_PASSWORD));
        bindConstant().annotatedWith(named(DaoOptionKeys.RPC_PORT)).to(environment.getRequiredProperty(DaoOptionKeys.RPC_PORT));
        bindConstant().annotatedWith(named(DaoOptionKeys.RPC_BLOCK_NOTIFICATION_PORT))
                .to(environment.getRequiredProperty(DaoOptionKeys.RPC_BLOCK_NOTIFICATION_PORT));
        bindConstant().annotatedWith(named(DaoOptionKeys.DUMP_BLOCKCHAIN_DATA))
                .to(environment.getRequiredProperty(DaoOptionKeys.DUMP_BLOCKCHAIN_DATA));
        bindConstant().annotatedWith(named(DaoOptionKeys.FULL_DAO_NODE))
                .to(environment.getRequiredProperty(DaoOptionKeys.FULL_DAO_NODE));
        Boolean daoActivated = environment.getProperty(DaoOptionKeys.DAO_ACTIVATED, Boolean.class, false);
        bind(Boolean.class).annotatedWith(Names.named(DaoOptionKeys.DAO_ACTIVATED)).toInstance(daoActivated);
    }
}

