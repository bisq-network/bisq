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

import bisq.core.dao.state.blockchain.Block;
import bisq.core.dao.state.blockchain.SpentInfo;
import bisq.core.dao.state.blockchain.TxOutput;
import bisq.core.dao.state.blockchain.TxOutputKey;
import bisq.core.dao.state.governance.Issuance;
import bisq.core.dao.state.governance.ParamChange;
import bisq.core.dao.state.period.Cycle;

import bisq.common.proto.persistable.PersistableEnvelope;

import io.bisq.generated.protobuffer.PB;

import com.google.protobuf.Message;

import javax.inject.Inject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;


/**
 * Root class for mutable state of the DAO.
 * Holds both blockchain data as well as data derived from the governance process (voting).
 */
@Slf4j
public class BsqState implements PersistableEnvelope {

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Fields
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Getter
    private int chainHeight;
    @Getter
    private final LinkedList<Block> blocks;
    @Getter
    private final LinkedList<Cycle> cycles;

    // Those maps represent mutual data which can get changed at parsing a transaction
    @Getter
    private final Map<TxOutputKey, TxOutput> unspentTxOutputMap;
    @Getter
    private final Map<TxOutputKey, TxOutput> nonBsqTxOutputMap;
    @Getter
    private final Map<TxOutputKey, SpentInfo> spentInfoMap;

    // Those maps are related to state change triggered by voting
    @Getter
    private final Map<TxOutputKey, TxOutput> confiscatedTxOutputMap;
    @Getter
    private final Map<String, Issuance> issuanceMap; // key is txId
    @Getter
    private final List<ParamChange> paramChangeList;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    public BsqState() {
        this(0,
                new LinkedList<>(),
                new LinkedList<>(),
                new HashMap<>(),
                new HashMap<>(),
                new HashMap<>(),
                new HashMap<>(),
                new HashMap<>(),
                new ArrayList<>()
        );
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // PROTO BUFFER
    ///////////////////////////////////////////////////////////////////////////////////////////

    private BsqState(int chainHeight,
                     LinkedList<Block> blocks,
                     LinkedList<Cycle> cycles,
                     Map<TxOutputKey, TxOutput> unspentTxOutputMap,
                     Map<TxOutputKey, TxOutput> nonBsqTxOutputMap,
                     Map<TxOutputKey, SpentInfo> spentInfoMap,
                     Map<TxOutputKey, TxOutput> confiscatedTxOutputMap,
                     Map<String, Issuance> issuanceMap,
                     List<ParamChange> paramChangeList) {
        this.chainHeight = chainHeight;
        this.blocks = blocks;
        this.cycles = cycles;

        this.unspentTxOutputMap = unspentTxOutputMap;
        this.nonBsqTxOutputMap = nonBsqTxOutputMap;
        this.spentInfoMap = spentInfoMap;

        this.confiscatedTxOutputMap = confiscatedTxOutputMap;
        this.issuanceMap = issuanceMap;
        this.paramChangeList = paramChangeList;
    }

    @Override
    public Message toProtoMessage() {
        return PB.PersistableEnvelope.newBuilder().setBsqState(getBsqStateBuilder()).build();
    }

    private PB.BsqState.Builder getBsqStateBuilder() {
        final PB.BsqState.Builder builder = PB.BsqState.newBuilder();
        builder.setChainHeight(chainHeight)
                .addAllBlocks(blocks.stream().map(Block::toProtoMessage).collect(Collectors.toList()))
                .addAllCycles(cycles.stream().map(Cycle::toProtoMessage).collect(Collectors.toList()))
                .putAllUnspentTxOutputMap(unspentTxOutputMap.entrySet().stream()
                        .collect(Collectors.toMap(e -> e.getKey().toString(), e -> e.getValue().toProtoMessage())))
                .putAllUnspentTxOutputMap(nonBsqTxOutputMap.entrySet().stream()
                        .collect(Collectors.toMap(e -> e.getKey().toString(), e -> e.getValue().toProtoMessage())))
                .putAllSpentInfoMap(spentInfoMap.entrySet().stream()
                        .collect(Collectors.toMap(e -> e.getKey().toString(), entry -> entry.getValue().toProtoMessage())))
                .putAllUnspentTxOutputMap(confiscatedTxOutputMap.entrySet().stream()
                        .collect(Collectors.toMap(e -> e.getKey().toString(), e -> e.getValue().toProtoMessage())))
                .putAllIssuanceMap(issuanceMap.entrySet().stream()
                        .collect(Collectors.toMap(Map.Entry::getKey, entry -> entry.getValue().toProtoMessage())))
                .addAllParamChangeList(paramChangeList.stream().map(ParamChange::toProtoMessage).collect(Collectors.toList()));
        return builder;
    }

    public static PersistableEnvelope fromProto(PB.BsqState proto) {
        LinkedList<Block> blocks = proto.getBlocksList().stream()
                .map(Block::fromProto)
                .collect(Collectors.toCollection(LinkedList::new));
        final LinkedList<Cycle> cycles = proto.getCyclesList().stream()
                .map(Cycle::fromProto).collect(Collectors.toCollection(LinkedList::new));
        Map<TxOutputKey, TxOutput> unspentTxOutputMap = proto.getUnspentTxOutputMapMap().entrySet().stream()
                .collect(Collectors.toMap(e -> TxOutputKey.getKeyFromString(e.getKey()), e -> TxOutput.fromProto(e.getValue())));
        Map<TxOutputKey, TxOutput> nonBsqTxOutputMap = proto.getNonBsqTxOutputMapMap().entrySet().stream()
                .collect(Collectors.toMap(e -> TxOutputKey.getKeyFromString(e.getKey()), e -> TxOutput.fromProto(e.getValue())));
        Map<TxOutputKey, SpentInfo> spentInfoMap = proto.getSpentInfoMapMap().entrySet().stream()
                .collect(Collectors.toMap(e -> TxOutputKey.getKeyFromString(e.getKey()), e -> SpentInfo.fromProto(e.getValue())));
        Map<TxOutputKey, TxOutput> confiscatedTxOutputMap = proto.getConfiscatedTxOutputMapMap().entrySet().stream()
                .collect(Collectors.toMap(e -> TxOutputKey.getKeyFromString(e.getKey()), e -> TxOutput.fromProto(e.getValue())));
        Map<String, Issuance> issuanceMap = proto.getIssuanceMapMap().entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, e -> Issuance.fromProto(e.getValue())));
        final List<ParamChange> paramChangeList = proto.getParamChangeListList().stream()
                .map(ParamChange::fromProto).collect(Collectors.toCollection(ArrayList::new));
        return new BsqState(proto.getChainHeight(),
                blocks,
                cycles,
                unspentTxOutputMap,
                nonBsqTxOutputMap,
                spentInfoMap,
                confiscatedTxOutputMap,
                issuanceMap,
                paramChangeList);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Package scope access
    ///////////////////////////////////////////////////////////////////////////////////////////

    void setChainHeight(int chainHeight) {
        this.chainHeight = chainHeight;
    }

    BsqState getClone() {
        return (BsqState) BsqState.fromProto(getBsqStateBuilder().build());
    }

    BsqState getClone(BsqState snapshotCandidate) {
        return (BsqState) BsqState.fromProto(snapshotCandidate.getBsqStateBuilder().build());
    }
}
