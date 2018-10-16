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

package bisq.core.dao.governance.voteresult;

import bisq.core.dao.governance.ConsensusCritical;

import bisq.common.proto.persistable.PersistableList;

import io.bisq.generated.protobuffer.PB;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import lombok.EqualsAndHashCode;

/**
 * PersistableEnvelope wrapper for list of decryptedBallotsWithMerits.
 */
@EqualsAndHashCode(callSuper = true)
public class DecryptedBallotsWithMeritsList extends PersistableList<DecryptedBallotsWithMerits> implements ConsensusCritical {

    public DecryptedBallotsWithMeritsList(List<DecryptedBallotsWithMerits> list) {
        super(list);
    }

    public DecryptedBallotsWithMeritsList() {
        super();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // PROTO BUFFER
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public PB.PersistableEnvelope toProtoMessage() {
        return PB.PersistableEnvelope.newBuilder().setDecryptedBallotsWithMeritsList(getBuilder()).build();
    }

    private PB.DecryptedBallotsWithMeritsList.Builder getBuilder() {
        return PB.DecryptedBallotsWithMeritsList.newBuilder()
                .addAllDecryptedBallotsWithMerits(getList().stream()
                        .map(DecryptedBallotsWithMerits::toProtoMessage)
                        .collect(Collectors.toList()));
    }

    public static DecryptedBallotsWithMeritsList fromProto(PB.DecryptedBallotsWithMeritsList proto) {
        return new DecryptedBallotsWithMeritsList(new ArrayList<>(proto.getDecryptedBallotsWithMeritsList().stream()
                .map(DecryptedBallotsWithMerits::fromProto)
                .collect(Collectors.toList())));
    }

    @Override
    public String toString() {
        return "List of blindVoteTxId's in DecryptedBallotsWithMeritsList: " + getList().stream()
                .map(DecryptedBallotsWithMerits::getBlindVoteTxId)
                .collect(Collectors.toList());
    }
}

