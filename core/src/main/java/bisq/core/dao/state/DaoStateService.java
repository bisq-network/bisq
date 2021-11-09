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
import bisq.core.dao.state.model.blockchain.BaseTxOutput;
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
import bisq.core.util.ParsingUtils;
import bisq.core.util.coin.BsqFormatter;

import org.bitcoinj.core.Coin;

import javax.inject.Inject;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import lombok.Getter;
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
    @Getter
    private boolean parseBlockChainComplete;
    private boolean allowDaoStateChange;


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
        allowDaoStateChange = true;
        assertDaoStateChange();
        daoState.setChainHeight(genesisTxInfo.getGenesisBlockHeight());
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Snapshot
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void applySnapshot(DaoState snapshot) {
        allowDaoStateChange = true;
        assertDaoStateChange();

        log.info("Apply snapshot with chain height {}", snapshot.getChainHeight());

        daoState.setChainHeight(snapshot.getChainHeight());

        daoState.setTxCache(snapshot.getTxCache());

        daoState.clearAndSetBlocks(snapshot.getBlocks());

        daoState.getCycles().clear();
        daoState.getCycles().addAll(snapshot.getCycles());

        daoState.getUnspentTxOutputMap().clear();
        daoState.getUnspentTxOutputMap().putAll(snapshot.getUnspentTxOutputMap());

        daoState.getSpentInfoMap().clear();
        daoState.getSpentInfoMap().putAll(snapshot.getSpentInfoMap());

        daoState.getConfiscatedLockupTxList().clear();
        daoState.getConfiscatedLockupTxList().addAll(snapshot.getConfiscatedLockupTxList());

        daoState.getIssuanceMap().clear();
        daoState.getIssuanceMap().putAll(snapshot.getIssuanceMap());

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

    public protobuf.DaoState getBsqStateCloneExcludingBlocks() {
        return DaoState.getBsqStateCloneExcludingBlocks(daoState);
    }

    public byte[] getSerializedStateForHashChain() {
        return daoState.getSerializedStateForHashChain();
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

    public void addCycle(Cycle cycle) {
        assertDaoStateChange();
        getCycles().add(cycle);
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

    public Optional<Integer> getStartHeightOfCurrentCycle(int blockHeight) {
        return getCycle(blockHeight).map(cycle -> cycle.getHeightOfFirstBlock());
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Block
    ///////////////////////////////////////////////////////////////////////////////////////////


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Parser events
    ///////////////////////////////////////////////////////////////////////////////////////////

    // First we get the blockHeight set
    public void onNewBlockHeight(int blockHeight) {
        allowDaoStateChange = true;
        daoState.setChainHeight(blockHeight);
        daoStateListeners.forEach(listener -> listener.onNewBlockHeight(blockHeight));
    }

    // Second we get the block added with empty txs
    public void onNewBlockWithEmptyTxs(Block block) {
        assertDaoStateChange();
        if (daoState.getBlocks().isEmpty() && block.getHeight() != getGenesisBlockHeight()) {
            log.warn("We don't have any blocks yet and we received a block which is not the genesis block. " +
                    "We ignore that block as the first block need to be the genesis block. " +
                    "That might happen in edge cases at reorgs. Received block={}", block);
        } else {
            daoState.addBlock(block);

            if (parseBlockChainComplete)
                log.info("New Block added at blockHeight {}", block.getHeight());
        }
    }

    // Third we add each successfully parsed BSQ tx to the last block
    public void onNewTxForLastBlock(Block block, Tx tx) {
        assertDaoStateChange();

        getLastBlock().ifPresent(lastBlock -> {
            if (block == lastBlock) {
                // We need to ensure that the txs in all blocks are in sync with the txs in our txMap (cache).
                block.addTx(tx);
                daoState.addToTxCache(tx);
            } else {
                // Not clear if this case can happen but at onNewBlockWithEmptyTxs we handle such a potential edge
                // case as well, so we need to reflect that here as well.
                log.warn("Block for parsing does not match last block. That might happen in edge cases at reorgs. " +
                        "Received block={}", block);
            }
        });
    }

    // Fourth we get the onParseBlockComplete called after all rawTxs of blocks have been parsed
    public void onParseBlockComplete(Block block) {
        if (parseBlockChainComplete)
            log.info("Parse block completed: Block height {}, {} BSQ transactions.", block.getHeight(), block.getTxs().size());

        // Need to be called before onParseTxsCompleteAfterBatchProcessing as we use it in
        // VoteResult and other listeners like balances usually listen on onParseTxsCompleteAfterBatchProcessing
        // so we need to make sure that vote result calculation is completed before (e.g. for comp. request to
        // update balance).
        daoStateListeners.forEach(l -> l.onParseBlockComplete(block));

        // We use 2 different handlers as we don't want to update domain listeners during batch processing of all
        // blocks as that causes performance issues. In earlier versions when we updated at each block it took
        // 50 sec. for 4000 blocks, after that change it was about 4 sec.
        // Clients
        if (parseBlockChainComplete)
            daoStateListeners.forEach(l -> l.onParseBlockCompleteAfterBatchProcessing(block));

        // Here listeners must not trigger any state change in the DAO as we trigger the validation service to
        // generate a hash of the state.
        allowDaoStateChange = false;
        daoStateListeners.forEach(l -> l.onDaoStateChanged(block));
    }

    // Called after parsing of all pending blocks is completed
    public void onParseBlockChainComplete() {
        log.info("Parse blockchain completed");
        parseBlockChainComplete = true;

        getLastBlock().ifPresent(block -> {
            daoStateListeners.forEach(l -> l.onParseBlockCompleteAfterBatchProcessing(block));
        });

        daoStateListeners.forEach(DaoStateListener::onParseBlockChainComplete);
    }

    public List<Block> getBlocks() {
        return daoState.getBlocks();
    }

    public Optional<Block> getLastBlock() {
        if (!getBlocks().isEmpty())
            return Optional.of(daoState.getLastBlock());
        else
            return Optional.empty();
    }

    public int getBlockHeightOfLastBlock() {
        return getLastBlock().map(Block::getHeight).orElse(0);
    }

    public String getBlockHashOfLastBlock() {
        return getLastBlock().map(Block::getHash).orElse("");
    }

    public Optional<Block> getBlockAtHeight(int height) {
        return Optional.ofNullable(daoState.getBlocksByHeight().get(height));
    }

    public long getBlockTimeAtBlockHeight(int height) {
        return getBlockAtHeight(height).map(Block::getTime).orElse(0L);
    }

    public boolean containsBlock(Block block) {
        return getBlocks().contains(block);
    }

    public long getBlockTime(int height) {
        return getBlockAtHeight(height).map(Block::getTime).orElse(0L);
    }

    public List<Block> getBlocksFromBlockHeight(int fromBlockHeight) {
        return getBlocksFromBlockHeight(fromBlockHeight, Integer.MAX_VALUE);
    }

    public List<Block> getBlocksFromBlockHeight(int fromBlockHeight, int numMaxBlocks) {
        // We limit requests to numMaxBlocks blocks, to avoid performance issues and too
        // large network data in case a node requests too far back in history.
        return getBlocks().stream()
                .filter(block -> block.getHeight() >= fromBlockHeight)
                .limit(numMaxBlocks)
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
        return Coin.valueOf(genesisTxInfo.getGenesisTotalSupply());
    }

    public Optional<Tx> getGenesisTx() {
        return getTx(getGenesisTxId());
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Tx
    ///////////////////////////////////////////////////////////////////////////////////////////

    public Stream<Tx> getUnorderedTxStream() {
        return daoState.getTxCache().values().stream();
    }

    public int getNumTxs() {
        return daoState.getTxCache().size();
    }

    public List<Tx> getInvalidTxs() {
        return getUnorderedTxStream().filter(tx -> tx.getTxType() == TxType.INVALID).collect(Collectors.toList());
    }

    public List<Tx> getIrregularTxs() {
        return getUnorderedTxStream().filter(tx -> tx.getTxType() == TxType.IRREGULAR).collect(Collectors.toList());
    }

    public Optional<Tx> getTx(String txId) {
        return Optional.ofNullable(daoState.getTxCache().get(txId));
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
    // BurntFee (trade fee and fee burned at proof of burn)
    ///////////////////////////////////////////////////////////////////////////////////////////

    public long getBurntFee(String txId) {
        return getTx(txId).map(Tx::getBurntFee).orElse(0L);
    }

    public boolean hasTxBurntFee(String txId) {
        return getBurntFee(txId) > 0;
    }

    public Set<Tx> getTradeFeeTxs() {
        return getUnorderedTxStream()
                .filter(tx -> tx.getTxType() == TxType.PAY_TRADE_FEE)
                .collect(Collectors.toSet());
    }

    public Set<Tx> getProofOfBurnTxs() {
        return getUnorderedTxStream()
                .filter(tx -> tx.getTxType() == TxType.PROOF_OF_BURN)
                .collect(Collectors.toSet());
    }

    // Any tx with burned BSQ
    public Set<Tx> getBurntFeeTxs() {
        return getUnorderedTxStream()
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

    private Stream<TxOutput> getUnorderedTxOutputStream() {
        return getUnorderedTxStream()
                .flatMap(tx -> tx.getTxOutputs().stream());
    }

    public boolean existsTxOutput(TxOutputKey key) {
        return getUnorderedTxOutputStream().anyMatch(txOutput -> txOutput.getKey().equals(key));
    }

    public Optional<TxOutput> getTxOutput(TxOutputKey txOutputKey) {
        return getUnorderedTxOutputStream()
                .filter(txOutput -> txOutput.getKey().equals(txOutputKey))
                .findAny();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // UnspentTxOutput
    ///////////////////////////////////////////////////////////////////////////////////////////

    public TreeMap<TxOutputKey, TxOutput> getUnspentTxOutputMap() {
        return daoState.getUnspentTxOutputMap();
    }

    public void addUnspentTxOutput(TxOutput txOutput) {
        assertDaoStateChange();
        getUnspentTxOutputMap().put(txOutput.getKey(), txOutput);
    }

    public void removeUnspentTxOutput(TxOutput txOutput) {
        assertDaoStateChange();
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

    public long getUnspentTxOutputValue(TxOutputKey key) {
        return getUnspentTxOutput(key)
                .map(BaseTxOutput::getValue)
                .orElse(0L);
    }

    public boolean isTxOutputSpendable(TxOutputKey key) {
        if (!isUnspent(key))
            return false;

        Optional<TxOutput> optionalTxOutput = getUnspentTxOutput(key);
        // The above isUnspent call satisfies optionalTxOutput.isPresent()
        checkArgument(optionalTxOutput.isPresent(), "optionalTxOutput must be present");
        TxOutput txOutput = optionalTxOutput.get();
        return isTxOutputSpendable(txOutput);
    }

    public boolean isTxOutputSpendable(TxOutput txOutput) {
        // OP_RETURN_OUTPUTs are actually not spendable but as we have no value on them
        // they would not be used anyway.
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

    private Set<TxOutput> getTxOutputsByTxOutputType(TxOutputType txOutputType) {
        return daoState.getTxOutputByTxOutputType(txOutputType);
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
        assertDaoStateChange();
        daoState.getIssuanceMap().put(issuance.getTxId(), issuance);
    }

    public Set<Issuance> getIssuanceSetForType(IssuanceType issuanceType) {
        return daoState.getIssuanceMap().values().stream()
                .filter(issuance -> issuance.getIssuanceType() == issuanceType)
                .collect(Collectors.toSet());
    }

    public Optional<Issuance> getIssuance(String txId, IssuanceType issuanceType) {
        return getIssuance(txId).filter(issuance -> issuance.getIssuanceType() == issuanceType);
    }

    public Optional<Issuance> getIssuance(String txId) {
        return Optional.ofNullable(daoState.getIssuanceMap().get(txId));
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
    // Not accepted issuance candidate outputs of past cycles
    ///////////////////////////////////////////////////////////////////////////////////////////

    public boolean isRejectedIssuanceOutput(TxOutputKey txOutputKey) {
        Cycle currentCycle = getCurrentCycle();
        return currentCycle != null &&
                getIssuanceCandidateTxOutputs().stream()
                        .filter(txOutput -> txOutput.getKey().equals(txOutputKey))
                        .filter(txOutput -> !currentCycle.isInCycle(txOutput.getBlockHeight()))
                        .anyMatch(txOutput -> !isIssuanceTx(txOutput.getTxId()));

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
    // supported as we do not expose unconfirmed BSQ txs so lockTime of 1 is the smallest the user can actually use.

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

    public Optional<Tx> getUnlockTxFromLockupTxId(String lockupTxId) {
        return getTx(lockupTxId).flatMap(tx -> getSpentInfo(tx.getTxOutputs().get(0))).flatMap(spentInfo -> getTx(spentInfo.getTxId()));
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
                .flatMap(e -> getTx(e).stream())
                .mapToLong(tx -> tx.getLockupOutput().getValue())
                .sum();
    }

    public long getTotalAmountOfInvalidatedBsq() {
        return getUnorderedTxStream().mapToLong(Tx::getInvalidatedBsq).sum();
    }

    // Contains burnt fee and invalidated bsq due invalid txs
    public long getTotalAmountOfBurntBsq() {
        return getUnorderedTxStream().mapToLong(Tx::getBurntBsq).sum();
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
                    // but it's probably better to stick to the rules that confiscation can only happen before lock time
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
        assertDaoStateChange();
        log.warn("TxId {} added to confiscatedLockupTxIdList.", lockupTxId);
        daoState.getConfiscatedLockupTxList().add(lockupTxId);
    }

    public boolean isConfiscatedOutput(TxOutputKey txOutputKey) {
        if (isLockupOutput(txOutputKey))
            return isConfiscatedLockupTxOutput(txOutputKey.getTxId());
        else if (isUnspentUnlockOutput(txOutputKey))
            return isConfiscatedUnlockTxOutput(txOutputKey.getTxId());
        return false;
    }

    public boolean isConfiscatedLockupTxOutput(String lockupTxId) {
        return daoState.getConfiscatedLockupTxList().contains(lockupTxId);
    }

    public boolean isConfiscatedUnlockTxOutput(String unlockTxId) {
        return getLockupTxFromUnlockTxId(unlockTxId).
                map(lockupTx -> isConfiscatedLockupTxOutput(lockupTx.getId())).
                orElse(false);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Param
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void setNewParam(int blockHeight, Param param, String paramValue) {
        assertDaoStateChange();
        List<ParamChange> paramChangeList = daoState.getParamChangeList();
        getStartHeightOfNextCycle(blockHeight)
                .ifPresent(heightOfNewCycle -> {
                    ParamChange paramChange = new ParamChange(param.name(), paramValue, heightOfNewCycle);
                    paramChangeList.add(paramChange);
                    // Addition with older height should not be possible but to ensure correct sorting lets run a sort.
                    paramChangeList.sort(Comparator.comparingInt(ParamChange::getActivationHeight));
                });
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

    public List<Coin> getParamChangeList(Param param) {
        List<Coin> values = new ArrayList<>();
        for (ParamChange paramChange : daoState.getParamChangeList()) {
            if (paramChange.getParamName().equals(param.name())) {
                values.add(getParamValueAsCoin(param, paramChange.getValue()));
            }
        }
        return values;
    }

    public Coin getParamValueAsCoin(Param param, String paramValue) {
        return bsqFormatter.parseParamValueToCoin(param, paramValue);
    }

    public double getParamValueAsPercentDouble(String paramValue) {
        return ParsingUtils.parsePercentStringToDouble(paramValue);
    }

    public int getParamValueAsBlock(String paramValue) {
        return Integer.parseInt(paramValue);
    }

    public Coin getParamValueAsCoin(Param param, int blockHeight) {
        return getParamValueAsCoin(param, getParamValue(param, blockHeight));
    }

    public double getParamValueAsPercentDouble(Param param, int blockHeight) {
        return getParamValueAsPercentDouble(getParamValue(param, blockHeight));
    }

    public int getParamValueAsBlock(Param param, int blockHeight) {
        return getParamValueAsBlock(getParamValue(param, blockHeight));
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // SpentInfo
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void setSpentInfo(TxOutputKey txOutputKey, SpentInfo spentInfo) {
        assertDaoStateChange();
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

    public void addEvaluatedProposalSet(Set<EvaluatedProposal> evaluatedProposals) {
        assertDaoStateChange();

        evaluatedProposals.stream()
                .filter(e -> !daoState.getEvaluatedProposalList().contains(e))
                .forEach(daoState.getEvaluatedProposalList()::add);

        // We need deterministic order for the hash chain
        daoState.getEvaluatedProposalList().sort(Comparator.comparing(EvaluatedProposal::getProposalTxId));
    }

    public List<DecryptedBallotsWithMerits> getDecryptedBallotsWithMeritsList() {
        return daoState.getDecryptedBallotsWithMeritsList();
    }

    public void addDecryptedBallotsWithMeritsSet(Set<DecryptedBallotsWithMerits> decryptedBallotsWithMeritsSet) {
        assertDaoStateChange();

        decryptedBallotsWithMeritsSet.stream()
                .filter(e -> !daoState.getDecryptedBallotsWithMeritsList().contains(e))
                .forEach(daoState.getDecryptedBallotsWithMeritsList()::add);

        // We need deterministic order for the hash chain
        daoState.getDecryptedBallotsWithMeritsList().sort(Comparator.comparing(DecryptedBallotsWithMerits::getBlindVoteTxId));
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

    public void addDaoStateListener(DaoStateListener listener) {
        daoStateListeners.add(listener);
    }

    public void removeDaoStateListener(DaoStateListener listener) {
        daoStateListeners.remove(listener);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Utils
    ///////////////////////////////////////////////////////////////////////////////////////////

    public String daoStateToString() {
        return daoState.toString();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Private
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void assertDaoStateChange() {
        if (!allowDaoStateChange)
            throw new RuntimeException("We got a call which would change the daoState outside of the allowed event phase");
    }
}

