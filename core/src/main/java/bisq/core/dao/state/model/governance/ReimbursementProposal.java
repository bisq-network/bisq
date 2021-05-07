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
import bisq.core.dao.governance.proposal.IssuanceProposal;
import bisq.core.dao.governance.proposal.ProposalType;
import bisq.core.dao.state.model.ImmutableDaoStateModel;
import bisq.core.dao.state.model.blockchain.TxType;

import bisq.common.app.Version;
import bisq.common.util.CollectionUtils;

import org.bitcoinj.core.Coin;

import java.util.Date;
import java.util.Map;

import lombok.EqualsAndHashCode;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;

import javax.annotation.concurrent.Immutable;

@Immutable
@Slf4j
@EqualsAndHashCode(callSuper = true)
@Value
public final class ReimbursementProposal extends Proposal implements IssuanceProposal, ImmutableDaoStateModel {
    private final long requestedBsq;
    private final String bsqAddress;

    public ReimbursementProposal(String name,
                                 String link,
                                 Coin requestedBsq,
                                 String bsqAddress,
                                 Map<String, String> extraDataMap) {
        this(name,
                link,
                bsqAddress,
                requestedBsq.value,
                Version.REIMBURSEMENT_REQUEST,
                new Date().getTime(),
                null,
                extraDataMap);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // PROTO BUFFER
    ///////////////////////////////////////////////////////////////////////////////////////////

    private ReimbursementProposal(String name,
                                  String link,
                                  String bsqAddress,
                                  long requestedBsq,
                                  byte version,
                                  long creationDate,
                                  String txId,
                                  Map<String, String> extraDataMap) {
        super(name,
                link,
                version,
                creationDate,
                txId,
                extraDataMap);

        this.requestedBsq = requestedBsq;
        this.bsqAddress = bsqAddress;
    }

    @Override
    public protobuf.Proposal.Builder getProposalBuilder() {
        final protobuf.ReimbursementProposal.Builder builder = protobuf.ReimbursementProposal.newBuilder()
                .setBsqAddress(bsqAddress)
                .setRequestedBsq(requestedBsq);
        return super.getProposalBuilder().setReimbursementProposal(builder);
    }

    public static ReimbursementProposal fromProto(protobuf.Proposal proto) {
        final protobuf.ReimbursementProposal proposalProto = proto.getReimbursementProposal();
        return new ReimbursementProposal(proto.getName(),
                proto.getLink(),
                proposalProto.getBsqAddress(),
                proposalProto.getRequestedBsq(),
                (byte) proto.getVersion(),
                proto.getCreationDate(),
                proto.getTxId(),
                CollectionUtils.isEmpty(proto.getExtraDataMap()) ?
                        null : proto.getExtraDataMap());
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Getters
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public Coin getRequestedBsq() {
        return Coin.valueOf(requestedBsq);
    }

    @Override
    public ProposalType getType() {
        return ProposalType.REIMBURSEMENT_REQUEST;
    }

    @Override
    public Param getQuorumParam() {
        return Param.QUORUM_REIMBURSEMENT;
    }

    @Override
    public Param getThresholdParam() {
        return Param.THRESHOLD_REIMBURSEMENT;
    }

    @Override
    public TxType getTxType() {
        return TxType.REIMBURSEMENT_REQUEST;
    }

    @Override
    public Proposal cloneProposalAndAddTxId(String txId) {
        return new ReimbursementProposal(getName(),
                getLink(),
                getBsqAddress(),
                getRequestedBsq().value,
                getVersion(),
                getCreationDate(),
                txId,
                extraDataMap);
    }

    @Override
    public String toString() {
        return "ReimbursementProposal{" +
                "\n     requestedBsq=" + requestedBsq +
                ",\n     bsqAddress='" + bsqAddress + '\'' +
                "\n} " + super.toString();
    }
}
