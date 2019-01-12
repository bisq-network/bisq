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

import bisq.common.app.Version;

import io.bisq.generated.protobuffer.PB;

import java.util.Date;

import lombok.EqualsAndHashCode;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;

import javax.annotation.concurrent.Immutable;

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
                "");
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
                txId);
        this.lockupTxId = lockupTxId;
    }

    @Override
    public PB.Proposal.Builder getProposalBuilder() {
        final PB.ConfiscateBondProposal.Builder builder = PB.ConfiscateBondProposal.newBuilder()
                .setLockupTxId(lockupTxId);
        return super.getProposalBuilder().setConfiscateBondProposal(builder);
    }

    public static ConfiscateBondProposal fromProto(PB.Proposal proto) {
        final PB.ConfiscateBondProposal proposalProto = proto.getConfiscateBondProposal();
        return new ConfiscateBondProposal(proto.getName(),
                proto.getLink(),
                proposalProto.getLockupTxId(),
                (byte) proto.getVersion(),
                proto.getCreationDate(),
                proto.getTxId());
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
                getCreationDate().getTime(),
                txId);
    }

    @Override
    public String toString() {
        return "ConfiscateBondProposal{" +
                "\n     lockupTxId=" + lockupTxId +
                "\n} " + super.toString();
    }
}
