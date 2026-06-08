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

import java.util.Date;

import lombok.EqualsAndHashCode;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;

import javax.annotation.concurrent.Immutable;

import static com.google.common.base.Preconditions.checkArgument;

@Immutable
@Slf4j
@EqualsAndHashCode(callSuper = true)
@Value
public final class ConfiscateBondProposal extends Proposal implements ImmutableDaoStateModel {
    private final String lockupTxId;

    public ConfiscateBondProposal(String name,
                                  String link,
                                  String lockupTxId) {
        this(name,
                link,
                lockupTxId,
                Version.PROPOSAL,
                new Date().getTime(),
                null);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // PROTO BUFFER
    ///////////////////////////////////////////////////////////////////////////////////////////

    private ConfiscateBondProposal(String name,
                                   String link,
                                   String lockupTxId,
                                   byte version,
                                   long creationDate,
                                   String txId) {
        super(name,
                link,
                version,
                creationDate,
                txId,
                null);
        this.lockupTxId = lockupTxId;
    }

    @Override
    public protobuf.Proposal.Builder getProposalBuilder() {
        final protobuf.ConfiscateBondProposal.Builder builder = protobuf.ConfiscateBondProposal.newBuilder()
                .setLockupTxId(lockupTxId);
        return super.getProposalBuilder().setConfiscateBondProposal(builder);
    }

    public static ConfiscateBondProposal fromProto(protobuf.Proposal proto) {
        // ExtraDataMap was always empty and is not supported anymore since v1.10.2.
        // It is not expected that any historical data exist with a non-empty ExtraDataMap.
        checkArgument(proto.getExtraDataMap().isEmpty(),
                "ExtraDataMap is expected to be not set in ConfiscateBondProposal");

        final protobuf.ConfiscateBondProposal proposalProto = proto.getConfiscateBondProposal();
        return new ConfiscateBondProposal(proto.getName(),
                proto.getLink(),
                proposalProto.getLockupTxId(),
                (byte) proto.getVersion(),
                proto.getCreationDate(),
                proto.getTxId());
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Canonical
    ///////////////////////////////////////////////////////////////////////////////////////////

    static final CanonicalSchema<ConfiscateBondProposal> EXTENSION_SCHEMA =
            CanonicalSchema.<ConfiscateBondProposal>newBuilder()
                    .string(1, ConfiscateBondProposal::getLockupTxId)
                    .build();

    public static final CanonicalSchema<ConfiscateBondProposal> SCHEMA =
            ConfiscateBondProposal.<ConfiscateBondProposal>getBaseProposalSchemaBuilder()
                    .extend(10, proposal -> proposal, EXTENSION_SCHEMA)
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
        return ProposalType.CONFISCATE_BOND;
    }

    @Override
    public Param getQuorumParam() {
        return Param.QUORUM_CONFISCATION;
    }

    @Override
    public Param getThresholdParam() {
        return Param.THRESHOLD_CONFISCATION;
    }

    @Override
    public TxType getTxType() {
        return TxType.PROPOSAL;
    }

    @Override
    public Proposal cloneProposalAndAddTxId(String txId) {
        return new ConfiscateBondProposal(getName(),
                getLink(),
                getLockupTxId(),
                getVersion(),
                getCreationDate(),
                txId);
    }

    @Override
    public String toString() {
        return "ConfiscateBondProposal{" +
                "\n     lockupTxId=" + lockupTxId +
                "\n} " + super.toString();
    }
}
