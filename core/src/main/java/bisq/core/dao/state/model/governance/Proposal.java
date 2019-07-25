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

package bisq.core.dao.state.model.governance;

import bisq.core.dao.governance.ConsensusCritical;
import bisq.core.dao.governance.param.Param;
import bisq.core.dao.governance.proposal.ProposalType;
import bisq.core.dao.state.model.ImmutableDaoStateModel;
import bisq.core.dao.state.model.blockchain.TxType;

import bisq.common.proto.ProtobufferRuntimeException;
import bisq.common.proto.network.NetworkPayload;
import bisq.common.proto.persistable.PersistablePayload;
import bisq.common.util.ExtraDataMapValidator;

import java.util.Date;
import java.util.Map;
import java.util.Optional;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

/**
 * Base class for proposals.
 */
@Immutable
@Slf4j
@Getter
@EqualsAndHashCode
public abstract class Proposal implements PersistablePayload, NetworkPayload, ConsensusCritical, ImmutableDaoStateModel {
    protected final String name;
    protected final String link;
    protected final byte version;
    protected final long creationDate;
    @Nullable
    protected final String txId;

    // This hash map allows addition of data in future versions without breaking consensus
    @Nullable
    protected final Map<String, String> extraDataMap;

    protected Proposal(String name,
                       String link,
                       byte version,
                       long creationDate,
                       @Nullable String txId,
                       @Nullable Map<String, String> extraDataMap) {
        this.name = name;
        this.link = link;
        this.version = version;
        this.creationDate = creationDate;
        this.txId = txId;
        this.extraDataMap = ExtraDataMapValidator.getValidatedExtraDataMap(extraDataMap);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // PROTO BUFFER
    ///////////////////////////////////////////////////////////////////////////////////////////

    public protobuf.Proposal.Builder getProposalBuilder() {
        final protobuf.Proposal.Builder builder = protobuf.Proposal.newBuilder()
                .setName(name)
                .setLink(link)
                .setVersion(version)
                .setCreationDate(creationDate);
        Optional.ofNullable(extraDataMap).ifPresent(builder::putAllExtraData);
        Optional.ofNullable(txId).ifPresent(builder::setTxId);
        return builder;
    }

    @Override
    public protobuf.Proposal toProtoMessage() {
        return getProposalBuilder().build();
    }

    public static Proposal fromProto(protobuf.Proposal proto) {
        switch (proto.getMessageCase()) {
            case COMPENSATION_PROPOSAL:
                return CompensationProposal.fromProto(proto);
            case REIMBURSEMENT_PROPOSAL:
                return ReimbursementProposal.fromProto(proto);
            case CHANGE_PARAM_PROPOSAL:
                return ChangeParamProposal.fromProto(proto);
            case ROLE_PROPOSAL:
                return RoleProposal.fromProto(proto);
            case CONFISCATE_BOND_PROPOSAL:
                return ConfiscateBondProposal.fromProto(proto);
            case GENERIC_PROPOSAL:
                return GenericProposal.fromProto(proto);
            case REMOVE_ASSET_PROPOSAL:
                return RemoveAssetProposal.fromProto(proto);
            case MESSAGE_NOT_SET:
            default:
                throw new ProtobufferRuntimeException("Unknown message case: " + proto);
        }
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Utils
    ///////////////////////////////////////////////////////////////////////////////////////////

    public Date getCreationDateAsDate() {
        return new Date(getCreationDate());
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Abstract
    ///////////////////////////////////////////////////////////////////////////////////////////

    public abstract Proposal cloneProposalAndAddTxId(String txId);

    public abstract ProposalType getType();

    public abstract TxType getTxType();

    public abstract Param getQuorumParam();

    public abstract Param getThresholdParam();

    @Override
    public String toString() {
        return "Proposal{" +
                "\n     txId='" + txId + '\'' +
                ",\n     name='" + name + '\'' +
                ",\n     link='" + link + '\'' +
                ",\n     txId='" + txId + '\'' +
                ",\n     version=" + version +
                ",\n     creationDate=" + new Date(creationDate) +
                "\n}";
    }
}
