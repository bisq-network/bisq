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

package bisq.network.p2p.inventory;

import bisq.network.p2p.inventory.messages.GetInventoryRequest;
import bisq.network.p2p.inventory.messages.GetInventoryResponse;
import bisq.network.p2p.network.Connection;
import bisq.network.p2p.network.MessageListener;
import bisq.network.p2p.network.NetworkNode;
import bisq.network.p2p.storage.P2PDataStorage;
import bisq.network.p2p.storage.payload.ProtectedStorageEntry;

import bisq.common.proto.network.NetworkEnvelope;

import javax.inject.Inject;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class GetInventoryRequestHandler implements MessageListener {
    private final NetworkNode networkNode;
    private final P2PDataStorage p2PDataStorage;

    @Inject
    public GetInventoryRequestHandler(NetworkNode networkNode, P2PDataStorage p2PDataStorage) {
        this.networkNode = networkNode;
        this.p2PDataStorage = p2PDataStorage;
        networkNode.addMessageListener(this);
    }

    @Override
    public void onMessage(NetworkEnvelope networkEnvelope, Connection connection) {
        if (networkEnvelope instanceof GetInventoryRequest) {
            GetInventoryRequest getInventoryRequest = (GetInventoryRequest) networkEnvelope;

            Map<String, Integer> numPayloadsByClassName = new HashMap<>();
            p2PDataStorage.getMapForDataResponse(getInventoryRequest.getVersion()).values().stream()
                    .map(e -> e.getClass().getSimpleName())
                    .forEach(className -> {
                        numPayloadsByClassName.putIfAbsent(className, 0);
                        int prev = numPayloadsByClassName.get(className);
                        numPayloadsByClassName.put(className, prev + 1);
                    });
            p2PDataStorage.getMap().values().stream()
                    .map(ProtectedStorageEntry::getProtectedStoragePayload)
                    .filter(Objects::nonNull)
                    .map(e -> e.getClass().getSimpleName())
                    .forEach(className -> {
                        numPayloadsByClassName.putIfAbsent(className, 0);
                        int prev = numPayloadsByClassName.get(className);
                        numPayloadsByClassName.put(className, prev + 1);
                    });

            GetInventoryResponse getInventoryResponse = new GetInventoryResponse(numPayloadsByClassName);
            networkNode.sendMessage(connection, getInventoryResponse);
        }
    }

    public void shutDown() {
        networkNode.removeMessageListener(this);
    }
}
