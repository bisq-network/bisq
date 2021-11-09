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

package bisq.core.dao.state.model;

import bisq.core.dao.state.model.blockchain.Block;
import bisq.core.dao.state.model.blockchain.SpentInfo;
import bisq.core.dao.state.model.blockchain.Tx;
import bisq.core.dao.state.model.blockchain.TxOutput;
import bisq.core.dao.state.model.blockchain.TxOutputKey;
import bisq.core.dao.state.model.blockchain.TxOutputType;
import bisq.core.dao.state.model.governance.Cycle;
import bisq.core.dao.state.model.governance.DecryptedBallotsWithMerits;
import bisq.core.dao.state.model.governance.EvaluatedProposal;
import bisq.core.dao.state.model.governance.Issuance;
import bisq.core.dao.state.model.governance.ParamChange;

import bisq.common.proto.persistable.PersistablePayload;
import bisq.common.util.JsonExclude;

import com.google.protobuf.Message;

import javax.inject.Inject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.stream.Collectors;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

/**
 * Root class for mutable state of the DAO.
 * Holds both blockchain data as well as data derived from the governance process (voting).
 * <p>
 * One BSQ block with empty txs adds 152 bytes which results in about 8 MB/year
 *
 * For supporting the hashChain we need to ensure deterministic sorting behaviour of all collections so we use a
 * TreeMap which is sorted by the key.
 */
@Slf4j
public class DaoState implements PersistablePayload {

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Static
    ///////////////////////////////////////////////////////////////////////////////////////////

    public static DaoState getClone(DaoState daoState) {
        return DaoState.fromProto(daoState.getBsqStateBuilder().build());
    }

    public static protobuf.DaoState getBsqStateCloneExcludingBlocks(DaoState daoState) {
        return daoState.getBsqStateBuilderExcludingBlocks().build();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Fields
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Getter
    private int chainHeight; // Is set initially to genesis height

    // We override the getter so callers can't modify the list without also updating
    // the block caches and indices below
    private final LinkedList<Block> blocks;
    @Getter
    private final LinkedList<Cycle> cycles;

    // These maps represent mutual data which can get changed at parsing a transaction
    // We use TreeMaps instead of HashMaps because we need deterministic sorting of the maps for the hashChains
    // used for the DAO monitor.
    @Getter
    private final TreeMap<TxOutputKey, TxOutput> unspentTxOutputMap;
    @Getter
    private final TreeMap<TxOutputKey, SpentInfo> spentInfoMap;

    // These maps are related to state change triggered by voting
    @Getter
    private final List<String> confiscatedLockupTxList;
    @Getter
    private final TreeMap<String, Issuance> issuanceMap; // key is txId
    @Getter
    private final List<ParamChange> paramChangeList;

    // Vote result data
    // All evaluated proposals which get added at the result phase
    @Getter
    private final List<EvaluatedProposal> evaluatedProposalList;
    // All voting data which get added at the result phase
    @Getter
    private final List<DecryptedBallotsWithMerits> decryptedBallotsWithMeritsList;

    // Transient data used only as an index - must be kept in sync with the block list
    @JsonExclude
    private transient final Map<String, Tx> txCache; // key is txId
    @JsonExclude
    private transient final Map<Integer, Block> blocksByHeight; // Blocks indexed by height
    @JsonExclude
    private transient final Map<TxOutputType, Set<TxOutput>> txOutputsByTxOutputType = new HashMap<>();


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    public DaoState() {
        this(0,
                new LinkedList<>(),
                new LinkedList<>(),
                new TreeMap<>(),
                new TreeMap<>(),
                new ArrayList<>(),
                new TreeMap<>(),
                new ArrayList<>(),
                new ArrayList<>(),
                new ArrayList<>()
        );
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // PROTO BUFFER
    ///////////////////////////////////////////////////////////////////////////////////////////

    private DaoState(int chainHeight,
                     LinkedList<Block> blocks,
                     LinkedList<Cycle> cycles,
                     TreeMap<TxOutputKey, TxOutput> unspentTxOutputMap,
                     TreeMap<TxOutputKey, SpentInfo> spentInfoMap,
                     List<String> confiscatedLockupTxList,
                     TreeMap<String, Issuance> issuanceMap,
                     List<ParamChange> paramChangeList,
                     List<EvaluatedProposal> evaluatedProposalList,
                     List<DecryptedBallotsWithMerits> decryptedBallotsWithMeritsList) {
        this.chainHeight = chainHeight;
        this.blocks = blocks;
        this.cycles = cycles;

        this.unspentTxOutputMap = unspentTxOutputMap;
        this.spentInfoMap = spentInfoMap;

        this.confiscatedLockupTxList = confiscatedLockupTxList;
        this.issuanceMap = issuanceMap;
        this.paramChangeList = paramChangeList;
        this.evaluatedProposalList = evaluatedProposalList;
        this.decryptedBallotsWithMeritsList = decryptedBallotsWithMeritsList;

        txCache = blocks.stream()
                .flatMap(block -> block.getTxs().stream())
                .peek(this::addToTxOutputsByTxOutputTypeMap)
                .collect(Collectors.toMap(Tx::getId, Function.identity(), (x, y) -> x, HashMap::new));

        blocksByHeight = blocks.stream()
                .collect(Collectors.toMap(Block::getHeight, Function.identity(), (x, y) -> x, HashMap::new));
    }

    @Override
    public Message toProtoMessage() {
        return getBsqStateBuilder().build();
    }

    public protobuf.DaoState.Builder getBsqStateBuilder() {
        return getBsqStateBuilderExcludingBlocks().addAllBlocks(blocks.stream()
                .map(Block::toProtoMessage)
                .collect(Collectors.toList()));
    }

    private protobuf.DaoState.Builder getBsqStateBuilderExcludingBlocks() {
        protobuf.DaoState.Builder builder = protobuf.DaoState.newBuilder();
        builder.setChainHeight(chainHeight)
                .addAllCycles(cycles.stream().map(Cycle::toProtoMessage).collect(Collectors.toList()))
                .putAllUnspentTxOutputMap(unspentTxOutputMap.entrySet().stream()
                        .collect(Collectors.toMap(e -> e.getKey().toString(), e -> e.getValue().toProtoMessage())))
                .putAllSpentInfoMap(spentInfoMap.entrySet().stream()
                        .collect(Collectors.toMap(e -> e.getKey().toString(), entry -> entry.getValue().toProtoMessage())))
                .addAllConfiscatedLockupTxList(confiscatedLockupTxList)
                .putAllIssuanceMap(issuanceMap.entrySet().stream()
                        .collect(Collectors.toMap(Map.Entry::getKey, entry -> entry.getValue().toProtoMessage())))
                .addAllParamChangeList(paramChangeList.stream().map(ParamChange::toProtoMessage).collect(Collectors.toList()))
                .addAllEvaluatedProposalList(evaluatedProposalList.stream().map(EvaluatedProposal::toProtoMessage).collect(Collectors.toList()))
                .addAllDecryptedBallotsWithMeritsList(decryptedBallotsWithMeritsList.stream().map(DecryptedBallotsWithMerits::toProtoMessage).collect(Collectors.toList()));
        return builder;
    }

    public static DaoState fromProto(protobuf.DaoState proto) {
        LinkedList<Block> blocks = proto.getBlocksList().stream()
                .map(Block::fromProto)
                .collect(Collectors.toCollection(LinkedList::new));
        return fromProto(proto, blocks);
    }

    public static DaoState fromProto(protobuf.DaoState proto, LinkedList<Block> blocks) {
        LinkedList<Cycle> cycles = proto.getCyclesList().stream()
                .map(Cycle::fromProto).collect(Collectors.toCollection(LinkedList::new));
        TreeMap<TxOutputKey, TxOutput> unspentTxOutputMap = new TreeMap<>(proto.getUnspentTxOutputMapMap().entrySet().stream()
                .collect(Collectors.toMap(e -> TxOutputKey.getKeyFromString(e.getKey()), e -> TxOutput.fromProto(e.getValue()))));
        TreeMap<TxOutputKey, SpentInfo> spentInfoMap = new TreeMap<>(proto.getSpentInfoMapMap().entrySet().stream()
                .collect(Collectors.toMap(e -> TxOutputKey.getKeyFromString(e.getKey()), e -> SpentInfo.fromProto(e.getValue()))));
        List<String> confiscatedLockupTxList = new ArrayList<>(proto.getConfiscatedLockupTxListList());
        TreeMap<String, Issuance> issuanceMap = new TreeMap<>(proto.getIssuanceMapMap().entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, e -> Issuance.fromProto(e.getValue()))));
        List<ParamChange> paramChangeList = proto.getParamChangeListList().stream()
                .map(ParamChange::fromProto).collect(Collectors.toCollection(ArrayList::new));
        List<EvaluatedProposal> evaluatedProposalList = proto.getEvaluatedProposalListList().stream()
                .map(EvaluatedProposal::fromProto).collect(Collectors.toCollection(ArrayList::new));
        List<DecryptedBallotsWithMerits> decryptedBallotsWithMeritsList = proto.getDecryptedBallotsWithMeritsListList().stream()
                .map(DecryptedBallotsWithMerits::fromProto).collect(Collectors.toCollection(ArrayList::new));
        return new DaoState(proto.getChainHeight(),
                blocks,
                cycles,
                unspentTxOutputMap,
                spentInfoMap,
                confiscatedLockupTxList,
                issuanceMap,
                paramChangeList,
                evaluatedProposalList,
                decryptedBallotsWithMeritsList);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // API
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void setChainHeight(int chainHeight) {
        this.chainHeight = chainHeight;
    }

    public byte[] getSerializedStateForHashChain() {
        // We only add last block as for the hash chain we include the prev. hash in the new hash so the state of the
        // earlier blocks is included in the hash. The past blocks cannot be changed anyway when a new block arrives.
        // Reorgs are handled by rebuilding the hash chain from last snapshot.
        // Using the full blocks list becomes quite heavy. 7000 blocks are
        // about 1.4 MB and creating the hash takes 30 sec. By using just the last block we reduce the time to 7 sec.
        return getBsqStateBuilderExcludingBlocks().addBlocks(getLastBlock().toProtoMessage()).build().toByteArray();
    }

    public void addToTxCache(Tx tx) {
        // We shouldn't get duplicate txIds, but use putIfAbsent instead of put for consistency with the map merge
        // function used in the constructor to initialise txCache (and to exactly match the pre-caching behaviour).
        txCache.putIfAbsent(tx.getId(), tx);

        addToTxOutputsByTxOutputTypeMap(tx);
    }

    public void setTxCache(Map<String, Tx> txCache) {
        this.txCache.clear();
        this.txCache.putAll(txCache);

        txOutputsByTxOutputType.clear();
        this.txCache.values().forEach(this::addToTxOutputsByTxOutputTypeMap);
    }

    private void addToTxOutputsByTxOutputTypeMap(Tx tx) {
        tx.getTxOutputs().forEach(txOutput -> {
            TxOutputType txOutputType = txOutput.getTxOutputType();
            txOutputsByTxOutputType.putIfAbsent(txOutputType, new HashSet<>());
            txOutputsByTxOutputType.get(txOutputType).add(txOutput);
        });
    }

    public Set<TxOutput> getTxOutputByTxOutputType(TxOutputType txOutputType) {
        if (txOutputsByTxOutputType.containsKey(txOutputType)) {
            return Collections.unmodifiableSet(txOutputsByTxOutputType.get(txOutputType));
        } else {
            return new HashSet<>();
        }
    }

    public Map<String, Tx> getTxCache() {
        return Collections.unmodifiableMap(txCache);
    }

    public Map<Integer, Block> getBlocksByHeight() {
        return Collections.unmodifiableMap(blocksByHeight);
    }

    /**
     * @return Unmodifiable view of the list of blocks. This prevents callers from
     * directly modifying the list. We need to do this to make sure the block list is only
     * modified together with the corresponding caches and indices.
     *
     * @see #addBlock(Block) to add a single block
     * @see #addBlocks(List) to add a list of blocks
     * @see #clearAndSetBlocks(List)  to replace existing blocks with a new list
     */
    public List<Block> getBlocks() {
        return Collections.unmodifiableList(blocks);
    }

    // Wrapper that directly accesses the LinkedList, such that we don't have to expose
    // the LinkedList
    public Block getLastBlock() {
        return blocks.getLast();
    }

    public void addBlock(Block block) {
        blocks.add(block);
        blocksByHeight.put(block.getHeight(), block);
    }

    public void addBlocks(List<Block> newBlocks) {
        newBlocks.forEach(this::addBlock);
    }

    /**
     * Clears the existing block list and caches, and repopulates them with the new list
     * @param newBlocks
     */
    public void clearAndSetBlocks(List<Block> newBlocks) {
        blocks.clear();
        blocksByHeight.clear();

        addBlocks(newBlocks);
    }

    @Override
    public String toString() {
        return "DaoState{" +
                "\n     chainHeight=" + chainHeight +
                ",\n     blocks=" + blocks +
                ",\n     cycles=" + cycles +
                ",\n     unspentTxOutputMap=" + unspentTxOutputMap +
                ",\n     spentInfoMap=" + spentInfoMap +
                ",\n     confiscatedLockupTxList=" + confiscatedLockupTxList +
                ",\n     issuanceMap=" + issuanceMap +
                ",\n     paramChangeList=" + paramChangeList +
                ",\n     evaluatedProposalList=" + evaluatedProposalList +
                ",\n     decryptedBallotsWithMeritsList=" + decryptedBallotsWithMeritsList +
                ",\n     txCache=" + txCache +
                ",\n     txOutputsByTxOutputType=" + txOutputsByTxOutputType +
                "\n}";
    }
}
