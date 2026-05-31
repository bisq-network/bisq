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
import bisq.core.encoding.canonical.Canonical;
import bisq.core.encoding.canonical.CanonicalEncoder;
import bisq.core.encoding.canonical.CanonicalSchema;
import bisq.core.encoding.canonical.TreeMapOrderMapEntryIterator;

import bisq.common.proto.ProtobufferRuntimeException;
import bisq.common.proto.network.NetworkPayload;
import bisq.common.proto.persistable.PersistablePayload;
import bisq.common.util.ExtraDataMapValidator;

import java.util.Collections;
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
    // Canonical
    ///////////////////////////////////////////////////////////////////////////////////////////

    private static final CanonicalSchema<CompensationProposal> COMPENSATION_PROPOSAL_SCHEMA =
            CanonicalSchema.<CompensationProposal>newBuilder("CompensationProposal")
                    .int64(1, "requested_bsq", proposal -> proposal.getRequestedBsq().value)
                    .string(2, "bsq_address", CompensationProposal::getBsqAddress)
                    .build();
    private static final CanonicalSchema<ReimbursementProposal> REIMBURSEMENT_PROPOSAL_SCHEMA =
            CanonicalSchema.<ReimbursementProposal>newBuilder("ReimbursementProposal")
                    .int64(1, "requested_bsq", proposal -> proposal.getRequestedBsq().value)
                    .string(2, "bsq_address", ReimbursementProposal::getBsqAddress)
                    .build();
    private static final CanonicalSchema<ChangeParamProposal> CHANGE_PARAM_PROPOSAL_SCHEMA =
            CanonicalSchema.<ChangeParamProposal>newBuilder("ChangeParamProposal")
                    .string(1, "param", proposal -> proposal.getParam().name())
                    .string(2, "param_value", ChangeParamProposal::getParamValue)
                    .build();
    private static final CanonicalSchema<RoleProposal> ROLE_PROPOSAL_SCHEMA =
            CanonicalSchema.<RoleProposal>newBuilder("RoleProposal")
                    .compose(1, "role", RoleProposal::getRole, Role.SCHEMA)
                    .int64(2, "required_bond_unit", RoleProposal::getRequiredBondUnit)
                    .int32(3, "unlock_time", RoleProposal::getUnlockTime)
                    .build();
    private static final CanonicalSchema<ConfiscateBondProposal> CONFISCATE_BOND_PROPOSAL_SCHEMA =
            CanonicalSchema.<ConfiscateBondProposal>newBuilder("ConfiscateBondProposal")
                    .string(1, "lockup_tx_id", ConfiscateBondProposal::getLockupTxId)
                    .build();
    private static final CanonicalSchema<GenericProposal> GENERIC_PROPOSAL_SCHEMA =
            CanonicalSchema.<GenericProposal>newBuilder("GenericProposal").build();
    private static final CanonicalSchema<RemoveAssetProposal> REMOVE_ASSET_PROPOSAL_SCHEMA =
            CanonicalSchema.<RemoveAssetProposal>newBuilder("RemoveAssetProposal")
                    .string(1, "ticker_symbol", RemoveAssetProposal::getTickerSymbol)
                    .build();

    public static final CanonicalSchema<Proposal> SCHEMA = CanonicalSchema.<Proposal>newBuilder("Proposal")
            .string(1, "name", Proposal::getName)
            .string(2, "link", Proposal::getLink)
            .uint32(3, "version", proposal -> proposal.version)
            .int64(4, "creation_date", Proposal::getCreationDate)
            .string(5, "tx_id", Proposal::getTxId)
            .extend(6, "compensation_proposal", Proposal::getCompensationProposal, COMPENSATION_PROPOSAL_SCHEMA)
            .extend(7, "reimbursement_proposal", Proposal::getReimbursementProposal, REIMBURSEMENT_PROPOSAL_SCHEMA)
            .extend(8, "change_param_proposal", Proposal::getChangeParamProposal, CHANGE_PARAM_PROPOSAL_SCHEMA)
            .extend(9, "role_proposal", Proposal::getRoleProposal, ROLE_PROPOSAL_SCHEMA)
            .extend(10, "confiscate_bond_proposal", Proposal::getConfiscateBondProposal, CONFISCATE_BOND_PROPOSAL_SCHEMA)
            .extend(11, "generic_proposal", Proposal::getGenericProposal, GENERIC_PROPOSAL_SCHEMA)
            .extend(12, "remove_asset_proposal", Proposal::getRemoveAssetProposal, REMOVE_ASSET_PROPOSAL_SCHEMA)
            .mapStringToString(20,
                    "extra_data",
                    Proposal::getExtraDataMapForCanonical,
                    TreeMapOrderMapEntryIterator.naturalOrder())
            .build();

    @Override
    public byte[] encodeCanonical(CanonicalEncoder canonicalEncoder) {
        return canonicalEncoder.encode(this, SCHEMA);
    }


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

    private Map<String, String> getExtraDataMapForCanonical() {
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
