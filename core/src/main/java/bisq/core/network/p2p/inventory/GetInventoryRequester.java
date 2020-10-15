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

import bisq.core.network.p2p.inventory.messages.GetInventoryRequest;
import bisq.core.network.p2p.inventory.messages.GetInventoryResponse;

import bisq.network.p2p.NodeAddress;
import bisq.network.p2p.network.Connection;
import bisq.network.p2p.network.MessageListener;
import bisq.network.p2p.network.NetworkNode;

import bisq.common.Timer;
import bisq.common.UserThread;
import bisq.common.app.Version;
import bisq.common.crypto.CryptoUtils;
import bisq.common.handlers.ErrorMessageHandler;
import bisq.common.proto.network.NetworkEnvelope;

import org.bitcoinj.core.ECKey;
import org.bitcoinj.core.Sha256Hash;

import java.util.Map;
import java.util.function.Consumer;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class GetInventoryRequester implements MessageListener {
    private final static int TIMEOUT_SEC = 90;

    private final NetworkNode networkNode;
    private final NodeAddress nodeAddress;
    private final ECKey sigKey;
    private final Consumer<Map<String, String>> resultHandler;
    private final ErrorMessageHandler errorMessageHandler;
    private Timer timer;

    public GetInventoryRequester(NetworkNode networkNode,
                                 NodeAddress nodeAddress,
                                 ECKey sigKey,
                                 Consumer<Map<String, String>> resultHandler,
                                 ErrorMessageHandler errorMessageHandler) {
        this.networkNode = networkNode;
        this.nodeAddress = nodeAddress;
        this.sigKey = sigKey;
        this.resultHandler = resultHandler;
        this.errorMessageHandler = errorMessageHandler;
    }

    public void request() {
        networkNode.addMessageListener(this);
        timer = UserThread.runAfter(this::onTimeOut, TIMEOUT_SEC);
        byte[] nonce = CryptoUtils.getRandomBytes(32);
        byte[] signature = sigKey.sign(Sha256Hash.of(nonce)).encodeToDER();
        byte[] pubKey = sigKey.getPubKey();
        GetInventoryRequest getInventoryRequest = new GetInventoryRequest(Version.VERSION, nonce, signature, pubKey);
        networkNode.sendMessage(nodeAddress, getInventoryRequest);
    }

    private void onTimeOut() {
        errorMessageHandler.handleErrorMessage("Timeout got triggered (" + TIMEOUT_SEC + " sec)");
        shutDown();
    }

    @Override
    public void onMessage(NetworkEnvelope networkEnvelope, Connection connection) {
        if (networkEnvelope instanceof GetInventoryResponse) {
            connection.getPeersNodeAddressOptional().ifPresent(peer -> {
                if (peer.equals(nodeAddress)) {
                    GetInventoryResponse getInventoryResponse = (GetInventoryResponse) networkEnvelope;
                    resultHandler.accept(getInventoryResponse.getInventory());
                    shutDown();
                }
            });
        }
    }

    public void shutDown() {
        if (timer != null) {
            timer.stop();
            timer = null;
        }
        networkNode.removeMessageListener(this);
    }
}
