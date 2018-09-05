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

package bisq.core.dao.governance.proposal;

import bisq.core.dao.governance.ConsensusCritical;
import bisq.core.dao.governance.proposal.compensation.CompensationProposal;
import bisq.core.dao.governance.proposal.confiscatebond.ConfiscateBondProposal;
import bisq.core.dao.governance.proposal.param.ChangeParamProposal;
import bisq.core.dao.governance.proposal.role.BondedRoleProposal;
import bisq.core.dao.state.blockchain.TxType;
import bisq.core.dao.state.governance.Param;

import bisq.common.proto.ProtobufferRuntimeException;
import bisq.common.proto.network.NetworkPayload;
import bisq.common.proto.persistable.PersistablePayload;

import io.bisq.generated.protobuffer.PB;

import java.util.Date;
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
public abstract class Proposal implements PersistablePayload, NetworkPayload, ConsensusCritical {
    protected final String name;
    protected final String link;
    protected final byte version;
    protected final long creationDate;
    protected final String txId;

    protected Proposal(String name,
                       String link,
                       byte version,
                       long creationDate,
                       @Nullable String txId) {
        this.name = name;
        this.link = link;
        this.version = version;
        this.creationDate = creationDate;
        this.txId = txId;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // PROTO BUFFER
    ///////////////////////////////////////////////////////////////////////////////////////////

    public PB.Proposal.Builder getProposalBuilder() {
        final PB.Proposal.Builder builder = PB.Proposal.newBuilder()
                .setName(name)
                .setLink(link)
                .setVersion(version)
                .setCreationDate(creationDate);
        Optional.ofNullable(txId).ifPresent(builder::setTxId);
        return builder;
    }

    @Override
    public PB.Proposal toProtoMessage() {
        return getProposalBuilder().build();
    }

    public static Proposal fromProto(PB.Proposal proto) {
        switch (proto.getMessageCase()) {
            case COMPENSATION_PROPOSAL:
                return CompensationProposal.fromProto(proto);
            case GENERIC_PROPOSAL:
                throw new ProtobufferRuntimeException("Not implemented yet: " + proto);
            case CHANGE_PARAM_PROPOSAL:
                return ChangeParamProposal.fromProto(proto);
            case REMOVE_ALTCOIN_PROPOSAL:
                throw new ProtobufferRuntimeException("Not implemented yet: " + proto);
            case CONFISCATE_BOND_PROPOSAL:
                return ConfiscateBondProposal.fromProto(proto);
            case BONDED_ROLE_PROPOSAL:
                return BondedRoleProposal.fromProto(proto);
            case MESSAGE_NOT_SET:
            default:
                throw new ProtobufferRuntimeException("Unknown message case: " + proto);
        }
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Utils
    ///////////////////////////////////////////////////////////////////////////////////////////

    public Date getCreationDate() {
        return new Date(creationDate);
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
                "\n     uid='" + txId + '\'' +
                ",\n     name='" + name + '\'' +
                ",\n     link='" + link + '\'' +
                ",\n     txId='" + txId + '\'' +
                ",\n     version=" + version +
                ",\n     creationDate=" + new Date(creationDate) +
                "\n}";
    }
}
