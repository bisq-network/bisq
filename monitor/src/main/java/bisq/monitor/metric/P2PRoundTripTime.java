/*
 * This file is part of Bisq.
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

package bisq.monitor.metric;

import bisq.monitor.OnionParser;
import bisq.monitor.Reporter;
import bisq.monitor.StatisticsHelper;

import bisq.network.p2p.NodeAddress;
import bisq.network.p2p.network.CloseConnectionReason;
import bisq.network.p2p.network.Connection;
import bisq.network.p2p.peers.keepalive.messages.Ping;
import bisq.network.p2p.peers.keepalive.messages.Pong;

import bisq.common.proto.network.NetworkEnvelope;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import static com.google.common.base.Preconditions.checkNotNull;

public class P2PRoundTripTime extends P2PSeedNodeSnapshotBase {

    private static final String SAMPLE_SIZE = "run.sampleSize";
    private final Map<Integer, Long> sentAt = new HashMap<>();
    private Map<NodeAddress, Statistics> measurements = new HashMap<>();

    public P2PRoundTripTime(Reporter reporter) {
        super(reporter);
    }

    /**
     * Use a counter to do statistics.
     */
    private class Statistics {

        private final List<Long> samples = new ArrayList<>();

        public synchronized void log(Object message) {
            Pong pong = (Pong) message;
            Long start = sentAt.get(pong.getRequestNonce());
            if (start != null)
                samples.add(System.currentTimeMillis() - start);
        }

        public List<Long> values() {
            return samples;
        }
    }

    @Override
    protected List<NetworkEnvelope> getRequests() {
        List<NetworkEnvelope> result = new ArrayList<>();

        Random random = new Random();
        for (int i = 0; i < Integer.parseInt(configuration.getProperty(SAMPLE_SIZE, "1")); i++)
            result.add(new Ping(random.nextInt(), 42));

        return result;
    }

    @Override
    protected void aboutToSend(NetworkEnvelope message) {
        sentAt.put(((Ping) message).getNonce(), System.currentTimeMillis());
    }

    @Override
    protected boolean treatMessage(NetworkEnvelope networkEnvelope, Connection connection) {
        if (networkEnvelope instanceof Pong) {
            checkNotNull(connection.getPeersNodeAddressProperty(),
                    "although the property is nullable, we need it to not be null");

            measurements.putIfAbsent(connection.getPeersNodeAddressProperty().getValue(), new Statistics());

            measurements.get(connection.getPeersNodeAddressProperty().getValue()).log(networkEnvelope);

            connection.shutDown(CloseConnectionReason.APP_SHUT_DOWN);
            return true;
        }
        return false;
    }

    @Override
    void report() {
        // report
        measurements.forEach(((nodeAddress, samples) ->
                reporter.report(StatisticsHelper.process(samples.values()),
                        getName() + "." + OnionParser.prettyPrint(nodeAddress))
        ));
        // clean up for next round
        measurements = new HashMap<>();
    }
}
