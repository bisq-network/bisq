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

package bisq.core.network.p2p.inventory;

import bisq.core.network.p2p.inventory.model.InventoryItem;

import bisq.network.p2p.NodeAddress;
import bisq.network.p2p.network.NetworkNode;

import bisq.common.handlers.ErrorMessageHandler;

import javax.inject.Inject;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class GetInventoryRequestManager {
    private final NetworkNode networkNode;
    private final Map<NodeAddress, GetInventoryRequester> requesterMap = new HashMap<>();

    @Inject
    public GetInventoryRequestManager(NetworkNode networkNode) {
        this.networkNode = networkNode;
    }

    public void request(NodeAddress nodeAddress,
                        Consumer<Map<InventoryItem, String>> resultHandler,
                        ErrorMessageHandler errorMessageHandler) {
        if (requesterMap.containsKey(nodeAddress)) {
            log.warn("There was still a pending request for {}. We shut it down and make a new request",
                    nodeAddress.getFullAddress());
            requesterMap.get(nodeAddress).shutDown();
        }

        GetInventoryRequester getInventoryRequester = new GetInventoryRequester(networkNode,
                nodeAddress,
                resultMap -> {
                    requesterMap.remove(nodeAddress);
                    resultHandler.accept(resultMap);
                },
                errorMessage -> {
                    requesterMap.remove(nodeAddress);
                    errorMessageHandler.handleErrorMessage(errorMessage);
                });
        requesterMap.put(nodeAddress, getInventoryRequester);
        getInventoryRequester.request();
    }

    public void shutDown() {
        requesterMap.values().forEach(GetInventoryRequester::shutDown);
        requesterMap.clear();
    }
}
