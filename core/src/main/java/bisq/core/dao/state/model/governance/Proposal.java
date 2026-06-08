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
import bisq.common.encoding.canonical.Canonical;
import bisq.common.encoding.canonical.CanonicalSchema;
import bisq.common.encoding.canonical.TreeMapIterator;

import bisq.common.proto.ProtobufferRuntimeException;
import bisq.common.proto.network.NetworkPayload;
import bisq.common.proto.persistable.PersistablePayload;
import bisq.common.util.ExtraDataMapValidator;

import java.util.Collections;
import java.util.Date;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

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
public abstract class Proposal implements PersistablePayload, NetworkPayload, ConsensusCritical, ImmutableDaoStateModel,
        Canonical {
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
                       @Nullable TreeMap<String, String> extraDataMap) {
        this.name = name;
        this.link = link;
        this.version = version;
        this.creationDate = creationDate;
        this.txId = txId;

        Map<String, String> validatedExtraDataMap = ExtraDataMapValidator.getValidatedExtraDataMap(extraDataMap);
        this.extraDataMap = validatedExtraDataMap == null ? null : new TreeMap<>(validatedExtraDataMap);
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
    // Canonical
    ///////////////////////////////////////////////////////////////////////////////////////////

    protected static <T extends Proposal> CanonicalSchema.Builder<T> getBaseProposalSchemaBuilder() {
        return CanonicalSchema.<T>newBuilder()
                .string(1, Proposal::getName)
                .string(2, Proposal::getLink)
                .uint32(3, proposal -> proposal.version)
                .int64(4, Proposal::getCreationDate)
                .string(5, Proposal::getTxId);
    }

    // Proposal is composed through this abstract base type by Ballot and ProposalVoteResult.
    // The canonical schema must therefore describe every concrete proposal variant. The subtype
    // getters below return null for non-matching variants, and null fields are omitted by the
    // encoder, so only the active proposal extension is encoded.
    public static CanonicalSchema.Builder<Proposal> getProposalSchemaBuilder() {
        return Proposal.<Proposal>getBaseProposalSchemaBuilder()
                .extend(6, Proposal::getCompensationProposal, CompensationProposal.EXTENSION_SCHEMA)
                .extend(7, Proposal::getReimbursementProposal, ReimbursementProposal.EXTENSION_SCHEMA)
                .extend(8, Proposal::getChangeParamProposal, ChangeParamProposal.EXTENSION_SCHEMA)
                .extend(9, Proposal::getRoleProposal, RoleProposal.EXTENSION_SCHEMA)
                .extend(10, Proposal::getConfiscateBondProposal, ConfiscateBondProposal.EXTENSION_SCHEMA)
                .extend(11, Proposal::getGenericProposal, GenericProposal.EXTENSION_SCHEMA)
                .extend(12, Proposal::getRemoveAssetProposal, RemoveAssetProposal.EXTENSION_SCHEMA)
                // extra_data keeps protobuf field 20 and must stay after proposal subtype
                // extensions, which occupy fields 6 through 12.
                .mapStringToString(20,
                        Proposal::getExtraDataMapForCanonical,
                        TreeMapIterator.naturalOrder());
    }

    // These methods adapt the runtime subtype into fixed schema fields. A null result means the
    // field is not the active proposal variant and must be skipped by canonical encoding.
    @Nullable
    private static CompensationProposal getCompensationProposal(Proposal proposal) {
        return proposal instanceof CompensationProposal ? (CompensationProposal) proposal : null;
    }

    @Nullable
    private static ReimbursementProposal getReimbursementProposal(Proposal proposal) {
        return proposal instanceof ReimbursementProposal ? (ReimbursementProposal) proposal : null;
    }

    @Nullable
    private static ChangeParamProposal getChangeParamProposal(Proposal proposal) {
        return proposal instanceof ChangeParamProposal ? (ChangeParamProposal) proposal : null;
    }

    @Nullable
    private static RoleProposal getRoleProposal(Proposal proposal) {
        return proposal instanceof RoleProposal ? (RoleProposal) proposal : null;
    }

    @Nullable
    private static ConfiscateBondProposal getConfiscateBondProposal(Proposal proposal) {
        return proposal instanceof ConfiscateBondProposal ? (ConfiscateBondProposal) proposal : null;
    }

    @Nullable
    private static GenericProposal getGenericProposal(Proposal proposal) {
        return proposal instanceof GenericProposal ? (GenericProposal) proposal : null;
    }

    @Nullable
    private static RemoveAssetProposal getRemoveAssetProposal(Proposal proposal) {
        return proposal instanceof RemoveAssetProposal ? (RemoveAssetProposal) proposal : null;
    }

    protected Map<String, String> getExtraDataMapForCanonical() {
        return extraDataMap == null ? Collections.emptyMap() : extraDataMap;
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
                ",\n     extraDataMap='" + extraDataMap + '\'' +
                ",\n     version=" + version +
                ",\n     creationDate=" + new Date(creationDate) +
                "\n}";
    }
}
