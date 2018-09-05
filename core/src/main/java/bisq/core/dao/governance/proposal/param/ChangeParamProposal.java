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

package bisq.core.dao.governance.proposal.param;

import bisq.core.dao.governance.proposal.Proposal;
import bisq.core.dao.governance.proposal.ProposalType;
import bisq.core.dao.state.blockchain.TxType;
import bisq.core.dao.state.governance.Param;

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
public final class ChangeParamProposal extends Proposal {
    private final Param param;
    private final long paramValue;

    ChangeParamProposal(String name,
                        String link,
                        Param param,
                        long paramValue) {
        this(name,
                link,
                param,
                paramValue,
                Version.PROPOSAL,
                new Date().getTime(),
                "");
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // PROTO BUFFER
    ///////////////////////////////////////////////////////////////////////////////////////////

    private ChangeParamProposal(String name,
                                String link,
                                Param param,
                                long paramValue,
                                byte version,
                                long creationDate,
                                String txId) {
        super(name,
                link,
                version,
                creationDate,
                txId);

        this.param = param;
        this.paramValue = paramValue;
    }

    @Override
    public PB.Proposal.Builder getProposalBuilder() {
        final PB.ChangeParamProposal.Builder builder = PB.ChangeParamProposal.newBuilder()
                .setParam(param.getParamName())
                .setParamValue(paramValue);
        return super.getProposalBuilder().setChangeParamProposal(builder);
    }

    public static ChangeParamProposal fromProto(PB.Proposal proto) {
        final PB.ChangeParamProposal proposalProto = proto.getChangeParamProposal();
        return new ChangeParamProposal(proto.getName(),
                proto.getLink(),
                Param.fromProto(proposalProto),
                proposalProto.getParamValue(),
                (byte) proto.getVersion(),
                proto.getCreationDate(),
                proto.getTxId());
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
                getCreationDate().getTime(),
                txId);
    }

    @Override
    public String toString() {
        return "ChangeParamProposal{" +
                "\n     param=" + param +
                ",\n     paramValue=" + paramValue +
                "\n} " + super.toString();
    }
}
