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

import bisq.core.dao.governance.param.Param;
import bisq.core.dao.governance.proposal.ProposalType;
import bisq.core.dao.state.model.ImmutableDaoStateModel;
import bisq.core.dao.state.model.blockchain.TxType;
import bisq.common.encoding.canonical.CanonicalEncoder;
import bisq.common.encoding.canonical.CanonicalSchema;
import bisq.common.encoding.canonical.TreeMapIterator;

import bisq.common.app.Version;
import bisq.common.util.CollectionUtils;

import java.util.Date;
import java.util.Objects;
import java.util.TreeMap;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

@Immutable
@Slf4j
@Getter
public final class ChangeParamProposal extends Proposal implements ImmutableDaoStateModel {
    private final Param param;
    private final String paramValue;

    public ChangeParamProposal(String name,
                               String link,
                               Param param,
                               String paramValue,
                               @Nullable TreeMap<String, String> extraDataMap) {
        this(name,
                link,
                param,
                paramValue,
                Version.PROPOSAL,
                new Date().getTime(),
                null,
                extraDataMap);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // PROTO BUFFER
    ///////////////////////////////////////////////////////////////////////////////////////////

    private ChangeParamProposal(String name,
                                String link,
                                Param param,
                                String paramValue,
                                byte version,
                                long creationDate,
                                String txId,
                                @Nullable TreeMap<String, String> extraDataMap) {
        super(name,
                link,
                version,
                creationDate,
                txId,
                extraDataMap);

        this.param = param;
        this.paramValue = paramValue;
    }

    @Override
    public protobuf.Proposal.Builder getProposalBuilder() {
        final protobuf.ChangeParamProposal.Builder builder = protobuf.ChangeParamProposal.newBuilder()
                .setParam(param.name())
                .setParamValue(paramValue);
        return super.getProposalBuilder().setChangeParamProposal(builder);
    }

    public static ChangeParamProposal fromProto(protobuf.Proposal proto) {
        final protobuf.ChangeParamProposal proposalProto = proto.getChangeParamProposal();
        return new ChangeParamProposal(proto.getName(),
                proto.getLink(),
                Param.fromProto(proposalProto),
                proposalProto.getParamValue(),
                (byte) proto.getVersion(),
                proto.getCreationDate(),
                proto.getTxId(),
                CollectionUtils.isEmpty(proto.getExtraDataMap()) ?
                        null : new TreeMap<>(proto.getExtraDataMap()));
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Canonical
    ///////////////////////////////////////////////////////////////////////////////////////////

    static final CanonicalSchema<ChangeParamProposal> EXTENSION_SCHEMA =
            CanonicalSchema.<ChangeParamProposal>newBuilder()
                    .string(1, proposal -> proposal.getParam().name())
                    .string(2, ChangeParamProposal::getParamValue)
                    .build();

    public static final CanonicalSchema<ChangeParamProposal> SCHEMA =
            ChangeParamProposal.<ChangeParamProposal>getBaseProposalSchemaBuilder()
                    .extend(8, proposal -> proposal, EXTENSION_SCHEMA)
                    // extra_data keeps protobuf field 20 and must stay after proposal subtype
                    // extensions, which occupy fields 6 through 12.
                    .mapStringToString(20,
                            Proposal::getExtraDataMapForCanonical,
                            TreeMapIterator.naturalOrder())
                    .build();

    @Override
    public byte[] encodeCanonical(CanonicalEncoder canonicalEncoder) {
        return canonicalEncoder.encode(this, SCHEMA);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Getters
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public ProposalType getType() {
        return ProposalType.CHANGE_PARAM;
    }

    @Override
    public Param getQuorumParam() {
        return Param.QUORUM_CHANGE_PARAM;
    }

    @Override
    public Param getThresholdParam() {
        return Param.THRESHOLD_CHANGE_PARAM;
    }

    @Override
    public TxType getTxType() {
        return TxType.PROPOSAL;
    }

    @Override
    public Proposal cloneProposalAndAddTxId(String txId) {
        return new ChangeParamProposal(getName(),
                getLink(),
                getParam(),
                getParamValue(),
                getVersion(),
                getCreationDate(),
                txId,
                extraDataMap == null ? null : new TreeMap<>(extraDataMap));
    }

    @Override
    public String toString() {
        return "ChangeParamProposal{" +
                "\n     param=" + param +
                ",\n     paramValue=" + paramValue +
                "\n} " + super.toString();
    }

    // Enums must not be used directly for hashCode or equals as it delivers the Object.hashCode (internal address)!
    // The equals and hashCode methods cannot be overwritten in Enums.
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ChangeParamProposal)) return false;
        if (!super.equals(o)) return false;
        ChangeParamProposal that = (ChangeParamProposal) o;
        boolean paramTypeNameIsEquals = param.getParamType().name().equals(that.param.getParamType().name());
        boolean paramNameIsEquals = param.name().equals(that.param.name());
        return paramNameIsEquals && paramTypeNameIsEquals &&
                Objects.equals(paramValue, that.paramValue);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), param.getParamType().name(), param.name(), paramValue);
    }
}
