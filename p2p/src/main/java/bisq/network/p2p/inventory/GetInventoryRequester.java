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

import bisq.network.p2p.NodeAddress;
import bisq.network.p2p.inventory.messages.GetInventoryRequest;
import bisq.network.p2p.inventory.messages.GetInventoryResponse;
import bisq.network.p2p.network.Connection;
import bisq.network.p2p.network.MessageListener;
import bisq.network.p2p.network.NetworkNode;

import bisq.common.app.Version;
import bisq.common.proto.network.NetworkEnvelope;

import java.util.Map;
import java.util.function.Consumer;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class GetInventoryRequester implements MessageListener {
    private final NetworkNode networkNode;
    private final NodeAddress nodeAddress;
    private final Consumer<Map<String, Integer>> resultHandler;

    public GetInventoryRequester(NetworkNode networkNode,
                                 NodeAddress nodeAddress,
                                 Consumer<Map<String, Integer>> resultHandler) {
        this.networkNode = networkNode;
        this.nodeAddress = nodeAddress;
        this.resultHandler = resultHandler;
        networkNode.addMessageListener(this);
    }

    public void request() {
        networkNode.sendMessage(nodeAddress, new GetInventoryRequest(Version.VERSION));
    }

    @Override
    public void onMessage(NetworkEnvelope networkEnvelope, Connection connection) {
        if (networkEnvelope instanceof GetInventoryResponse) {
            GetInventoryResponse getInventoryResponse = (GetInventoryResponse) networkEnvelope;
            resultHandler.accept(getInventoryResponse.getNumPayloadsMap());
            shutDown();
        }
    }

    public void shutDown() {
        networkNode.removeMessageListener(this);
    }
}
