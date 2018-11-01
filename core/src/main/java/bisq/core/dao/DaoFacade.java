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
import bisq.core.dao.bonding.bond.BondWithHash;
import bisq.core.dao.bonding.bond.BondedReputation;
import bisq.core.dao.bonding.bond.BondedReputationService;
import bisq.core.dao.bonding.lockup.LockupService;
import bisq.core.dao.bonding.lockup.LockupType;
import bisq.core.dao.bonding.unlock.UnlockService;
import bisq.core.dao.exceptions.ValidationException;
import bisq.core.dao.governance.ballot.Ballot;
import bisq.core.dao.governance.ballot.BallotListPresentation;
import bisq.core.dao.governance.ballot.BallotListService;
import bisq.core.dao.governance.ballot.vote.Vote;
import bisq.core.dao.governance.blindvote.BlindVoteConsensus;
import bisq.core.dao.governance.blindvote.MyBlindVoteListService;
import bisq.core.dao.governance.myvote.MyVote;
import bisq.core.dao.governance.myvote.MyVoteListService;
import bisq.core.dao.governance.proposal.MyProposalListService;
import bisq.core.dao.governance.proposal.Proposal;
import bisq.core.dao.governance.proposal.ProposalConsensus;
import bisq.core.dao.governance.proposal.ProposalListPresentation;
import bisq.core.dao.governance.proposal.ProposalWithTransaction;
import bisq.core.dao.governance.proposal.TxException;
import bisq.core.dao.governance.proposal.compensation.CompensationConsensus;
import bisq.core.dao.governance.proposal.compensation.CompensationProposalService;
import bisq.core.dao.governance.proposal.confiscatebond.ConfiscateBondProposalService;
import bisq.core.dao.governance.proposal.generic.GenericProposalService;
import bisq.core.dao.governance.proposal.param.ChangeParamProposalService;
import bisq.core.dao.governance.proposal.reimbursement.ReimbursementConsensus;
import bisq.core.dao.governance.proposal.reimbursement.ReimbursementProposalService;
import bisq.core.dao.governance.proposal.removeAsset.RemoveAssetProposalService;
import bisq.core.dao.governance.proposal.role.BondedRoleProposalService;
import bisq.core.dao.governance.role.BondedRole;
import bisq.core.dao.governance.role.BondedRolesService;
import bisq.core.dao.governance.role.Role;
import bisq.core.dao.state.DaoStateListener;
import bisq.core.dao.state.DaoStateService;
import bisq.core.dao.state.DaoStateStorageService;
import bisq.core.dao.state.blockchain.Block;
import bisq.core.dao.state.blockchain.Tx;
import bisq.core.dao.state.blockchain.TxOutput;
import bisq.core.dao.state.blockchain.TxOutputKey;
import bisq.core.dao.state.blockchain.TxType;
import bisq.core.dao.state.governance.IssuanceType;
import bisq.core.dao.state.governance.Param;
import bisq.core.dao.state.period.DaoPhase;
import bisq.core.dao.state.period.PeriodService;

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

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;

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
    private final CompensationProposalService compensationProposalService;
    private final ReimbursementProposalService reimbursementProposalService;
    private final ChangeParamProposalService changeParamProposalService;
    private final ConfiscateBondProposalService confiscateBondProposalService;
    private final BondedRoleProposalService bondedRoleProposalService;
    private final GenericProposalService genericProposalService;
    private final RemoveAssetProposalService removeAssetProposalService;
    private final BondedRolesService bondedRolesService;
    private final BondedReputationService bondedReputationService;
    private final LockupService lockupService;
    private final UnlockService unlockService;
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
                     CompensationProposalService compensationProposalService,
                     ReimbursementProposalService reimbursementProposalService,
                     ChangeParamProposalService changeParamProposalService,
                     ConfiscateBondProposalService confiscateBondProposalService,
                     BondedRoleProposalService bondedRoleProposalService,
                     GenericProposalService genericProposalService,
                     RemoveAssetProposalService removeAssetProposalService,
                     BondedRolesService bondedRolesService,
                     BondedReputationService bondedReputationService,
                     LockupService lockupService,
                     UnlockService unlockService,
                     DaoStateStorageService daoStateStorageService) {
        this.proposalListPresentation = proposalListPresentation;
        this.ballotListService = ballotListService;
        this.ballotListPresentation = ballotListPresentation;
        this.myProposalListService = myProposalListService;
        this.daoStateService = daoStateService;
        this.periodService = periodService;
        this.myBlindVoteListService = myBlindVoteListService;
        this.myVoteListService = myVoteListService;
        this.compensationProposalService = compensationProposalService;
        this.reimbursementProposalService = reimbursementProposalService;
        this.changeParamProposalService = changeParamProposalService;
        this.confiscateBondProposalService = confiscateBondProposalService;
        this.bondedRoleProposalService = bondedRoleProposalService;
        this.genericProposalService = genericProposalService;
        this.removeAssetProposalService = removeAssetProposalService;
        this.bondedRolesService = bondedRolesService;
        this.bondedReputationService = bondedReputationService;
        this.lockupService = lockupService;
        this.unlockService = unlockService;
        this.daoStateStorageService = daoStateStorageService;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // DaoSetupService
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void addListeners() {
    }

    @Override
    public void start() {
        daoStateService.addBsqStateListener(new DaoStateListener() {
            @Override
            public void onNewBlockHeight(int blockHeight) {
                if (blockHeight > 0 && periodService.getCurrentCycle() != null)
                    periodService.getCurrentCycle().getPhaseForHeight(blockHeight).ifPresent(phaseProperty::set);
            }

            @Override
            public void onParseTxsComplete(Block block) {
            }

            @Override
            public void onParseBlockChainComplete() {
            }
        });
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
        return compensationProposalService.createProposalWithTransaction(name,
                link,
                requestedBsq);
    }

    public ProposalWithTransaction getReimbursementProposalWithTransaction(String name,
                                                                           String link,
                                                                           Coin requestedBsq)
            throws ValidationException, InsufficientMoneyException, TxException {
        return reimbursementProposalService.createProposalWithTransaction(name,
                link,
                requestedBsq);
    }

    public ProposalWithTransaction getParamProposalWithTransaction(String name,
                                                                   String link,
                                                                   Param param,
                                                                   String paramValue)
            throws ValidationException, InsufficientMoneyException, TxException {
        return changeParamProposalService.createProposalWithTransaction(name,
                link,
                param,
                paramValue);
    }

    public ProposalWithTransaction getConfiscateBondProposalWithTransaction(String name,
                                                                            String link,
                                                                            byte[] hash)
            throws ValidationException, InsufficientMoneyException, TxException {
        return confiscateBondProposalService.createProposalWithTransaction(name,
                link,
                hash);
    }

    public ProposalWithTransaction getBondedRoleProposalWithTransaction(Role role)
            throws ValidationException, InsufficientMoneyException, TxException {
        return bondedRoleProposalService.createProposalWithTransaction(role);
    }

    public ProposalWithTransaction getGenericProposalWithTransaction(String name,
                                                                     String link)
            throws ValidationException, InsufficientMoneyException, TxException {
        return genericProposalService.createProposalWithTransaction(name, link);
    }

    public ProposalWithTransaction getRemoveAssetProposalWithTransaction(String name,
                                                                         String link,
                                                                         Asset asset)
            throws ValidationException, InsufficientMoneyException, TxException {
        return removeAssetProposalService.createProposalWithTransaction(name, link, asset);
    }

    public Collection<BondedRole> getBondedRoleStates() {
        return bondedRolesService.getBondedRoleStates();
    }

    public List<BondedReputation> getBondedReputationList() {
        return bondedReputationService.getBondedReputationList();
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

    public ObservableList<Ballot> getBallots() {
        return ballotListPresentation.getBallots();
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
                firstBlock++;
                break;
            case BLIND_VOTE:
                break;
            case BREAK2:
                firstBlock++;
                break;
            case VOTE_REVEAL:
                break;
            case BREAK3:
                firstBlock++;
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

    // Because last block in request and voting phases must not be used fo making a tx as it will get confirmed in the
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

    // listeners for phase change
    public ReadOnlyObjectProperty<DaoPhase.Phase> phaseProperty() {
        return phaseProperty;
    }

    public int getChainHeight() {
        return daoStateService.getChainHeight();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Use case: Bonding
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void publishLockupTx(Coin lockupAmount, int lockTime, LockupType lockupType, BondWithHash bondWithHash,
                                ResultHandler resultHandler, ExceptionHandler exceptionHandler) {
        lockupService.publishLockupTx(lockupAmount, lockTime, lockupType, bondWithHash, resultHandler, exceptionHandler);
    }

    public void publishUnlockTx(String lockupTxId, ResultHandler resultHandler,
                                ExceptionHandler exceptionHandler) {
        unlockService.publishUnlockTx(lockupTxId, resultHandler, exceptionHandler);
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

    public Optional<Integer> getLockTime(String txId) {
        return daoStateService.getLockTime(txId);
    }

    public List<Role> getActiveBondedRoles() {
        return bondedRolesService.getActiveBondedRoles();
    }

    /*public List<BondedReputation> getValidBondedReputationList() {
        return bondedReputationService.getValidBondedReputationList();
    }*/


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

    public Optional<Role> getBondedRoleFromHash(byte[] hash) {
        return bondedRolesService.getBondedRoleFromHash(hash);
    }

    public Optional<BondedReputation> getBondedReputationFromHash(byte[] hash) {
        return bondedReputationService.getBondedReputationFromHash(hash);
    }

    /*public boolean isUnlocking(String unlockTxId) {
        return daoStateService.isUnlocking(unlockTxId);
    }*/

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
}
