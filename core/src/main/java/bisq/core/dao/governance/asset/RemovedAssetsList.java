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

import bisq.core.dao.governance.ConsensusCritical;

import bisq.common.proto.persistable.PersistableList;

import io.bisq.generated.protobuffer.PB;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import lombok.EqualsAndHashCode;

/**
 * PersistableEnvelope wrapper for list of removedAssets.
 */
@EqualsAndHashCode(callSuper = true)
public class RemovedAssetsList extends PersistableList<RemovedAsset> implements ConsensusCritical {

    public RemovedAssetsList(List<RemovedAsset> list) {
        super(list);
    }

    public RemovedAssetsList() {
        super();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // PROTO BUFFER
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public PB.PersistableEnvelope toProtoMessage() {
        return PB.PersistableEnvelope.newBuilder().setRemovedAssetList(getBuilder()).build();
    }

    public PB.RemovedAssetList.Builder getBuilder() {
        return PB.RemovedAssetList.newBuilder()
                .addAllRemovedAsset(getList().stream()
                        .map(RemovedAsset::toProtoMessage)
                        .collect(Collectors.toList()));
    }

    public static RemovedAssetsList fromProto(PB.RemovedAssetList proto) {
        return new RemovedAssetsList(new ArrayList<>(proto.getRemovedAssetList().stream()
                .map(RemovedAsset::fromProto)
                .collect(Collectors.toList())));
    }

    @Override
    public String toString() {
        return "List of tickerSymbols in RemovedAssetList: " + getList().stream()
                .map(RemovedAsset::getTickerSymbol)
                .collect(Collectors.toList());
    }
}
