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

package bisq.core.dao.governance.asset;

import bisq.common.proto.ProtoUtil;
import bisq.common.proto.persistable.PersistablePayload;

import io.bisq.generated.protobuffer.PB;

import lombok.Value;

@Value
public class RemovedAsset implements PersistablePayload {
    private final String tickerSymbol;
    private final RemoveReason removeReason;

    public RemovedAsset(String tickerSymbol, RemoveReason removeReason) {
        this.tickerSymbol = tickerSymbol;
        this.removeReason = removeReason;
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // PROTO BUFFER
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public PB.RemovedAsset toProtoMessage() {
        PB.RemovedAsset.Builder builder = PB.RemovedAsset.newBuilder()
                .setTickerSymbol(tickerSymbol)
                .setRemoveReason(removeReason.name());
        return builder.build();
    }

    public static RemovedAsset fromProto(PB.RemovedAsset proto) {
        return new RemovedAsset(proto.getTickerSymbol(),
                ProtoUtil.enumFromProto(RemoveReason.class, proto.getRemoveReason()));
    }
}
