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
import bisq.core.dao.governance.bond.role.BondedRole;
import bisq.core.dao.governance.bond.role.BondedRolesRepository;
import bisq.core.dao.governance.bond.unlock.UnlockTxService;
import bisq.core.dao.governance.myvote.MyVote;
import bisq.core.dao.governance.myvote.MyVoteListService;
import bisq.core.dao.governance.param.Param;
import bisq.core.dao.governance.period.CycleService;
import bisq.core.dao.governance.period.PeriodService;
import bisq.core.dao.governance.proposal.MyProposalListService;
import bisq.core.dao.governance.proposal.ProposalConsensus;
import bisq.core.dao.governance.proposal.ProposalListPresentation;
import bisq.core.dao.governance.proposal.ProposalService;
import bisq.core.dao.governance.proposal.ProposalValidationException;
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
import bisq.core.dao.monitoring.DaoStateMonitoringService;
import bisq.core.dao.state.DaoStateListener;
import bisq.core.dao.state.DaoStateService;
import bisq.core.dao.state.model.blockchain.BaseTx;
import bisq.core.dao.state.model.blockchain.BaseTxOutput;
import bisq.core.dao.state.model.blockchain.Block;
import bisq.core.dao.state.model.blockchain.Tx;
import bisq.core.dao.state.model.blockchain.TxOutput;
import bisq.core.dao.state.model.blockchain.TxOutputKey;
import bisq.core.dao.state.model.blockchain.TxType;
import bisq.core.dao.state.model.governance.Ballot;
import bisq.core.dao.state.model.governance.BondedRoleType;
import bisq.core.dao.state.model.governance.Cycle;
import bisq.core.dao.state.model.governance.DaoPhase;
import bisq.core.dao.state.model.governance.IssuanceType;
import bisq.core.dao.state.model.governance.Proposal;
import bisq.core.dao.state.model.governance.Role;
import bisq.core.dao.state.model.governance.RoleProposal;
import bisq.core.dao.state.model.governance.Vote;
import bisq.core.dao.state.storage.DaoStateStorageService;
import bisq.core.trade.DelayedPayoutAddressProvider;

import bisq.asset.Asset;

import bisq.common.config.Config;
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

import java.io.File;
import java.io.IOException;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import lombok.extern.slf4j.Slf4j;

import javax.annotation.Nullable;

import static com.google.common.base.Preconditions.checkArgument;

/**
 * Provides a facade to interact with the Dao domain. Hides complexity and domain details to clients (e.g. UI or APIs)
 * by providing a reduced API and/or aggregating subroutines.
 */
@Slf4j
public class DaoFacade implements DaoSetupService {
    private final ProposalListPresentation proposalListPresentation;
    private final ProposalService proposalService;
    private final BallotListService ballotListService;
    private final BallotListPresentation ballotListPresentation;
    private final MyProposalListService myProposalListService;
    private final DaoStateService daoStateService;
    private final DaoStateMonitoringService daoStateMonitoringService;
    private final PeriodService periodService;
    private final CycleService cycleService;
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
    private final MyBondedReputationRepository myBondedReputationRepository;
    private final LockupTxService lockupTxService;
    private final UnlockTxService unlockTxService;
    private final DaoStateStorageService daoStateStorageService;

    private final ObjectProperty<DaoPhase.Phase> phaseProperty = new SimpleObjectProperty<>(DaoPhase.Phase.UNDEFINED);

    @Inject
    public DaoFacade(MyProposalListService myProposalListService,
                     ProposalListPresentation proposalListPresentation,
                     ProposalService proposalService,
                     BallotListService ballotListService,
                     BallotListPresentation ballotListPresentation,
                     DaoStateService daoStateService,
                     DaoStateMonitoringService daoStateMonitoringService,
                     PeriodService periodService,
                     CycleService cycleService,
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
                     MyBondedReputationRepository myBondedReputationRepository,
                     LockupTxService lockupTxService,
                     UnlockTxService unlockTxService,
                     DaoStateStorageService daoStateStorageService) {
        this.proposalListPresentation = proposalListPresentation;
        this.proposalService = proposalService;
        this.ballotListService = ballotListService;
        this.ballotListPresentation = ballotListPresentation;
        this.myProposalListService = myProposalListService;
        this.daoStateService = daoStateService;
        this.daoStateMonitoringService = daoStateMonitoringService;
        this.periodService = periodService;
        this.cycleService = cycleService;
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
        daoStateService.addDaoStateListener(new DaoStateListener() {
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
        daoStateService.addDaoStateListener(listener);
    }

    public void removeBsqStateListener(DaoStateListener listener) {
        daoStateService.removeDaoStateListener(listener);
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
            throws ProposalValidationException, InsufficientMoneyException, TxException {
        return compensationProposalFactory.createProposalWithTransaction(name,
                link,
                requestedBsq);
    }

    public ProposalWithTransaction getReimbursementProposalWithTransaction(String name,
                                                                           String link,
                                                                           Coin requestedBsq)
            throws ProposalValidationException, InsufficientMoneyException, TxException {
        return reimbursementProposalFactory.createProposalWithTransaction(name,
                link,
                requestedBsq);
    }

    public ProposalWithTransaction getParamProposalWithTransaction(String name,
                                                                   String link,
                                                                   Param param,
                                                                   String paramValue)
            throws ProposalValidationException, InsufficientMoneyException, TxException {
        return changeParamProposalFactory.createProposalWithTransaction(name,
                link,
                param,
                paramValue);
    }

    public ProposalWithTransaction getConfiscateBondProposalWithTransaction(String name,
                                                                            String link,
                                                                            String lockupTxId)
            throws ProposalValidationException, InsufficientMoneyException, TxException {
        return confiscateBondProposalFactory.createProposalWithTransaction(name,
                link,
                lockupTxId);
    }

    public ProposalWithTransaction getBondedRoleProposalWithTransaction(Role role)
            throws ProposalValidationException, InsufficientMoneyException, TxException {
        return roleProposalFactory.createProposalWithTransaction(role);
    }

    public ProposalWithTransaction getGenericProposalWithTransaction(String name,
                                                                     String link)
            throws ProposalValidationException, InsufficientMoneyException, TxException {
        return genericProposalFactory.createProposalWithTransaction(name, link);
    }

    public ProposalWithTransaction getRemoveAssetProposalWithTransaction(String name,
                                                                         String link,
                                                                         Asset asset)
            throws ProposalValidationException, InsufficientMoneyException, TxException {
        return removeAssetProposalFactory.createProposalWithTransaction(name, link, asset);
    }

    public ObservableList<BondedRole> getBondedRoles() {
        return bondedRolesRepository.getBonds();
    }

    public List<BondedRole> getAcceptedBondedRoles() {
        return bondedRolesRepository.getAcceptedBonds();
    }

    // Show fee
    public Coin getProposalFee(int chainHeight) {
        return ProposalConsensus.getFee(daoStateService, chainHeight);
    }

    // Publish proposal tx, proposal payload and persist it to myProposalList
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

    public Tuple2<Coin, Integer> getBlindVoteMiningFeeAndTxVsize(Coin stake)
            throws WalletException, InsufficientMoneyException, TransactionVerificationException {
        return myBlindVoteListService.getMiningFeeAndTxVsize(stake);
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

    // Because last block in request and voting phases must not be used for making a tx as it will get confirmed in the
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

    public Map<Integer, Date> getBlockStartDateByCycleIndex() {
        return periodService.getCycles().stream().collect(Collectors.toMap(
                cycleService::getCycleIndex,
                cycle -> new Date(daoStateService.getBlockTimeAtBlockHeight(cycle.getHeightOfFirstBlock()))
        ));
    }

    // Because last block in request and voting phases must not be used for making a tx as it will get confirmed in the
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
        Cycle currentCycle = periodService.getCurrentCycle();
        return currentCycle != null ? currentCycle.getDuration() : 0;
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

    public boolean daoStateNeedsRebuilding() {
        return daoStateMonitoringService.isInConflictWithSeedNode() || daoStateMonitoringService.isDaoStateBlockChainNotConnecting();
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Use case: Bonding
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void publishLockupTx(Coin lockupAmount, int lockTime, LockupReason lockupReason, byte[] hash,
                                Consumer<String> resultHandler, ExceptionHandler exceptionHandler) {
        lockupTxService.publishLockupTx(lockupAmount, lockTime, lockupReason, hash, resultHandler, exceptionHandler);
    }

    public Tuple2<Coin, Integer> getLockupTxMiningFeeAndTxVsize(Coin lockupAmount,
                                                                int lockTime,
                                                                LockupReason lockupReason,
                                                                byte[] hash)
            throws InsufficientMoneyException, IOException, TransactionVerificationException, WalletException {
        return lockupTxService.getMiningFeeAndTxVsize(lockupAmount, lockTime, lockupReason, hash);
    }

    public void publishUnlockTx(String lockupTxId, Consumer<String> resultHandler,
                                ExceptionHandler exceptionHandler) {
        unlockTxService.publishUnlockTx(lockupTxId, resultHandler, exceptionHandler);
    }

    public Tuple2<Coin, Integer> getUnlockTxMiningFeeAndTxVsize(String lockupTxId)
            throws InsufficientMoneyException, TransactionVerificationException, WalletException {
        return unlockTxService.getMiningFeeAndTxVsize(lockupTxId);
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

    public long getTotalAmountOfInvalidatedBsq() {
        return daoStateService.getTotalAmountOfInvalidatedBsq();
    }

    // Contains burned fee and invalidated bsq due invalid txs
    public long getTotalAmountOfBurntBsq() {
        return daoStateService.getTotalAmountOfBurntBsq();
    }

    public List<Tx> getInvalidTxs() {
        return daoStateService.getInvalidTxs();
    }

    public List<Tx> getIrregularTxs() {
        return daoStateService.getIrregularTxs();
    }

    public long getTotalAmountOfUnspentTxOutputs() {
        // Does not consider confiscated outputs (they stay as utxo)
        return daoStateService.getUnspentTxOutputMap().values().stream().mapToLong(BaseTxOutput::getValue).sum();
    }

    public Optional<Integer> getLockTime(String txId) {
        return daoStateService.getLockTime(txId);
    }


    public List<Bond> getAllBonds() {
        List<Bond> bonds = new ArrayList<>(bondedReputationRepository.getBonds());
        bonds.addAll(bondedRolesRepository.getBonds());
        return bonds;
    }

    public List<Bond> getAllActiveBonds() {
        List<Bond> bonds = new ArrayList<>(bondedReputationRepository.getActiveBonds());
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
        return daoStateService.getIssuanceSetForType(issuanceType).size();
    }

    public Set<Tx> getBurntFeeTxs() {
        return daoStateService.getBurntFeeTxs();
    }

    public Set<TxOutput> getUnspentTxOutputs() {
        return daoStateService.getUnspentTxOutputs();
    }

    public boolean isTxOutputSpendable(TxOutputKey txOutputKey) {
        return daoStateService.isTxOutputSpendable(txOutputKey);
    }

    public long getUnspentTxOutputValue(TxOutputKey key) {
        return daoStateService.getUnspentTxOutputValue(key);
    }

    public int getNumTxs() {
        return daoStateService.getNumTxs();
    }

    public Optional<TxOutput> getLockupTxOutput(String txId) {
        return daoStateService.getLockupTxOutput(txId);
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
        return getParamValue(param, periodService.getChainHeight());
    }

    public String getParamValue(Param param, int blockHeight) {
        return daoStateService.getParamValue(param, blockHeight);
    }

    public void resyncDaoStateFromGenesis(Runnable resultHandler) {
        daoStateStorageService.resyncDaoStateFromGenesis(resultHandler);
    }

    public void resyncDaoStateFromResources(File storageDir) throws IOException {
        daoStateStorageService.resyncDaoStateFromResources(storageDir);
    }

    public boolean isMyRole(Role role) {
        return bondedRolesRepository.isMyRole(role);
    }

    public Optional<Bond> getBondByLockupTxId(String lockupTxId) {
        return getAllBonds().stream().filter(e -> lockupTxId.equals(e.getLockupTxId())).findAny();
    }

    public double getRequiredThreshold(Proposal proposal) {
        return proposalService.getRequiredThreshold(proposal);
    }

    public Coin getRequiredQuorum(Proposal proposal) {
        return proposalService.getRequiredQuorum(proposal);
    }

    public long getRequiredBond(Optional<RoleProposal> roleProposal) {
        Optional<BondedRoleType> bondedRoleType = roleProposal.map(e -> e.getRole().getBondedRoleType());
        checkArgument(bondedRoleType.isPresent(), "bondedRoleType must be present");
        int height = roleProposal.flatMap(p -> daoStateService.getTx(p.getTxId()))
                .map(BaseTx::getBlockHeight)
                .orElse(daoStateService.getChainHeight());
        long requiredBondUnit = roleProposal.map(RoleProposal::getRequiredBondUnit)
                .orElse(bondedRoleType.get().getRequiredBondUnit());
        long baseFactor = daoStateService.getParamValueAsCoin(Param.BONDED_ROLE_FACTOR, height).value;
        return requiredBondUnit * baseFactor;
    }

    public long getRequiredBond(RoleProposal roleProposal) {
        return getRequiredBond(Optional.of(roleProposal));
    }

    public long getRequiredBond(BondedRoleType bondedRoleType) {
        int height = daoStateService.getChainHeight();
        long requiredBondUnit = bondedRoleType.getRequiredBondUnit();
        long baseFactor = daoStateService.getParamValueAsCoin(Param.BONDED_ROLE_FACTOR, height).value;
        return requiredBondUnit * baseFactor;
    }

    public Set<String> getAllPastParamValues(Param param) {
        Set<String> set = new HashSet<>();
        periodService.getCycles().forEach(cycle -> {
            set.add(getParamValue(param, cycle.getHeightOfFirstBlock()));
        });
        return set;
    }

    public Set<String> getAllDonationAddresses() {
        // We support any of the past addresses as well as in case the peer has not enabled the DAO or is out of sync we
        // do not want to break validation.
        Set<String> allPastParamValues = getAllPastParamValues(Param.RECIPIENT_BTC_ADDRESS);

        // If Dao is deactivated we need to add the default address as getAllPastParamValues will not return us any.
        if (allPastParamValues.isEmpty()) {
            allPastParamValues.add(Param.RECIPIENT_BTC_ADDRESS.getDefaultValue());
        }

        if (Config.baseCurrencyNetwork().isMainnet()) {
            // If Dao is deactivated we need to add the past addresses used as well.
            // This list need to be updated once a new address gets defined.
            allPastParamValues.add(DelayedPayoutAddressProvider.BM2019_ADDRESS);
            allPastParamValues.add(DelayedPayoutAddressProvider.BM2_ADDRESS);
            allPastParamValues.add(DelayedPayoutAddressProvider.BM3_ADDRESS);
        }

        return allPastParamValues;
    }

    public boolean isParseBlockChainComplete() {
        return daoStateService.isParseBlockChainComplete();
    }
}
