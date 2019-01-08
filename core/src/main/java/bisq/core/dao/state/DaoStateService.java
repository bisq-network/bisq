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

package bisq.core.dao.state;

import bisq.core.dao.DaoSetupService;
import bisq.core.dao.governance.bond.BondConsensus;
import bisq.core.dao.governance.param.Param;
import bisq.core.dao.state.model.DaoState;
import bisq.core.dao.state.model.blockchain.Block;
import bisq.core.dao.state.model.blockchain.SpentInfo;
import bisq.core.dao.state.model.blockchain.Tx;
import bisq.core.dao.state.model.blockchain.TxInput;
import bisq.core.dao.state.model.blockchain.TxOutput;
import bisq.core.dao.state.model.blockchain.TxOutputKey;
import bisq.core.dao.state.model.blockchain.TxOutputType;
import bisq.core.dao.state.model.blockchain.TxType;
import bisq.core.dao.state.model.governance.Cycle;
import bisq.core.dao.state.model.governance.DecryptedBallotsWithMerits;
import bisq.core.dao.state.model.governance.EvaluatedProposal;
import bisq.core.dao.state.model.governance.Issuance;
import bisq.core.dao.state.model.governance.IssuanceType;
import bisq.core.dao.state.model.governance.ParamChange;
import bisq.core.util.BsqFormatter;

import org.bitcoinj.core.Coin;

import javax.inject.Inject;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import lombok.extern.slf4j.Slf4j;

import javax.annotation.Nullable;

import static com.google.common.base.Preconditions.checkArgument;

/**
 * Provides access methods to DaoState data.
 */
@Slf4j
public class DaoStateService implements DaoSetupService {
    private final DaoState daoState;
    private final GenesisTxInfo genesisTxInfo;
    private final BsqFormatter bsqFormatter;
    private final List<DaoStateListener> daoStateListeners = new CopyOnWriteArrayList<>();
    private boolean parseBlockChainComplete;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    public DaoStateService(DaoState daoState, GenesisTxInfo genesisTxInfo, BsqFormatter bsqFormatter) {
        this.daoState = daoState;
        this.genesisTxInfo = genesisTxInfo;
        this.bsqFormatter = bsqFormatter;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // DaoSetupService
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void addListeners() {
    }

    @Override
    public void start() {
        daoState.setChainHeight(genesisTxInfo.getGenesisBlockHeight());
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Snapshot
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void applySnapshot(DaoState snapshot) {
        log.info("Apply snapshot with chain height {}", snapshot.getChainHeight());

        daoState.setChainHeight(snapshot.getChainHeight());

        daoState.getBlocks().clear();
        daoState.getBlocks().addAll(snapshot.getBlocks());

        daoState.getCycles().clear();
        daoState.getCycles().addAll(snapshot.getCycles());

        daoState.getUnspentTxOutputMap().clear();
        daoState.getUnspentTxOutputMap().putAll(snapshot.getUnspentTxOutputMap());

        daoState.getConfiscatedLockupTxList().clear();
        daoState.getConfiscatedLockupTxList().addAll(snapshot.getConfiscatedLockupTxList());

        daoState.getIssuanceMap().clear();
        daoState.getIssuanceMap().putAll(snapshot.getIssuanceMap());

        daoState.getSpentInfoMap().clear();
        daoState.getSpentInfoMap().putAll(snapshot.getSpentInfoMap());

        daoState.getParamChangeList().clear();
        daoState.getParamChangeList().addAll(snapshot.getParamChangeList());

        daoState.getEvaluatedProposalList().clear();
        daoState.getEvaluatedProposalList().addAll(snapshot.getEvaluatedProposalList());

        daoState.getDecryptedBallotsWithMeritsList().clear();
        daoState.getDecryptedBallotsWithMeritsList().addAll(snapshot.getDecryptedBallotsWithMeritsList());
    }

    public DaoState getClone() {
        return DaoState.getClone(daoState);
    }

    DaoState getClone(DaoState snapshotCandidate) {
        return DaoState.getClone(snapshotCandidate);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // ChainHeight
    ///////////////////////////////////////////////////////////////////////////////////////////

    public int getChainHeight() {
        return daoState.getChainHeight();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Cycle
    ///////////////////////////////////////////////////////////////////////////////////////////

    public LinkedList<Cycle> getCycles() {
        return daoState.getCycles();
    }

    @Nullable
    public Cycle getCurrentCycle() {
        return !getCycles().isEmpty() ? getCycles().getLast() : null;
    }

    public Optional<Cycle> getCycle(int height) {
        return getCycles().stream()
                .filter(cycle -> cycle.getHeightOfFirstBlock() <= height)
                .filter(cycle -> cycle.getHeightOfLastBlock() >= height)
                .findAny();
    }

    public Optional<Integer> getStartHeightOfNextCycle(int blockHeight) {
        return getCycle(blockHeight).map(cycle -> cycle.getHeightOfLastBlock() + 1);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Block
    ///////////////////////////////////////////////////////////////////////////////////////////


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Parser events
    ///////////////////////////////////////////////////////////////////////////////////////////

    // First we get the blockHeight set
    public void onNewBlockHeight(int blockHeight) {
        daoState.setChainHeight(blockHeight);
        daoStateListeners.forEach(listener -> listener.onNewBlockHeight(blockHeight));
    }

    // Second we get the block added with empty txs
    public void onNewBlockWithEmptyTxs(Block block) {
        if (daoState.getBlocks().isEmpty() && block.getHeight() != getGenesisBlockHeight()) {
            log.warn("We don't have any blocks yet and we received a block which is not the genesis block. " +
                    "We ignore that block as the first block need to be the genesis block. " +
                    "That might happen in edge cases at reorgs.");
        } else {
            daoState.getBlocks().add(block);
            daoStateListeners.forEach(l -> l.onEmptyBlockAdded(block));

            log.info("New Block added at blockHeight " + block.getHeight());
        }
    }

    // Third we get the onParseBlockComplete called after all rawTxs of blocks have been parsed
    public void onParseBlockComplete(Block block) {
        // We don't call it during batch parsing as that decreased performance a lot.
        // With calling at each block we got about 50 seconds for 4000 blocks, without about 4 seconds.
        if (parseBlockChainComplete)
            daoStateListeners.forEach(l -> l.onParseTxsComplete(block));
    }

    // Called after parsing of all pending blocks is completed
    public void onParseBlockChainComplete() {
        parseBlockChainComplete = true;

        // Now we need to trigger the onParseBlockComplete to update the state in the app
        getLastBlock().ifPresent(this::onParseBlockComplete);

        daoStateListeners.forEach(DaoStateListener::onParseBlockChainComplete);
    }


    public LinkedList<Block> getBlocks() {
        return daoState.getBlocks();
    }

    /**
     * Whether specified block hash belongs to a block we already know about.
     *
     * @param blockHash The hash of a {@link Block}.
     * @return True if the hash belongs to a {@link Block} we know about, otherwise
     * {@code false}.
     */
    public boolean isBlockHashKnown(String blockHash) {
        // TODO(chirhonul): If performance of O(n) time in number of blocks becomes an issue,
        // we should keep a HashMap of block hash -> Block to make this method O(1).
        return getBlocks().stream().anyMatch(block -> block.getHash().equals(blockHash));
    }

    public Optional<Block> getLastBlock() {
        if (!getBlocks().isEmpty())
            return Optional.of(getBlocks().getLast());
        else
            return Optional.empty();
    }

    public int getBlockHeightOfLastBlock() {
        return getLastBlock().map(Block::getHeight).orElse(0);
    }

    public Optional<Block> getBlockAtHeight(int height) {
        return getBlocks().stream()
                .filter(block -> block.getHeight() == height)
                .findAny();
    }

    public boolean containsBlock(Block block) {
        return getBlocks().contains(block);
    }

    public boolean containsBlockHash(String blockHash) {
        return getBlocks().stream().anyMatch(block -> block.getHash().equals(blockHash));
    }

    public long getBlockTime(int height) {
        return getBlockAtHeight(height).map(Block::getTime).orElse(0L);
    }

    public List<Block> getBlocksFromBlockHeight(int fromBlockHeight) {
        return getBlocks().stream()
                .filter(block -> block.getHeight() >= fromBlockHeight)
                .collect(Collectors.toList());
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Genesis
    ///////////////////////////////////////////////////////////////////////////////////////////

    public String getGenesisTxId() {
        return genesisTxInfo.getGenesisTxId();
    }

    public int getGenesisBlockHeight() {
        return genesisTxInfo.getGenesisBlockHeight();
    }

    public Coin getGenesisTotalSupply() {
        return GenesisTxInfo.GENESIS_TOTAL_SUPPLY;
    }

    public Optional<Tx> getGenesisTx() {
        return getTx(getGenesisTxId());
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Tx
    ///////////////////////////////////////////////////////////////////////////////////////////

    public Stream<Tx> getTxStream() {
        return getBlocks().stream()
                .flatMap(block -> block.getTxs().stream());
    }

    public Map<String, Tx> getTxMap() {
        return getTxStream().collect(Collectors.toMap(Tx::getId, tx -> tx));
    }

    public Set<Tx> getTxs() {
        return getTxStream().collect(Collectors.toSet());
    }

    public Optional<Tx> getTx(String txId) {
        return getTxStream().filter(tx -> tx.getId().equals(txId)).findAny();
    }

    public boolean containsTx(String txId) {
        return getTx(txId).isPresent();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // TxType
    ///////////////////////////////////////////////////////////////////////////////////////////

    public Optional<TxType> getOptionalTxType(String txId) {
        return getTx(txId).map(Tx::getTxType);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // BurntFee
    ///////////////////////////////////////////////////////////////////////////////////////////

    public long getBurntFee(String txId) {
        return getTx(txId).map(Tx::getBurntFee).orElse(0L);
    }

    public boolean hasTxBurntFee(String txId) {
        return getBurntFee(txId) > 0;
    }

    public long getTotalBurntFee() {
        return getTxStream()
                .mapToLong(Tx::getBurntFee)
                .sum();
    }

    public Set<Tx> getBurntFeeTxs() {
        return getTxStream()
                .filter(tx -> tx.getBurntFee() > 0)
                .collect(Collectors.toSet());
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // TxInput
    ///////////////////////////////////////////////////////////////////////////////////////////

    public Optional<TxOutput> getConnectedTxOutput(TxInput txInput) {
        return getTx(txInput.getConnectedTxOutputTxId())
                .map(tx -> tx.getTxOutputs().get(txInput.getConnectedTxOutputIndex()));
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // TxOutput
    ///////////////////////////////////////////////////////////////////////////////////////////

    public Stream<TxOutput> getTxOutputStream() {
        return getTxStream()
                .flatMap(tx -> tx.getTxOutputs().stream());
    }

    public boolean existsTxOutput(TxOutputKey key) {
        return getTxOutputStream().anyMatch(txOutput -> txOutput.getKey().equals(key));
    }

    public Optional<TxOutput> getTxOutput(TxOutputKey txOutputKey) {
        return getTxOutputStream()
                .filter(txOutput -> txOutput.getKey().equals(txOutputKey))
                .findAny();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // UnspentTxOutput
    ///////////////////////////////////////////////////////////////////////////////////////////

    public Map<TxOutputKey, TxOutput> getUnspentTxOutputMap() {
        return daoState.getUnspentTxOutputMap();
    }

    public void addUnspentTxOutput(TxOutput txOutput) {
        getUnspentTxOutputMap().put(txOutput.getKey(), txOutput);
    }

    public void removeUnspentTxOutput(TxOutput txOutput) {
        getUnspentTxOutputMap().remove(txOutput.getKey());
    }

    public boolean isUnspent(TxOutputKey key) {
        return getUnspentTxOutputMap().containsKey(key);
    }

    public Set<TxOutput> getUnspentTxOutputs() {
        return new HashSet<>(getUnspentTxOutputMap().values());
    }

    public Optional<TxOutput> getUnspentTxOutput(TxOutputKey key) {
        return Optional.ofNullable(getUnspentTxOutputMap().getOrDefault(key, null));
    }

    public boolean isTxOutputSpendable(TxOutputKey key) {
        if (!isUnspent(key))
            return false;

        Optional<TxOutput> optionalTxOutput = getUnspentTxOutput(key);
        // The above isUnspent call satisfies optionalTxOutput.isPresent()
        checkArgument(optionalTxOutput.isPresent(), "optionalTxOutput must be present");
        TxOutput txOutput = optionalTxOutput.get();

        switch (txOutput.getTxOutputType()) {
            case UNDEFINED_OUTPUT:
                return false;
            case GENESIS_OUTPUT:
            case BSQ_OUTPUT:
                return true;
            case BTC_OUTPUT:
                return false;
            case PROPOSAL_OP_RETURN_OUTPUT:
            case COMP_REQ_OP_RETURN_OUTPUT:
            case REIMBURSEMENT_OP_RETURN_OUTPUT:
            case ISSUANCE_CANDIDATE_OUTPUT:
                return true;
            case BLIND_VOTE_LOCK_STAKE_OUTPUT:
                return false;
            case BLIND_VOTE_OP_RETURN_OUTPUT:
            case VOTE_REVEAL_UNLOCK_STAKE_OUTPUT:
            case VOTE_REVEAL_OP_RETURN_OUTPUT:
                return true;
            case ASSET_LISTING_FEE_OP_RETURN_OUTPUT:
            case PROOF_OF_BURN_OP_RETURN_OUTPUT:
                return false;
            case LOCKUP_OUTPUT:
                return false;
            case LOCKUP_OP_RETURN_OUTPUT:
                return true;
            case UNLOCK_OUTPUT:
                return isLockTimeOverForUnlockTxOutput(txOutput);
            case INVALID_OUTPUT:
                return false;
            default:
                return false;
        }
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // TxOutputType
    ///////////////////////////////////////////////////////////////////////////////////////////

    public Set<TxOutput> getTxOutputsByTxOutputType(TxOutputType txOutputType) {
        return getTxOutputStream()
                .filter(txOutput -> txOutput.getTxOutputType() == txOutputType)
                .collect(Collectors.toSet());
    }

    public boolean isBsqTxOutputType(TxOutput txOutput) {
        final TxOutputType txOutputType = txOutput.getTxOutputType();
        switch (txOutputType) {
            case UNDEFINED_OUTPUT:
                return false;
            case GENESIS_OUTPUT:
            case BSQ_OUTPUT:
                return true;
            case BTC_OUTPUT:
                return false;
            case PROPOSAL_OP_RETURN_OUTPUT:
            case COMP_REQ_OP_RETURN_OUTPUT:
            case REIMBURSEMENT_OP_RETURN_OUTPUT:
                return true;
            case ISSUANCE_CANDIDATE_OUTPUT:
                return isIssuanceTx(txOutput.getTxId());
            case BLIND_VOTE_LOCK_STAKE_OUTPUT:
            case BLIND_VOTE_OP_RETURN_OUTPUT:
            case VOTE_REVEAL_UNLOCK_STAKE_OUTPUT:
            case VOTE_REVEAL_OP_RETURN_OUTPUT:
            case ASSET_LISTING_FEE_OP_RETURN_OUTPUT:
            case PROOF_OF_BURN_OP_RETURN_OUTPUT:
            case LOCKUP_OUTPUT:
            case LOCKUP_OP_RETURN_OUTPUT:
            case UNLOCK_OUTPUT:
                return true;
            case INVALID_OUTPUT:
                return false;
            default:
                return false;
        }
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // TxOutputType - Voting
    ///////////////////////////////////////////////////////////////////////////////////////////

    public Set<TxOutput> getUnspentBlindVoteStakeTxOutputs() {
        return getTxOutputsByTxOutputType(TxOutputType.BLIND_VOTE_LOCK_STAKE_OUTPUT).stream()
                .filter(txOutput -> isUnspent(txOutput.getKey()))
                .collect(Collectors.toSet());
    }

    public Set<TxOutput> getVoteRevealOpReturnTxOutputs() {
        return getTxOutputsByTxOutputType(TxOutputType.VOTE_REVEAL_OP_RETURN_OUTPUT);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // TxOutputType - Issuance
    ///////////////////////////////////////////////////////////////////////////////////////////

    public Set<TxOutput> getIssuanceCandidateTxOutputs() {
        return getTxOutputsByTxOutputType(TxOutputType.ISSUANCE_CANDIDATE_OUTPUT);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Issuance
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void addIssuance(Issuance issuance) {
        daoState.getIssuanceMap().put(issuance.getTxId(), issuance);
    }

    public Set<Issuance> getIssuanceSet(IssuanceType issuanceType) {
        return daoState.getIssuanceMap().values().stream()
                .filter(issuance -> issuance.getIssuanceType() == issuanceType)
                .collect(Collectors.toSet());
    }

    public Optional<Issuance> getIssuance(String txId, IssuanceType issuanceType) {
        return daoState.getIssuanceMap().values().stream()
                .filter(issuance -> issuance.getTxId().equals(txId))
                .filter(issuance -> issuance.getIssuanceType() == issuanceType)
                .findAny();
    }

    public Optional<Issuance> getIssuance(String txId) {
        return daoState.getIssuanceMap().values().stream()
                .filter(issuance -> issuance.getTxId().equals(txId))
                .findAny();
    }

    public boolean isIssuanceTx(String txId) {
        return getIssuance(txId).isPresent();
    }

    public boolean isIssuanceTx(String txId, IssuanceType issuanceType) {
        return getIssuance(txId, issuanceType).isPresent();
    }

    public int getIssuanceBlockHeight(String txId) {
        return getIssuance(txId)
                .map(Issuance::getChainHeight)
                .orElse(0);
    }

    public long getTotalIssuedAmount(IssuanceType issuanceType) {
        return getIssuanceCandidateTxOutputs().stream()
                .filter(txOutput -> isIssuanceTx(txOutput.getTxId(), issuanceType))
                .mapToLong(TxOutput::getValue)
                .sum();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Non-BSQ
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void addNonBsqTxOutput(TxOutput txOutput) {
        checkArgument(txOutput.getTxOutputType() == TxOutputType.ISSUANCE_CANDIDATE_OUTPUT,
                "txOutput must be type ISSUANCE_CANDIDATE_OUTPUT");
        daoState.getNonBsqTxOutputMap().put(txOutput.getKey(), txOutput);
    }

    public Optional<TxOutput> getBtcTxOutput(TxOutputKey key) {
        // Issuance candidates which did not got accepted in voting are covered here
        Map<TxOutputKey, TxOutput> nonBsqTxOutputMap = daoState.getNonBsqTxOutputMap();
        if (nonBsqTxOutputMap.containsKey(key))
            return Optional.of(nonBsqTxOutputMap.get(key));

        // We might have also outputs of type BTC_OUTPUT
        return getTxOutputsByTxOutputType(TxOutputType.BTC_OUTPUT).stream()
                .filter(output -> output.getKey().equals(key))
                .findAny();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Bond
    ///////////////////////////////////////////////////////////////////////////////////////////

    // Terminology
    // HashOfBondId - 20 bytes hash of the bond ID
    // Lockup - txOutputs of LOCKUP type
    // Unlocking - UNLOCK txOutputs that are not yet spendable due to lock time
    // Unlocked - UNLOCK txOutputs that are spendable since the lock time has passed
    // LockTime - 0 means that the funds are spendable at the same block of the UNLOCK tx. For the user that is not
    // supported as we do not expose unconfirmed BSQ txs so lockTime of 1 is the smallest the use can actually use.

    // LockTime
    public Optional<Integer> getLockTime(String txId) {
        return getTx(txId).map(Tx::getLockTime);
    }

    public Optional<byte[]> getLockupHash(TxOutput txOutput) {
        Optional<Tx> lockupTx = Optional.empty();
        String txId = txOutput.getTxId();
        if (txOutput.getTxOutputType() == TxOutputType.LOCKUP_OUTPUT) {
            lockupTx = getTx(txId);
        } else if (isUnlockTxOutputAndLockTimeNotOver(txOutput)) {
            if (getTx(txId).isPresent()) {
                Tx unlockTx = getTx(txId).get();
                lockupTx = getTx(unlockTx.getTxInputs().get(0).getConnectedTxOutputTxId());
            }
        }
        if (lockupTx.isPresent()) {
            byte[] opReturnData = lockupTx.get().getLastTxOutput().getOpReturnData();
            if (opReturnData != null)
                return Optional.of(BondConsensus.getHashFromOpReturnData(opReturnData));
        }
        return Optional.empty();
    }

   /* public Set<byte[]> getHashOfBondIdSet() {
        return getTxOutputStream()
                .filter(txOutput -> isUnspent(txOutput.getKey()))
                .filter(txOutput -> txOutput.getTxOutputType() == TxOutputType.LOCKUP ||
                        isUnlockTxOutputAndLockTimeNotOver(txOutput))
                .map(txOutput -> getHash(txOutput).orElse(null))
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
    }*/

    public boolean isUnlockTxOutputAndLockTimeNotOver(TxOutput txOutput) {
        return txOutput.getTxOutputType() == TxOutputType.UNLOCK_OUTPUT && !isLockTimeOverForUnlockTxOutput(txOutput);
    }

    // Lockup
    public boolean isLockupOutput(TxOutputKey key) {
        Optional<TxOutput> opTxOutput = getUnspentTxOutput(key);
        return opTxOutput.isPresent() && isLockupOutput(opTxOutput.get());
    }

    public boolean isLockupOutput(TxOutput txOutput) {
        return txOutput.getTxOutputType() == TxOutputType.LOCKUP_OUTPUT;
    }

    public Set<TxOutput> getLockupTxOutputs() {
        return getTxOutputsByTxOutputType(TxOutputType.LOCKUP_OUTPUT);
    }

    public Set<TxOutput> getUnlockTxOutputs() {
        return getTxOutputsByTxOutputType(TxOutputType.UNLOCK_OUTPUT);
    }

    public Set<TxOutput> getUnspentLockUpTxOutputs() {
        return getTxOutputsByTxOutputType(TxOutputType.LOCKUP_OUTPUT).stream()
                .filter(txOutput -> isUnspent(txOutput.getKey()))
                .collect(Collectors.toSet());
    }

    public Optional<TxOutput> getLockupTxOutput(String txId) {
        return getTx(txId).flatMap(tx -> tx.getTxOutputs().stream()
                .filter(this::isLockupOutput)
                .findFirst());
    }

    public Optional<TxOutput> getLockupOpReturnTxOutput(String txId) {
        return getTx(txId).map(Tx::getLastTxOutput).filter(txOutput -> txOutput.getOpReturnData() != null);
    }

    // Returns amount of all LOCKUP txOutputs (they might have been unlocking or unlocked in the meantime)
    public long getTotalAmountOfLockupTxOutputs() {
        return getLockupTxOutputs().stream()
                .filter(txOutput -> !isConfiscatedLockupTxOutput(txOutput.getTxId()))
                .mapToLong(TxOutput::getValue)
                .sum();
    }

    // Returns the current locked up amount (excluding unlocking and unlocked)
    public long getTotalLockupAmount() {
        return getTotalAmountOfLockupTxOutputs() - getTotalAmountOfUnLockingTxOutputs() - getTotalAmountOfUnLockedTxOutputs();
    }


    // Unlock
    public boolean isUnspentUnlockOutput(TxOutputKey key) {
        Optional<TxOutput> opTxOutput = getUnspentTxOutput(key);
        return opTxOutput.isPresent() && isUnlockOutput(opTxOutput.get());
    }

    public boolean isUnlockOutput(TxOutput txOutput) {
        return txOutput.getTxOutputType() == TxOutputType.UNLOCK_OUTPUT;
    }

    // Unlocking
    // Return UNLOCK TxOutputs that are not yet spendable as lockTime is not over
    public Stream<TxOutput> getUnspentUnlockingTxOutputsStream() {
        return getTxOutputsByTxOutputType(TxOutputType.UNLOCK_OUTPUT).stream()
                .filter(txOutput -> isUnspent(txOutput.getKey()))
                .filter(txOutput -> !isLockTimeOverForUnlockTxOutput(txOutput));
    }

    public long getTotalAmountOfUnLockingTxOutputs() {
        return getUnspentUnlockingTxOutputsStream()
                .filter(txOutput -> !isConfiscatedUnlockTxOutput(txOutput.getTxId()))
                .mapToLong(TxOutput::getValue)
                .sum();
    }

    public boolean isUnlockingAndUnspent(TxOutputKey key) {
        Optional<TxOutput> opTxOutput = getUnspentTxOutput(key);
        return opTxOutput.isPresent() && isUnlockingAndUnspent(opTxOutput.get());
    }

    public boolean isUnlockingAndUnspent(String unlockTxId) {
        Optional<Tx> optionalTx = getTx(unlockTxId);
        return optionalTx.isPresent() && isUnlockingAndUnspent(optionalTx.get().getTxOutputs().get(0));
    }

    public boolean isUnlockingAndUnspent(TxOutput unlockTxOutput) {
        return unlockTxOutput.getTxOutputType() == TxOutputType.UNLOCK_OUTPUT &&
                isUnspent(unlockTxOutput.getKey()) &&
                !isLockTimeOverForUnlockTxOutput(unlockTxOutput);
    }

    public Optional<Tx> getLockupTxFromUnlockTxId(String unlockTxId) {
        return getTx(unlockTxId).flatMap(tx -> getTx(tx.getTxInputs().get(0).getConnectedTxOutputTxId()));
    }

    // Unlocked
    public Optional<Integer> getUnlockBlockHeight(String txId) {
        return getTx(txId).map(Tx::getUnlockBlockHeight);
    }

    public boolean isLockTimeOverForUnlockTxOutput(TxOutput unlockTxOutput) {
        checkArgument(isUnlockOutput(unlockTxOutput), "txOutput must be of type UNLOCK");
        return getUnlockBlockHeight(unlockTxOutput.getTxId())
                .map(unlockBlockHeight -> BondConsensus.isLockTimeOver(unlockBlockHeight, getChainHeight()))
                .orElse(false);
    }

    // We don't care here about the unspent state
    public Stream<TxOutput> getUnlockedTxOutputsStream() {
        return getTxOutputsByTxOutputType(TxOutputType.UNLOCK_OUTPUT).stream()
                .filter(txOutput -> !isConfiscatedUnlockTxOutput(txOutput.getTxId()))
                .filter(this::isLockTimeOverForUnlockTxOutput);
    }

    public long getTotalAmountOfUnLockedTxOutputs() {
        return getUnlockedTxOutputsStream()
                .mapToLong(TxOutput::getValue)
                .sum();
    }

    public long getTotalAmountOfConfiscatedTxOutputs() {
        return daoState.getConfiscatedLockupTxList()
                .stream()
                .map(txId -> getTx(txId))
                .filter(optionalTx -> optionalTx.isPresent())
                .mapToLong(optionalTx -> optionalTx.get().getLockupOutput().getValue())
                .sum();
    }

    // Confiscate bond
    public void confiscateBond(String lockupTxId) {
        Optional<TxOutput> optionalTxOutput = getLockupTxOutput(lockupTxId);
        if (optionalTxOutput.isPresent()) {
            TxOutput lockupTxOutput = optionalTxOutput.get();
            if (isUnspent(lockupTxOutput.getKey())) {
                log.warn("confiscateBond: lockupTxOutput {} is still unspent so we can confiscate it.", lockupTxOutput.getKey());
                doConfiscateBond(lockupTxId);
            } else {
                // We lookup for the unlock tx which need to be still in unlocking state
                Optional<SpentInfo> optionalSpentInfo = getSpentInfo(lockupTxOutput);
                checkArgument(optionalSpentInfo.isPresent(), "optionalSpentInfo must be present");
                String unlockTxId = optionalSpentInfo.get().getTxId();
                if (isUnlockingAndUnspent(unlockTxId)) {
                    // We found the unlock tx is still not spend
                    log.warn("confiscateBond: lockupTxOutput {} is still unspent so we can We confiscate it.", lockupTxOutput.getKey());
                    doConfiscateBond(lockupTxId);
                } else {
                    // We could be more radical here and confiscate the output if it is unspent but lock time is over,
                    // but its probably better to stick to the rules that confiscation can only happen before lock time
                    // is over.
                    log.warn("We could not confiscate the bond because the unlock tx was already spent or lock time " +
                            "has exceeded. unlockTxId={}", unlockTxId);
                }
            }
        } else {
            log.warn("No lockupTxOutput found for lockupTxId {}", lockupTxId);
        }
    }

    private void doConfiscateBond(String lockupTxId) {
        log.warn("TxId {} added to confiscatedLockupTxIdList.", lockupTxId);
        daoState.getConfiscatedLockupTxList().add(lockupTxId);
    }

    public boolean isConfiscated(TxOutputKey txOutputKey) {
        if (isLockupOutput(txOutputKey))
            return isConfiscatedLockupTxOutput(txOutputKey.getTxId());
        else if (isUnspentUnlockOutput(txOutputKey))
            return isConfiscatedUnlockTxOutput(txOutputKey.getTxId());
        return false;
    }

    public boolean isConfiscated(String lockupTxId) {
        return daoState.getConfiscatedLockupTxList().contains(lockupTxId);
    }

    public boolean isConfiscatedLockupTxOutput(String lockupTxId) {
        return isConfiscated(lockupTxId);
    }

    public boolean isConfiscatedUnlockTxOutput(String unlockTxId) {
        return getLockupTxFromUnlockTxId(unlockTxId).
                map(lockupTx -> isConfiscated(lockupTx.getId())).
                orElse(false);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Param
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void setNewParam(int blockHeight, Param param, String paramValue) {
        List<ParamChange> paramChangeList = daoState.getParamChangeList();
        getStartHeightOfNextCycle(blockHeight)
                .ifPresent(heightOfNewCycle -> {
                    ParamChange paramChange = new ParamChange(param.name(), paramValue, heightOfNewCycle);
                    paramChangeList.add(paramChange);
                    // Addition with older height should not be possible but to ensure correct sorting lets run a sort.
                    paramChangeList.sort(Comparator.comparingInt(ParamChange::getActivationHeight));
                });
    }

    public Coin getParamValueAsCoin(Param param, int blockHeight) {
        String paramValue = getParamValue(param, blockHeight);
        return bsqFormatter.parseParamValueToCoin(param, paramValue);
    }

    public double getParamValueAsPercentDouble(Param param, int blockHeight) {
        String paramValue = getParamValue(param, blockHeight);
        return bsqFormatter.parsePercentStringToDouble(paramValue);
    }

    public int getParamValueAsBlock(Param param, int blockHeight) {
        String paramValue = getParamValue(param, blockHeight);
        return Integer.parseInt(paramValue);
    }

    public String getParamValue(Param param, int blockHeight) {
        List<ParamChange> paramChangeList = new ArrayList<>(daoState.getParamChangeList());
        if (!paramChangeList.isEmpty()) {
            // List is sorted by height, we start from latest entries to find most recent entry.
            for (int i = paramChangeList.size() - 1; i >= 0; i--) {
                ParamChange paramChange = paramChangeList.get(i);
                if (paramChange.getParamName().equals(param.name()) &&
                        blockHeight >= paramChange.getActivationHeight()) {
                    return paramChange.getValue();
                }
            }
        }

        // If no value found we use default values
        return param.getDefaultValue();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // SpentInfo
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void setSpentInfo(TxOutputKey txOutputKey, SpentInfo spentInfo) {
        daoState.getSpentInfoMap().put(txOutputKey, spentInfo);
    }

    public Optional<SpentInfo> getSpentInfo(TxOutput txOutput) {
        return Optional.ofNullable(daoState.getSpentInfoMap().getOrDefault(txOutput.getKey(), null));
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Vote result data
    ///////////////////////////////////////////////////////////////////////////////////////////

    public List<EvaluatedProposal> getEvaluatedProposalList() {
        return daoState.getEvaluatedProposalList();
    }

    public List<DecryptedBallotsWithMerits> getDecryptedBallotsWithMeritsList() {
        return daoState.getDecryptedBallotsWithMeritsList();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Asset listing fee
    ///////////////////////////////////////////////////////////////////////////////////////////

    public Set<TxOutput> getAssetListingFeeOpReturnTxOutputs() {
        return getTxOutputsByTxOutputType(TxOutputType.ASSET_LISTING_FEE_OP_RETURN_OUTPUT);
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Proof of burn
    ///////////////////////////////////////////////////////////////////////////////////////////

    public Set<TxOutput> getProofOfBurnOpReturnTxOutputs() {
        return getTxOutputsByTxOutputType(TxOutputType.PROOF_OF_BURN_OP_RETURN_OUTPUT);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Listeners
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void addBsqStateListener(DaoStateListener listener) {
        daoStateListeners.add(listener);
    }

    public void removeBsqStateListener(DaoStateListener listener) {
        daoStateListeners.remove(listener);
    }
}

