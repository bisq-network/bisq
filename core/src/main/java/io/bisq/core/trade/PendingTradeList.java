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

package io.bisq.core.trade;

import lombok.Getter;
import lombok.experimental.Delegate;

import java.util.ArrayList;
import java.util.List;

public class PendingTradeList /*implements PersistableEnvelope */ {
    @Getter
    @Delegate
    private List<Trade> list = new ArrayList<>();

    public PendingTradeList() {
    }

    public PendingTradeList(List<Trade> list) {
        this.list = list;
    }

   /* @Override
    public Message toProtoMessage() {
        return PB.PersistableEnvelope.newBuilder()
                .setPendingTradeList(PB.PendingTradeList.newBuilder()
                        .addAllTrade(getTradableList().stream().map(Trade::toProtoMessage).collect(Collectors.toList())))
                .build();
    }*/

   /* public static PersistableEnvelope fromProto(PB.PendingTradeList proto) {
        return new PendingTradeList(proto.getTradeList().stream().map(Trade::fromProto)
                .collect(Collectors.toList()));
    }*/
}
