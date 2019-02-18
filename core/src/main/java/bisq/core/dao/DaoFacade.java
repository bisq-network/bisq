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

import bisq.core.btc.exceptions.TransactionVerificationException;
import bisq.core.btc.exceptions.WalletException;
import bisq.core.dao.exceptions.ValidationException;
import bisq.core.dao.governance.ballot.BallotListPresentation;
import bisq.core.dao.governance.ballot.BallotListService;
import bisq.core.dao.governance.blindvote.BlindVoteConsensus;
import bisq.core.dao.governance.blindvote.MyBlindVoteListService;
import bisq.core.dao.governance.bond.Bond;
import bisq.core.dao.governance.bond.lockup.LockupReason;
import bisq.core.dao.governance.bond.lockup.LockupTxService;
import bisq.core.dao.governance.bond.reputation.BondedReputationRepository;
import bisq.core.dao.governance.bond.reputation.MyBondedReputation;
import bisq.core.dao.governance.bond.reputation.MyBondedReputationRepository;
import bisq.core.dao.governance.bond.reputation.MyReputationListService;
import bisq.core.dao.governance.bond.role.BondedRole;
import bisq.core.dao.governance.bond.role.BondedRolesRepository;
import bisq.core.dao.governance.bond.unlock.UnlockTxService;
import bisq.core.dao.governance.myvote.MyVote;
import bisq.core.dao.governance.myvote.MyVoteListService;
import bisq.core.dao.governance.param.Param;
import bisq.core.dao.governance.period.PeriodService;
import bisq.core.dao.governance.proposal.MyProposalListService;
import bisq.core.dao.governance.proposal.ProposalConsensus;
import bisq.core.dao.governance.proposal.ProposalListPresentation;
import bisq.core.dao.governance.proposal.ProposalWithTransaction;
import bisq.core.dao.governance.proposal.TxException;
import bisq.core.dao.governance.proposal.compensation.CompensationConsensus;
import bisq.core.dao.governance.proposal.compensation.CompensationProposalFactory;
import bisq.core.dao.governance.proposal.confiscatebond.ConfiscateBondProposalFactory;
import bisq.core.dao.governance.proposal.generic.GenericProposalFactory;
import bisq.core.dao.governance.proposal.param.ChangeParamProposalFactory;
import bisq.core.dao.governance.proposal.reimbursement.ReimbursementConsensus;
import bisq.core.dao.governance.proposal.reimbursement.ReimbursementProposalFactory;
import bisq.core.dao.governance.proposal.removeAsset.RemoveAssetProposalFactory;
import bisq.core.dao.governance.proposal.role.RoleProposalFactory;
import bisq.core.dao.state.DaoStateListener;
import bisq.core.dao.state.DaoStateService;
import bisq.core.dao.state.DaoStateStorageService;
import bisq.core.dao.state.model.blockchain.Block;
import bisq.core.dao.state.model.blockchain.Tx;
import bisq.core.dao.state.model.blockchain.TxOutput;
import bisq.core.dao.state.model.blockchain.TxOutputKey;
import bisq.core.dao.state.model.blockchain.TxType;
import bisq.core.dao.state.model.governance.Ballot;
import bisq.core.dao.state.model.governance.DaoPhase;
import bisq.core.dao.state.model.governance.IssuanceType;
import bisq.core.dao.state.model.governance.Proposal;
import bisq.core.dao.state.model.governance.Role;
import bisq.core.dao.state.model.governance.Vote;

import bisq.asset.Asset;

import bisq.common.handlers.ErrorMessageHandler;
import bisq.common.handlers.ExceptionHandler;
import bisq.common.handlers.ResultHandler;
import bisq.common.util.Tuple2;

import org.bitcoinj.core.Coin;
import org.bitcoinj.core.InsufficientMoneyException;
import org.bitcoinj.core.Transaction;

import javax.inject.Inject;

import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.SimpleObjectProperty;

import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;

import lombok.extern.slf4j.Slf4j;

import javax.annotation.Nullable;

/**
 * Provides a facade to interact with the Dao domain. Hides complexity and domain details to clients (e.g. UI or APIs)
 * by providing a reduced API and/or aggregating subroutines.
 */
@Slf4j
public class DaoFacade implements DaoSetupService {
    private final ProposalListPresentation proposalListPresentation;
    private final BallotListService ballotListService;
    private final BallotListPresentation ballotListPresentation;
    private final MyProposalListService myProposalListService;
    private final DaoStateService daoStateService;
    private final PeriodService periodService;
    private final MyBlindVoteListService myBlindVoteListService;
    private final MyVoteListService myVoteListService;
    private final CompensationProposalFactory compensationProposalFactory;
    private final ReimbursementProposalFactory reimbursementProposalFactory;
    private final ChangeParamProposalFactory changeParamProposalFactory;
    private final ConfiscateBondProposalFactory confiscateBondProposalFactory;
    private final RoleProposalFactory roleProposalFactory;
    private final GenericProposalFactory genericProposalFactory;
    private final RemoveAssetProposalFactory removeAssetProposalFactory;
    private final BondedRolesRepository bondedRolesRepository;
    private final BondedReputationRepository bondedReputationRepository;
    private final MyReputationListService myReputationListService;
    private final MyBondedReputationRepository myBondedReputationRepository;
    private final LockupTxService lockupTxService;
    private final UnlockTxService unlockTxService;
    private final DaoStateStorageService daoStateStorageService;

    private final ObjectProperty<DaoPhase.Phase> phaseProperty = new SimpleObjectProperty<>(DaoPhase.Phase.UNDEFINED);

    @Inject
    public DaoFacade(MyProposalListService myProposalListService,
                     ProposalListPresentation proposalListPresentation,
                     BallotListService ballotListService,
                     BallotListPresentation ballotListPresentation,
                     DaoStateService daoStateService,
                     PeriodService periodService,
                     MyBlindVoteListService myBlindVoteListService,
                     MyVoteListService myVoteListService,
                     CompensationProposalFactory compensationProposalFactory,
                     ReimbursementProposalFactory reimbursementProposalFactory,
                     ChangeParamProposalFactory changeParamProposalFactory,
                     ConfiscateBondProposalFactory confiscateBondProposalFactory,
                     RoleProposalFactory roleProposalFactory,
                     GenericProposalFactory genericProposalFactory,
                     RemoveAssetProposalFactory removeAssetProposalFactory,
                     BondedRolesRepository bondedRolesRepository,
                     BondedReputationRepository bondedReputationRepository,
                     MyReputationListService myReputationListService,
                     MyBondedReputationRepository myBondedReputationRepository,
                     LockupTxService lockupTxService,
                     UnlockTxService unlockTxService,
                     DaoStateStorageService daoStateStorageService) {
        this.proposalListPresentation = proposalListPresentation;
        this.ballotListService = ballotListService;
        this.ballotListPresentation = ballotListPresentation;
        this.myProposalListService = myProposalListService;
        this.daoStateService = daoStateService;
        this.periodService = periodService;
        this.myBlindVoteListService = myBlindVoteListService;
        this.myVoteListService = myVoteListService;
        this.compensationProposalFactory = compensationProposalFactory;
        this.reimbursementProposalFactory = reimbursementProposalFactory;
        this.changeParamProposalFactory = changeParamProposalFactory;
        this.confiscateBondProposalFactory = confiscateBondProposalFactory;
        this.roleProposalFactory = roleProposalFactory;
        this.genericProposalFactory = genericProposalFactory;
        this.removeAssetProposalFactory = removeAssetProposalFactory;
        this.bondedRolesRepository = bondedRolesRepository;
        this.bondedReputationRepository = bondedReputationRepository;
        this.myReputationListService = myReputationListService;
        this.myBondedReputationRepository = myBondedReputationRepository;
        this.lockupTxService = lockupTxService;
        this.unlockTxService = unlockTxService;
        this.daoStateStorageService = daoStateStorageService;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // DaoSetupService
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void addListeners() {
        daoStateService.addBsqStateListener(new DaoStateListener() {
            @Override
            public void onNewBlockHeight(int blockHeight) {
                if (blockHeight > 0 && periodService.getCurrentCycle() != null)
                    periodService.getCurrentCycle().getPhaseForHeight(blockHeight).ifPresent(phaseProperty::set);
            }
        });
    }

    @Override
    public void start() {
    }


    public void addBsqStateListener(DaoStateListener listener) {
        daoStateService.addBsqStateListener(listener);
    }

    public void removeBsqStateListener(DaoStateListener listener) {
        daoStateService.removeBsqStateListener(listener);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    //
    // Phase: Proposal
    //
    ///////////////////////////////////////////////////////////////////////////////////////////


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Use case: Present lists
    ///////////////////////////////////////////////////////////////////////////////////////////

    public ObservableList<Proposal> getActiveOrMyUnconfirmedProposals() {
        return proposalListPresentation.getActiveOrMyUnconfirmedProposals();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Use case: Create proposal
    ///////////////////////////////////////////////////////////////////////////////////////////

    // Creation of Proposal and proposalTransaction
    public ProposalWithTransaction getCompensationProposalWithTransaction(String name,
                                                                          String link,
                                                                          Coin requestedBsq)
            throws ValidationException, InsufficientMoneyException, TxException {
        return compensationProposalFactory.createProposalWithTransaction(name,
                link,
                requestedBsq);
    }

    public ProposalWithTransaction getReimbursementProposalWithTransaction(String name,
                                                                           String link,
                                                                           Coin requestedBsq)
            throws ValidationException, InsufficientMoneyException, TxException {
        return reimbursementProposalFactory.createProposalWithTransaction(name,
                link,
                requestedBsq);
    }

    public ProposalWithTransaction getParamProposalWithTransaction(String name,
                                                                   String link,
                                                                   Param param,
                                                                   String paramValue)
            throws ValidationException, InsufficientMoneyException, TxException {
        return changeParamProposalFactory.createProposalWithTransaction(name,
                link,
                param,
                paramValue);
    }

    public ProposalWithTransaction getConfiscateBondProposalWithTransaction(String name,
                                                                            String link,
                                                                            String lockupTxId)
            throws ValidationException, InsufficientMoneyException, TxException {
        return confiscateBondProposalFactory.createProposalWithTransaction(name,
                link,
                lockupTxId);
    }

    public ProposalWithTransaction getBondedRoleProposalWithTransaction(Role role)
            throws ValidationException, InsufficientMoneyException, TxException {
        return roleProposalFactory.createProposalWithTransaction(role);
    }

    public ProposalWithTransaction getGenericProposalWithTransaction(String name,
                                                                     String link)
            throws ValidationException, InsufficientMoneyException, TxException {
        return genericProposalFactory.createProposalWithTransaction(name, link);
    }

    public ProposalWithTransaction getRemoveAssetProposalWithTransaction(String name,
                                                                         String link,
                                                                         Asset asset)
            throws ValidationException, InsufficientMoneyException, TxException {
        return removeAssetProposalFactory.createProposalWithTransaction(name, link, asset);
    }

    public ObservableList<BondedRole> getBondedRoles() {
        return bondedRolesRepository.getBonds();
    }

    // Show fee
    public Coin getProposalFee(int chainHeight) {
        return ProposalConsensus.getFee(daoStateService, chainHeight);
    }

    // Publish proposal tx, proposal payload and and persist it to myProposalList
    public void publishMyProposal(Proposal proposal, Transaction transaction, ResultHandler resultHandler,
                                  ErrorMessageHandler errorMessageHandler) {
        myProposalListService.publishTxAndPayload(proposal, transaction, resultHandler, errorMessageHandler);
    }

    // Check if it is my proposal
    public boolean isMyProposal(Proposal proposal) {
        return myProposalListService.isMine(proposal);
    }

    // Remove my proposal
    public boolean removeMyProposal(Proposal proposal) {
        return myProposalListService.remove(proposal);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    //
    // Phase: Blind Vote
    //
    ///////////////////////////////////////////////////////////////////////////////////////////


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Use case: Present lists
    ///////////////////////////////////////////////////////////////////////////////////////////

    public ObservableList<Ballot> getAllBallots() {
        return ballotListPresentation.getAllBallots();
    }

    public List<Ballot> getAllValidBallots() {
        return ballotListPresentation.getAllValidBallots();
    }

    public FilteredList<Ballot> getBallotsOfCycle() {
        return ballotListPresentation.getBallotsOfCycle();
    }

    public Tuple2<Long, Long> getMeritAndStakeForProposal(String proposalTxId) {
        return myVoteListService.getMeritAndStakeForProposal(proposalTxId, myBlindVoteListService);
    }

    public long getAvailableMerit() {
        return myBlindVoteListService.getCurrentlyAvailableMerit();
    }

    public List<MyVote> getMyVoteListForCycle() {
        return myVoteListService.getMyVoteListForCycle();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Use case: Vote
    ///////////////////////////////////////////////////////////////////////////////////////////

    // Vote on ballot
    public void setVote(Ballot ballot, @Nullable Vote vote) {
        ballotListService.setVote(ballot, vote);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Use case: Create blindVote
    ///////////////////////////////////////////////////////////////////////////////////////////

    // When creating blind vote we present fee
    public Coin getBlindVoteFeeForCycle() {
        return BlindVoteConsensus.getFee(daoStateService, daoStateService.getChainHeight());
    }

    public Tuple2<Coin, Integer> getMiningFeeAndTxSize(Coin stake)
            throws WalletException, InsufficientMoneyException, TransactionVerificationException {
        return myBlindVoteListService.getMiningFeeAndTxSize(stake);
    }

    // Publish blindVote tx and broadcast blindVote to p2p network and store to blindVoteList.
    public void publishBlindVote(Coin stake, ResultHandler resultHandler, ExceptionHandler exceptionHandler) {
        myBlindVoteListService.publishBlindVote(stake, resultHandler, exceptionHandler);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    //
    // Generic
    //
    ///////////////////////////////////////////////////////////////////////////////////////////


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Use case: Presentation of phases
    ///////////////////////////////////////////////////////////////////////////////////////////

    // Because last block in request and voting phases must not be used fo making a tx as it will get confirmed in the
    // next block which would be already the next phase we hide that last block to the user and add it to the break.
    public int getFirstBlockOfPhaseForDisplay(int height, DaoPhase.Phase phase) {
        int firstBlock = periodService.getFirstBlockOfPhase(height, phase);
        switch (phase) {
            case UNDEFINED:
                break;
            case PROPOSAL:
                break;
            case BREAK1:
                firstBlock--;
                break;
            case BLIND_VOTE:
                break;
            case BREAK2:
                firstBlock--;
                break;
            case VOTE_REVEAL:
                break;
            case BREAK3:
                firstBlock--;
                break;
            case RESULT:
                break;
        }

        return firstBlock;
    }

    // Because last block in request and voting phases must not be used fo making a tx as it will get confirmed in the
    // next block which would be already the next phase we hide that last block to the user and add it to the break.
    public int getLastBlockOfPhaseForDisplay(int height, DaoPhase.Phase phase) {
        int lastBlock = periodService.getLastBlockOfPhase(height, phase);
        switch (phase) {
            case UNDEFINED:
                break;
            case PROPOSAL:
                lastBlock--;
                break;
            case BREAK1:
                break;
            case BLIND_VOTE:
                lastBlock--;
                break;
            case BREAK2:
                break;
            case VOTE_REVEAL:
                lastBlock--;
                break;
            case BREAK3:
                break;
            case RESULT:
                break;
        }
        return lastBlock;
    }

    // Because last block in request and voting phases must not be used for making a tx as it will get confirmed in the
    // next block which would be already the next phase we hide that last block to the user and add it to the break.
    public int getDurationForPhaseForDisplay(DaoPhase.Phase phase) {
        int duration = periodService.getDurationForPhase(phase, daoStateService.getChainHeight());
        switch (phase) {
            case UNDEFINED:
                break;
            case PROPOSAL:
                duration--;
                break;
            case BREAK1:
                duration++;
                break;
            case BLIND_VOTE:
                duration--;
                break;
            case BREAK2:
                duration++;
                break;
            case VOTE_REVEAL:
                duration--;
                break;
            case BREAK3:
                duration++;
                break;
            case RESULT:
                break;
        }
        return duration;
    }

    public int getCurrentCycleDuration() {
        return periodService.getCurrentCycle().getDuration();
    }

    // listeners for phase change
    public ReadOnlyObjectProperty<DaoPhase.Phase> phaseProperty() {
        return phaseProperty;
    }

    public int getChainHeight() {
        return daoStateService.getChainHeight();
    }

    public Optional<Block> getBlockAtChainHeight() {
        return getBlockAtHeight(getChainHeight());
    }

    public Optional<Block> getBlockAtHeight(int chainHeight) {
        return daoStateService.getBlockAtHeight(chainHeight);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Use case: Bonding
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void publishLockupTx(Coin lockupAmount, int lockTime, LockupReason lockupReason, byte[] hash,
                                Consumer<String> resultHandler, ExceptionHandler exceptionHandler) {
        lockupTxService.publishLockupTx(lockupAmount, lockTime, lockupReason, hash, resultHandler, exceptionHandler);
    }

    public void publishUnlockTx(String lockupTxId, Consumer<String> resultHandler,
                                ExceptionHandler exceptionHandler) {
        unlockTxService.publishUnlockTx(lockupTxId, resultHandler, exceptionHandler);
    }

    public long getTotalLockupAmount() {
        return daoStateService.getTotalLockupAmount();
    }

    public long getTotalAmountOfUnLockingTxOutputs() {
        return daoStateService.getTotalAmountOfUnLockingTxOutputs();
    }

    public long getTotalAmountOfUnLockedTxOutputs() {
        return daoStateService.getTotalAmountOfUnLockedTxOutputs();
    }

    public long getTotalAmountOfConfiscatedTxOutputs() {
        return daoStateService.getTotalAmountOfConfiscatedTxOutputs();
    }

    public Optional<Integer> getLockTime(String txId) {
        return daoStateService.getLockTime(txId);
    }


    public List<Bond> getAllBonds() {
        List<Bond> bonds = bondedReputationRepository.getActiveBonds();
        bonds.addAll(bondedRolesRepository.getActiveBonds());
        return bonds;
    }

    public ObservableList<MyBondedReputation> getMyBondedReputations() {
        return myBondedReputationRepository.getMyBondedReputations();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Use case: Present transaction related state
    ///////////////////////////////////////////////////////////////////////////////////////////

    public Optional<Tx> getTx(String txId) {
        return daoStateService.getTx(txId);
    }

    public Set<TxOutput> getUnspentBlindVoteStakeTxOutputs() {
        return daoStateService.getUnspentBlindVoteStakeTxOutputs();
    }

    public int getGenesisBlockHeight() {
        return daoStateService.getGenesisBlockHeight();
    }

    public String getGenesisTxId() {
        return daoStateService.getGenesisTxId();
    }

    public Coin getGenesisTotalSupply() {
        return daoStateService.getGenesisTotalSupply();
    }

    public int getNumIssuanceTransactions(IssuanceType issuanceType) {
        return daoStateService.getIssuanceSet(issuanceType).size();
    }

    public Set<Tx> getFeeTxs() {
        return daoStateService.getBurntFeeTxs();
    }

    public Set<TxOutput> getUnspentTxOutputs() {
        return daoStateService.getUnspentTxOutputs();
    }

    public Set<Tx> getTxs() {
        return daoStateService.getTxs();
    }

    public Optional<TxOutput> getLockupTxOutput(String txId) {
        return daoStateService.getLockupTxOutput(txId);
    }

    public Optional<TxOutput> getLockupOpReturnTxOutput(String txId) {
        return daoStateService.getLockupOpReturnTxOutput(txId);
    }

    public long getTotalBurntFee() {
        return daoStateService.getTotalBurntFee();
    }

    public long getTotalIssuedAmount(IssuanceType issuanceType) {
        return daoStateService.getTotalIssuedAmount(issuanceType);
    }

    public long getBlockTime(int issuanceBlockHeight) {
        return daoStateService.getBlockTime(issuanceBlockHeight);
    }

    public int getIssuanceBlockHeight(String txId) {
        return daoStateService.getIssuanceBlockHeight(txId);
    }

    public boolean isIssuanceTx(String txId, IssuanceType issuanceType) {
        return daoStateService.isIssuanceTx(txId, issuanceType);
    }

    public boolean hasTxBurntFee(String hashAsString) {
        return daoStateService.hasTxBurntFee(hashAsString);
    }

    public Optional<TxType> getOptionalTxType(String txId) {
        return daoStateService.getOptionalTxType(txId);
    }

    public TxType getTxType(String txId) {
        return daoStateService.getTx(txId).map(Tx::getTxType).orElse(TxType.UNDEFINED_TX_TYPE);
    }

    public boolean isInPhaseButNotLastBlock(DaoPhase.Phase phase) {
        return periodService.isInPhaseButNotLastBlock(phase);
    }

    public boolean isTxInCorrectCycle(int txHeight, int chainHeight) {
        return periodService.isTxInCorrectCycle(txHeight, chainHeight);
    }

    public boolean isTxInCorrectCycle(String txId, int chainHeight) {
        return periodService.isTxInCorrectCycle(txId, chainHeight);
    }

    public boolean isTxInPhaseAndCycle(String txId, DaoPhase.Phase phase, int chainHeight) {
        return periodService.isTxInPhaseAndCycle(txId, phase, chainHeight);
    }

    public boolean isUnspent(TxOutputKey key) {
        return daoStateService.isUnspent(key);
    }


    public Coin getMinCompensationRequestAmount() {
        return CompensationConsensus.getMinCompensationRequestAmount(daoStateService, periodService.getChainHeight());
    }

    public Coin getMaxCompensationRequestAmount() {
        return CompensationConsensus.getMaxCompensationRequestAmount(daoStateService, periodService.getChainHeight());
    }

    public Coin getMinReimbursementRequestAmount() {
        return ReimbursementConsensus.getMinReimbursementRequestAmount(daoStateService, periodService.getChainHeight());
    }

    public Coin getMaxReimbursementRequestAmount() {
        return ReimbursementConsensus.getMaxReimbursementRequestAmount(daoStateService, periodService.getChainHeight());
    }

    public String getParamValue(Param param) {
        return daoStateService.getParamValue(param, periodService.getChainHeight());
    }

    public void resyncDao(Runnable resultHandler) {
        daoStateStorageService.resetDaoState(resultHandler);
    }

    public boolean isMyRole(Role role) {
        return bondedRolesRepository.isMyRole(role);
    }

    public Optional<Bond> getBondByLockupTxId(String lockupTxId) {
        return getAllBonds().stream().filter(e -> lockupTxId.equals(e.getLockupTxId())).findAny();
    }
}
