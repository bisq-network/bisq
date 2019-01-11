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
import bisq.core.dao.state.model.blockchain.TxOutput;
import bisq.core.dao.state.model.blockchain.TxOutputKey;
import bisq.core.dao.state.model.governance.Cycle;
import bisq.core.dao.state.model.governance.DecryptedBallotsWithMerits;
import bisq.core.dao.state.model.governance.EvaluatedProposal;
import bisq.core.dao.state.model.governance.Issuance;
import bisq.core.dao.state.model.governance.ParamChange;

import bisq.common.proto.persistable.PersistablePayload;

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
 * <p>
 * One BSQ block with empty txs adds 152 bytes which results in about 8 MB/year
 */
@Slf4j
public class DaoState implements PersistablePayload {

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Static
    ///////////////////////////////////////////////////////////////////////////////////////////

    public static DaoState getClone(DaoState daoState) {
        return DaoState.fromProto(daoState.getBsqStateBuilder().build());
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Fields
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Getter
    private int chainHeight; // Is set initially to genesis height
    @Getter
    private final LinkedList<Block> blocks;
    @Getter
    private final LinkedList<Cycle> cycles;

    // These maps represent mutual data which can get changed at parsing a transaction
    @Getter
    private final Map<TxOutputKey, TxOutput> unspentTxOutputMap;
    @Getter
    private final Map<TxOutputKey, TxOutput> nonBsqTxOutputMap;
    @Getter
    private final Map<TxOutputKey, SpentInfo> spentInfoMap;

    // These maps are related to state change triggered by voting
    @Getter
    private final List<String> confiscatedLockupTxList;
    @Getter
    private final Map<String, Issuance> issuanceMap; // key is txId
    @Getter
    private final List<ParamChange> paramChangeList;

    // Vote result data
    // All evaluated proposals which get added at the result phase
    @Getter
    private final List<EvaluatedProposal> evaluatedProposalList;
    // All voting data which get added at the result phase
    @Getter
    private final List<DecryptedBallotsWithMerits> decryptedBallotsWithMeritsList;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    public DaoState() {
        this(0,
                new LinkedList<>(),
                new LinkedList<>(),
                new HashMap<>(),
                new HashMap<>(),
                new HashMap<>(),
                new ArrayList<>(),
                new HashMap<>(),
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
                     Map<TxOutputKey, TxOutput> unspentTxOutputMap,
                     Map<TxOutputKey, TxOutput> nonBsqTxOutputMap,
                     Map<TxOutputKey, SpentInfo> spentInfoMap,
                     List<String> confiscatedLockupTxList,
                     Map<String, Issuance> issuanceMap,
                     List<ParamChange> paramChangeList,
                     List<EvaluatedProposal> evaluatedProposalList,
                     List<DecryptedBallotsWithMerits> decryptedBallotsWithMeritsList) {
        this.chainHeight = chainHeight;
        this.blocks = blocks;
        this.cycles = cycles;

        this.unspentTxOutputMap = unspentTxOutputMap;
        this.nonBsqTxOutputMap = nonBsqTxOutputMap;
        this.spentInfoMap = spentInfoMap;

        this.confiscatedLockupTxList = confiscatedLockupTxList;
        this.issuanceMap = issuanceMap;
        this.paramChangeList = paramChangeList;
        this.evaluatedProposalList = evaluatedProposalList;
        this.decryptedBallotsWithMeritsList = decryptedBallotsWithMeritsList;
    }

    @Override
    public Message toProtoMessage() {
        return getBsqStateBuilder().build();
    }

    public PB.BsqState.Builder getBsqStateBuilder() {
        final PB.BsqState.Builder builder = PB.BsqState.newBuilder();
        builder.setChainHeight(chainHeight)
                .addAllBlocks(blocks.stream().map(Block::toProtoMessage).collect(Collectors.toList()))
                .addAllCycles(cycles.stream().map(Cycle::toProtoMessage).collect(Collectors.toList()))
                .putAllUnspentTxOutputMap(unspentTxOutputMap.entrySet().stream()
                        .collect(Collectors.toMap(e -> e.getKey().toString(), e -> e.getValue().toProtoMessage())))
                .putAllNonBsqTxOutputMap(nonBsqTxOutputMap.entrySet().stream()
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

    public static DaoState fromProto(PB.BsqState proto) {
        LinkedList<Block> blocks = proto.getBlocksList().stream()
                .map(Block::fromProto)
                .collect(Collectors.toCollection(LinkedList::new));
        LinkedList<Cycle> cycles = proto.getCyclesList().stream()
                .map(Cycle::fromProto).collect(Collectors.toCollection(LinkedList::new));
        Map<TxOutputKey, TxOutput> unspentTxOutputMap = proto.getUnspentTxOutputMapMap().entrySet().stream()
                .collect(Collectors.toMap(e -> TxOutputKey.getKeyFromString(e.getKey()), e -> TxOutput.fromProto(e.getValue())));
        Map<TxOutputKey, TxOutput> nonBsqTxOutputMap = proto.getNonBsqTxOutputMapMap().entrySet().stream()
                .collect(Collectors.toMap(e -> TxOutputKey.getKeyFromString(e.getKey()), e -> TxOutput.fromProto(e.getValue())));
        Map<TxOutputKey, SpentInfo> spentInfoMap = proto.getSpentInfoMapMap().entrySet().stream()
                .collect(Collectors.toMap(e -> TxOutputKey.getKeyFromString(e.getKey()), e -> SpentInfo.fromProto(e.getValue())));
        List<String> confiscatedLockupTxList = new ArrayList<>(proto.getConfiscatedLockupTxListList());
        Map<String, Issuance> issuanceMap = proto.getIssuanceMapMap().entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, e -> Issuance.fromProto(e.getValue())));
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
                nonBsqTxOutputMap,
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
}
