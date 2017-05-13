/*
 * This file is part of bisq.
 *
 * bisq is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * bisq is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with bisq. If not, see <http://www.gnu.org/licenses/>.
 */

package io.bisq.core.offer;

import com.google.protobuf.Message;
import io.bisq.common.proto.persistable.PersistableEnvelope;
import io.bisq.generated.protobuffer.PB;
import lombok.Getter;
import lombok.experimental.Delegate;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class OpenOfferList implements PersistableEnvelope {
    @Getter
    @Delegate
    private List<OpenOffer> list = new ArrayList<>();

    public OpenOfferList() {
    }

    public OpenOfferList(List<OpenOffer> list) {
        this.list = list;
    }

    @Override
    public Message toProtoMessage() {
        return PB.PersistableEnvelope.newBuilder()
                .setOpenOfferList(PB.OpenOfferList.newBuilder()
                        .addAllOpenOffer(getList().stream().map(OpenOffer::toProtoMessage).collect(Collectors.toList())))
                .build();
    }

    public static PersistableEnvelope fromProto(PB.OpenOfferList proto) {
        return new OpenOfferList(proto.getOpenOfferList().stream().map(OpenOffer::fromProto)
                .collect(Collectors.toList()));
    }
}
