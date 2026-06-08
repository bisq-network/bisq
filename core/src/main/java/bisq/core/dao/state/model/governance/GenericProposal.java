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
import java.util.TreeMap;

import lombok.EqualsAndHashCode;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

@Immutable
@Slf4j
@EqualsAndHashCode(callSuper = true)
@Value
public final class GenericProposal extends Proposal implements ImmutableDaoStateModel {

    public GenericProposal(String name,
                           String link,
                           @Nullable TreeMap<String, String> extraDataMap) {
        this(name,
                link,
                Version.PROPOSAL,
                new Date().getTime(),
                null,
                extraDataMap);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // PROTO BUFFER
    ///////////////////////////////////////////////////////////////////////////////////////////

    private GenericProposal(String name,
                            String link,
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
    }

    @Override
    public protobuf.Proposal.Builder getProposalBuilder() {
        final protobuf.GenericProposal.Builder builder = protobuf.GenericProposal.newBuilder();
        return super.getProposalBuilder().setGenericProposal(builder);
    }

    public static GenericProposal fromProto(protobuf.Proposal proto) {
        return new GenericProposal(proto.getName(),
                proto.getLink(),
                (byte) proto.getVersion(),
                proto.getCreationDate(),
                proto.getTxId(),
                CollectionUtils.isEmpty(proto.getExtraDataMap()) ?
                        null : new TreeMap<>(proto.getExtraDataMap()));
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Canonical
    ///////////////////////////////////////////////////////////////////////////////////////////

    static final CanonicalSchema<GenericProposal> EXTENSION_SCHEMA =
            CanonicalSchema.<GenericProposal>newBuilder().build();

    public static final CanonicalSchema<GenericProposal> SCHEMA =
            GenericProposal.<GenericProposal>getBaseProposalSchemaBuilder()
                    .extend(11, proposal -> proposal, EXTENSION_SCHEMA)
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
        return ProposalType.GENERIC;
    }

    @Override
    public Param getQuorumParam() {
        return Param.QUORUM_GENERIC;
    }

    @Override
    public Param getThresholdParam() {
        return Param.THRESHOLD_GENERIC;
    }

    @Override
    public TxType getTxType() {
        return TxType.PROPOSAL;
    }

    @Override
    public Proposal cloneProposalAndAddTxId(String txId) {
        return new GenericProposal(getName(),
                getLink(),
                getVersion(),
                getCreationDate(),
                txId,
                extraDataMap == null ? null : new TreeMap<>(extraDataMap));
    }

    @Override
    public String toString() {
        return "GenericProposal{" +
                "\n} " + super.toString();
    }
}
