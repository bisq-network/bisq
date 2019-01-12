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

import bisq.core.app.BisqEnvironment;
import bisq.core.dao.governance.param.Param;
import bisq.core.dao.governance.proposal.IssuanceProposal;
import bisq.core.dao.governance.proposal.ProposalType;
import bisq.core.dao.state.model.ImmutableDaoStateModel;
import bisq.core.dao.state.model.blockchain.TxType;

import bisq.common.app.Version;

import io.bisq.generated.protobuffer.PB;

import org.bitcoinj.core.Address;
import org.bitcoinj.core.AddressFormatException;
import org.bitcoinj.core.Coin;

import java.util.Date;

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
                                 String bsqAddress) {
        this(name,
                link,
                bsqAddress,
                requestedBsq.value,
                Version.REIMBURSEMENT_REQUEST,
                new Date().getTime(),
                "");
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
                                  String txId) {
        super(name,
                link,
                version,
                creationDate,
                txId);

        this.requestedBsq = requestedBsq;
        this.bsqAddress = bsqAddress;
    }

    @Override
    public PB.Proposal.Builder getProposalBuilder() {
        final PB.ReimbursementProposal.Builder builder = PB.ReimbursementProposal.newBuilder()
                .setBsqAddress(bsqAddress)
                .setRequestedBsq(requestedBsq);
        return super.getProposalBuilder().setReimbursementProposal(builder);
    }

    public static ReimbursementProposal fromProto(PB.Proposal proto) {
        final PB.ReimbursementProposal proposalProto = proto.getReimbursementProposal();
        return new ReimbursementProposal(proto.getName(),
                proto.getLink(),
                proposalProto.getBsqAddress(),
                proposalProto.getRequestedBsq(),
                (byte) proto.getVersion(),
                proto.getCreationDate(),
                proto.getTxId());
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Getters
    ///////////////////////////////////////////////////////////////////////////////////////////

    public Coin getRequestedBsq() {
        return Coin.valueOf(requestedBsq);
    }

    public Address getAddress() throws AddressFormatException {
        // Remove leading 'B'
        String underlyingBtcAddress = bsqAddress.substring(1, bsqAddress.length());
        return Address.fromBase58(BisqEnvironment.getParameters(), underlyingBtcAddress);
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
                getCreationDate().getTime(),
                txId);
    }

    @Override
    public String toString() {
        return "ReimbursementProposal{" +
                "\n     requestedBsq=" + requestedBsq +
                ",\n     bsqAddress='" + bsqAddress + '\'' +
                "\n} " + super.toString();
    }
}
