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

package bisq.core.dao.state.unconfirmed;

import bisq.common.proto.persistable.PersistableList;

import com.google.protobuf.Message;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import lombok.EqualsAndHashCode;

@EqualsAndHashCode(callSuper = true)
public class UnconfirmedBsqChangeOutputList extends PersistableList<UnconfirmedTxOutput> {

    UnconfirmedBsqChangeOutputList() {
        super();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // PROTO BUFFER
    ///////////////////////////////////////////////////////////////////////////////////////////

    private UnconfirmedBsqChangeOutputList(List<UnconfirmedTxOutput> list) {
        super(list);
    }

    @Override
    public Message toProtoMessage() {
        return protobuf.PersistableEnvelope.newBuilder()
                .setUnconfirmedBsqChangeOutputList(protobuf.UnconfirmedBsqChangeOutputList.newBuilder()
                        .addAllUnconfirmedTxOutput(getList().stream().map(UnconfirmedTxOutput::toProtoMessage).collect(Collectors.toList())))
                .build();
    }

    public static UnconfirmedBsqChangeOutputList fromProto(protobuf.UnconfirmedBsqChangeOutputList proto) {
        return new UnconfirmedBsqChangeOutputList(new ArrayList<>(proto.getUnconfirmedTxOutputList().stream()
                .map(UnconfirmedTxOutput::fromProto)
                .collect(Collectors.toList())));
    }

    public boolean containsTxOutput(UnconfirmedTxOutput txOutput) {
        return getList().stream().anyMatch(output -> output.getKey().equals(txOutput.getKey()));
    }
}
