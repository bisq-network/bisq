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
import bisq.network.p2p.InitialDataRequest;
import bisq.network.p2p.InitialDataResponse;
import bisq.network.p2p.NodeAddress;

import bisq.common.proto.network.NetworkEnvelope;
import bisq.common.util.Utilities;

import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ConnectionStatistics implements MessageListener {
    private final Connection connection;
    private final ConnectionState connectionState;
    private final Map<String, Integer> sentDataMap = new HashMap<>();
    private final Map<String, Integer> receivedDataMap = new HashMap<>();
    private final Map<String, Long> rrtMap = new HashMap<>();
    @Getter
    private final long connectionCreationTimeStamp;
    @Getter
    private long lastMessageTimestamp;
    @Getter
    private long timeOnSendMsg = 0;
    @Getter
    private long timeOnReceivedMsg = 0;
    @Getter
    private int sentBytes = 0;
    @Getter
    private int receivedBytes = 0;

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
        String age = Utilities.formatDurationAsWords(now - connectionCreationTimeStamp);
        String lastMsg = Utilities.formatDurationAsWords(now - lastMessageTimestamp);
        String peer = connection.getPeersNodeAddressOptional()
                .map(NodeAddress::getFullAddress)
                .orElse("[address not known yet]");

        // For seeds its processing time, for peers rrt
        String rrt = rrtMap.entrySet().stream()
                .map(e -> {
                    long value = e.getValue();
                    // Value is current milli as long we don't have the response
                    if (value < connectionCreationTimeStamp) {
                        String key = e.getKey().replace("Request", "Request/Response");
                        return key + ": " + Utilities.formatDurationAsWords(value);
                    } else {
                        // we don't want to show pending requests
                        return e.getKey() + " awaiting response... ";
                    }
                })
                .collect(Collectors.toList())
                .toString();
        if (rrt.equals("[]")) {
            rrt = "";
        } else {
            rrt = "Time for response: " + rrt + ls;
        }
        boolean seedNode = connectionState.isSeedNode();
        return String.format(
                "Age: %s" + ls +
                        "Peer: %s%s " + ls +
                        "Type: %s " + ls +
                        "Direction: %s" + ls +
                        "UID: %s" + ls +
                        "Time since last message: %s" + ls +
                        "%s" +
                        "Sent data: %s; %s" + ls +
                        "Received data: %s; %s" + ls +
                        "CPU time spent on sending messages: %s" + ls +
                        "CPU time spent on receiving messages: %s",
                age,
                seedNode ? "[Seed node] " : "", peer,
                connectionState.getPeerType().name(),
                conInstance,
                connection.getUid(),
                lastMsg,
                rrt,
                Utilities.readableFileSize(sentBytes), sentDataMap.toString(),
                Utilities.readableFileSize(receivedBytes), receivedDataMap.toString(),
                Utilities.formatDurationAsWords(timeOnSendMsg),
                Utilities.formatDurationAsWords(timeOnReceivedMsg));
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

        if (networkEnvelope instanceof InitialDataRequest) {
            rrtMap.putIfAbsent(key, System.currentTimeMillis());
        } else if (networkEnvelope instanceof InitialDataResponse) {
            String associatedRequest = ((InitialDataResponse) networkEnvelope).associatedRequest().getSimpleName();
            if (rrtMap.containsKey(associatedRequest)) {
                rrtMap.put(associatedRequest, System.currentTimeMillis() - rrtMap.get(associatedRequest));
            }
        }
    }

    public void addSendMsgMetrics(long timeSpent, int bytes) {
        this.timeOnSendMsg += timeSpent;
        this.sentBytes += bytes;
    }

    public void addReceivedMsgMetrics(long timeSpent, int bytes) {
        this.timeOnReceivedMsg += timeSpent;
        this.receivedBytes += bytes;
    }
}
