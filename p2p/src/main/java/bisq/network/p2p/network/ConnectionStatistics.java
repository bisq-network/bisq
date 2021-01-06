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

package bisq.network.p2p.network;

import bisq.network.p2p.BundleOfEnvelopes;
import bisq.network.p2p.NodeAddress;

import bisq.common.proto.network.NetworkEnvelope;
import bisq.common.util.Utilities;

import java.util.HashMap;
import java.util.Map;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ConnectionStatistics implements MessageListener {
    private final Connection connection;
    private final ConnectionState connectionState;
    private final Map<String, Integer> sentDataMap = new HashMap<>();
    private final Map<String, Integer> receivedDataMap = new HashMap<>();
    @Getter
    private final long connectionCreationTimeStamp;
    @Getter
    private long lastMessageTimestamp;

    public ConnectionStatistics(Connection connection, ConnectionState connectionState) {
        this.connection = connection;
        this.connectionState = connectionState;

        connection.addMessageListener(this);

        connectionCreationTimeStamp = System.currentTimeMillis();
    }

    public void shutDown() {
        connection.removeMessageListener(this);
    }

    public String getInfo() {
        String ls = System.lineSeparator();
        long now = System.currentTimeMillis();
        String conInstance = connection instanceof InboundConnection ? "Inbound" : "Outbound";
        String age = connectionCreationTimeStamp > 0 ?
                Utilities.formatDurationAsWords(now - connectionCreationTimeStamp) :
                "N/A";
        String lastMsg = lastMessageTimestamp > 0 ?
                Utilities.formatDurationAsWords(now - lastMessageTimestamp) :
                "N/A";
        String peer = connection.getPeersNodeAddressOptional()
                .map(NodeAddress::getFullAddress)
                .orElse("[address not known yet]");
        return String.format(
                "Age: %s" + ls +
                        "Peer: %s%s " + ls +
                        "Type: %s " + ls +
                        "Direction: %s" + ls +
                        "UID: %s" + ls +
                        "Last message sent/received: %s" + ls +
                        "Sent data: %s;" + ls +
                        "Received data: %s;",
                age,
                connectionState.isSeedNode() ? "[Seed node] " : "",
                peer,
                connectionState.getPeerType().name(),
                conInstance,
                connection.getUid(),
                lastMsg,
                sentDataMap.toString(),
                receivedDataMap.toString());
    }

    @Override
    public void onMessage(NetworkEnvelope networkEnvelope,
                          Connection connection) {
        lastMessageTimestamp = System.currentTimeMillis();
        if (networkEnvelope instanceof BundleOfEnvelopes) {
            ((BundleOfEnvelopes) networkEnvelope).getEnvelopes().forEach(e -> addToMap(e, receivedDataMap));
            // We want to track also number of BundleOfEnvelopes
            addToMap(networkEnvelope, receivedDataMap);
        } else {
            addToMap(networkEnvelope, receivedDataMap);
        }
    }

    @Override
    public void onMessageSent(NetworkEnvelope networkEnvelope, Connection connection) {
        lastMessageTimestamp = System.currentTimeMillis();
        if (networkEnvelope instanceof BundleOfEnvelopes) {
            ((BundleOfEnvelopes) networkEnvelope).getEnvelopes().forEach(e -> addToMap(e, sentDataMap));
            // We want to track also number of BundleOfEnvelopes
            addToMap(networkEnvelope, sentDataMap);
        } else {
            addToMap(networkEnvelope, sentDataMap);
        }
    }

    private void addToMap(NetworkEnvelope networkEnvelope, Map<String, Integer> map) {
        String key = networkEnvelope.getClass().getSimpleName();
        map.putIfAbsent(key, 0);
        map.put(key, map.get(key) + 1);
    }
}
