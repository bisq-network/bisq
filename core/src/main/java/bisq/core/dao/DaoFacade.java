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
import bisq.core.dao.governance.proposal.removeAsset.RemoveAssetProposalService;
import bisq.core.dao.governance.proposal.role.BondedRoleProposalService;
import bisq.core.dao.governance.role.BondedRole;
import bisq.core.dao.governance.role.BondedRolesService;
import bisq.core.dao.state.BsqStateListener;
import bisq.core.dao.state.BsqStateService;
import bisq.core.dao.state.blockchain.Block;
import bisq.core.dao.state.blockchain.Tx;
import bisq.core.dao.state.blockchain.TxOutput;
import bisq.core.dao.state.blockchain.TxOutputKey;
import bisq.core.dao.state.blockchain.TxType;
import bisq.core.dao.state.governance.Param;
import bisq.core.dao.state.period.DaoPhase;
import bisq.core.dao.state.period.PeriodService;

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

import javax.annotation.Nullable;



import bisq.asset.Asset;

/**
 * Provides a facade to interact with the Dao domain. Hides complexity and domain details to clients (e.g. UI or APIs)
 * by providing a reduced API and/or aggregating subroutines.
 */
public class DaoFacade implements DaoSetupService {
    private final ProposalListPresentation proposalListPresentation;
    private final BallotListService ballotListService;
    private final BallotListPresentation ballotListPresentation;
    private final MyProposalListService myProposalListService;
    private final BsqStateService bsqStateService;
    private final PeriodService periodService;
    private final MyBlindVoteListService myBlindVoteListService;
    private final MyVoteListService myVoteListService;
    private final CompensationProposalService compensationProposalService;
    private final ChangeParamProposalService changeParamProposalService;
    private final ConfiscateBondProposalService confiscateBondProposalService;
    private final BondedRoleProposalService bondedRoleProposalService;
    private final GenericProposalService genericProposalService;
    private final RemoveAssetProposalService removeAssetProposalService;
    private final BondedRolesService bondedRolesService;
    private final LockupService lockupService;
    private final UnlockService unlockService;
    private final ProposalConsensus proposalConsensus;

    private final ObjectProperty<DaoPhase.Phase> phaseProperty = new SimpleObjectProperty<>(DaoPhase.Phase.UNDEFINED);

    @Inject
    public DaoFacade(MyProposalListService myProposalListService,
                     ProposalListPresentation proposalListPresentation,
                     BallotListService ballotListService,
                     BallotListPresentation ballotListPresentation,
                     BsqStateService bsqStateService,
                     PeriodService periodService,
                     MyBlindVoteListService myBlindVoteListService,
                     MyVoteListService myVoteListService,
                     CompensationProposalService compensationProposalService,
                     ChangeParamProposalService changeParamProposalService,
                     ConfiscateBondProposalService confiscateBondProposalService,
                     BondedRoleProposalService bondedRoleProposalService,
                     GenericProposalService genericProposalService,
                     RemoveAssetProposalService removeAssetProposalService,
                     BondedRolesService bondedRolesService,
                     LockupService lockupService,
                     UnlockService unlockService,
                     ProposalConsensus proposalConsensus) {
        this.proposalListPresentation = proposalListPresentation;
        this.ballotListService = ballotListService;
        this.ballotListPresentation = ballotListPresentation;
        this.myProposalListService = myProposalListService;
        this.bsqStateService = bsqStateService;
        this.periodService = periodService;
        this.myBlindVoteListService = myBlindVoteListService;
        this.myVoteListService = myVoteListService;
        this.compensationProposalService = compensationProposalService;
        this.changeParamProposalService = changeParamProposalService;
        this.confiscateBondProposalService = confiscateBondProposalService;
        this.bondedRoleProposalService = bondedRoleProposalService;
        this.genericProposalService = genericProposalService;
        this.removeAssetProposalService = removeAssetProposalService;
        this.bondedRolesService = bondedRolesService;
        this.lockupService = lockupService;
        this.unlockService = unlockService;
        this.proposalConsensus = proposalConsensus;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // DaoSetupService
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void addListeners() {
    }

    @Override
    public void start() {
        bsqStateService.addBsqStateListener(new BsqStateListener() {
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


    public void addBsqStateListener(BsqStateListener listener) {
        bsqStateService.addBsqStateListener(listener);
    }

    public void removeBsqStateListener(BsqStateListener listener) {
        bsqStateService.removeBsqStateListener(listener);
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
                                                                          Coin requestedBsq,
                                                                          String bsqAddress)
            throws ValidationException, InsufficientMoneyException, TxException {
        return compensationProposalService.createProposalWithTransaction(name,
                link,
                requestedBsq,
                bsqAddress);
    }

    public ProposalWithTransaction getParamProposalWithTransaction(String name,
                                                                   String link,
                                                                   Param param,
                                                                   long paramValue)
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

    public ProposalWithTransaction getBondedRoleProposalWithTransaction(BondedRole bondedRole)
            throws ValidationException, InsufficientMoneyException, TxException {
        return bondedRoleProposalService.createProposalWithTransaction(bondedRole);
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

    public List<BondedRole> getBondedRoleList() {
        return bondedRolesService.getBondedRoleList();
    }

    // Show fee
    public Coin getProposalFee(int chainHeight) {
        return proposalConsensus.getFee(bsqStateService, chainHeight);
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
        return BlindVoteConsensus.getFee(bsqStateService, bsqStateService.getChainHeight());
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

    public int getFirstBlockOfPhase(int height, DaoPhase.Phase phase) {
        return periodService.getFirstBlockOfPhase(height, phase);
    }

    public int getLastBlockOfPhase(int height, DaoPhase.Phase phase) {
        return periodService.getLastBlockOfPhase(height, phase);
    }

    public int getDurationForPhase(DaoPhase.Phase phase) {
        return periodService.getDurationForPhase(phase, bsqStateService.getChainHeight());
    }

    // listeners for phase change
    public ReadOnlyObjectProperty<DaoPhase.Phase> phaseProperty() {
        return phaseProperty;
    }

    public int getChainHeight() {
        return bsqStateService.getChainHeight();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Use case: Bonding
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void publishLockupTx(Coin lockupAmount, int lockTime, LockupType lockupType, BondedRole bondedRole,
                                ResultHandler resultHandler, ExceptionHandler exceptionHandler) {
        lockupService.publishLockupTx(lockupAmount, lockTime, lockupType, bondedRole, resultHandler, exceptionHandler);
    }

    public void publishUnlockTx(String lockupTxId, ResultHandler resultHandler,
                                ExceptionHandler exceptionHandler) {
        unlockService.publishUnlockTx(lockupTxId, resultHandler, exceptionHandler);
    }

    public long getTotalLockupAmount() {
        return bsqStateService.getTotalLockupAmount();
    }

    public long getTotalAmountOfUnLockingTxOutputs() {
        return bsqStateService.getTotalAmountOfUnLockingTxOutputs();
    }

    public long getTotalAmountOfUnLockedTxOutputs() {
        return bsqStateService.getTotalAmountOfUnLockedTxOutputs();
    }

    public Optional<Integer> getLockTime(String txId) {
        return bsqStateService.getLockTime(txId);
    }

    public List<BondedRole> getValidBondedRoleList() {
        return bondedRolesService.getValidBondedRoleList();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Use case: Present transaction related state
    ///////////////////////////////////////////////////////////////////////////////////////////

    public Optional<Tx> getTx(String txId) {
        return bsqStateService.getTx(txId);
    }

    public Set<TxOutput> getUnspentBlindVoteStakeTxOutputs() {
        return bsqStateService.getUnspentBlindVoteStakeTxOutputs();
    }

    public int getGenesisBlockHeight() {
        return bsqStateService.getGenesisBlockHeight();
    }

    public String getGenesisTxId() {
        return bsqStateService.getGenesisTxId();
    }

    public Coin getGenesisTotalSupply() {
        return bsqStateService.getGenesisTotalSupply();
    }

    public Set<Tx> getFeeTxs() {
        return bsqStateService.getBurntFeeTxs();
    }

    public Set<TxOutput> getUnspentTxOutputs() {
        return bsqStateService.getUnspentTxOutputs();
    }

    public Set<Tx> getTxs() {
        return bsqStateService.getTxs();
    }

    public Optional<TxOutput> getLockupTxOutput(String txId) {
        return bsqStateService.getLockupTxOutput(txId);
    }

    public long getTotalBurntFee() {
        return bsqStateService.getTotalBurntFee();
    }

    public long getTotalIssuedAmountFromCompRequests() {
        return bsqStateService.getTotalIssuedAmount();
    }

    public long getBlockTime(int issuanceBlockHeight) {
        return bsqStateService.getBlockTime(issuanceBlockHeight);
    }

    public int getIssuanceBlockHeight(String txId) {
        return bsqStateService.getIssuanceBlockHeight(txId);
    }

    public boolean isIssuanceTx(String txId) {
        return bsqStateService.isIssuanceTx(txId);
    }

    public boolean hasTxBurntFee(String hashAsString) {
        return bsqStateService.hasTxBurntFee(hashAsString);
    }

    public Optional<TxType> getOptionalTxType(String txId) {
        return bsqStateService.getOptionalTxType(txId);
    }

    public TxType getTxType(String txId) {
        return bsqStateService.getTx(txId).map(Tx::getTxType).orElse(TxType.UNDEFINED_TX_TYPE);
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
        return bsqStateService.isUnspent(key);
    }

    public Optional<BondedRole> getBondedRoleFromHash(byte[] hash) {
        return bondedRolesService.getBondedRoleFromHash(hash);
    }

    public boolean isUnlocking(BondedRole bondedRole) {
        return bsqStateService.isUnlocking(bondedRole);
    }

    public Coin getMinCompensationRequestAmount() {
        return CompensationConsensus.getMinCompensationRequestAmount(bsqStateService, periodService.getChainHeight());
    }

    public Coin getMaxCompensationRequestAmount() {
        return CompensationConsensus.getMaxCompensationRequestAmount(bsqStateService, periodService.getChainHeight());
    }

    public long getPramValue(Param param) {
        return bsqStateService.getParamValue(param, periodService.getChainHeight());
    }
}
