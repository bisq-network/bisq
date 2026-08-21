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

package bisq.core.dao.monitoring.model;


import com.google.protobuf.ByteString;

import lombok.EqualsAndHashCode;
import lombok.Getter;

@EqualsAndHashCode(callSuper = true)

public final class ProposalStateHash extends StateHash {
    @Getter
    private final int numProposals;

    public ProposalStateHash(int cycleStartBlockHeight, byte[] hash, int numProposals) {
        super(cycleStartBlockHeight, hash);
        this.numProposals = numProposals;
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // PROTO BUFFER
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public protobuf.ProposalStateHash toProtoMessage() {
        return protobuf.ProposalStateHash.newBuilder()
                .setHeight(height)
                .setHash(ByteString.copyFrom(hash))
                .setNumProposals(numProposals).build();
    }

    public static ProposalStateHash fromProto(protobuf.ProposalStateHash proto) {
        return new ProposalStateHash(proto.getHeight(),
                proto.getHash().toByteArray(),
                proto.getNumProposals());
    }


    @Override
    public String toString() {
        return "ProposalStateHash{" +
                "\n     numProposals=" + numProposals +
                "\n} " + super.toString();
    }
}
