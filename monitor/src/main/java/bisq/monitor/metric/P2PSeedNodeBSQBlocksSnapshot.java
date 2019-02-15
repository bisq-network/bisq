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

import bisq.monitor.Reporter;

import bisq.core.dao.node.messages.GetBlocksRequest;
import bisq.core.dao.node.messages.GetBlocksResponse;

import bisq.network.p2p.NodeAddress;
import bisq.network.p2p.network.Connection;
import bisq.network.p2p.network.MessageListener;

import bisq.common.proto.network.NetworkEnvelope;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import lombok.extern.slf4j.Slf4j;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Contacts a list of hosts and asks them for all the data excluding persisted messages. The
 * answers are then compiled into buckets of message types. Based on these
 * buckets, the Metric reports (for each host) the message types observed and
 * their number.
 * 
 * @author Florian Reimair
 *
 */
@Slf4j
public class P2PSeedNodeBSQBlocksSnapshot extends P2PSeedNodeSnapshot implements MessageListener {

    Statistics statistics;
    Map<NodeAddress, Statistics> blocksPerHost = new ConcurrentHashMap<>();

    /**
     * Efficient way to count message occurrences.
     */
    private class Counter {
        private final int value;

        Counter(int value) {
            this.value = value;
        }

        int value() {
            return value;
        }
    }

    /**
     * Use a counter to do statistics.
     */
    private class MyStatistics  implements  Statistics<Counter> {

        private Map<String, Counter> buckets = new HashMap<>();

        @Override
        public Statistics create() {
            return new MyStatistics();
        }

        @Override
        public synchronized void log(Object value) {

            buckets.putIfAbsent("BsqBlocks", new Counter((Integer) value));
        }

        @Override
        public Map<String, Counter> values() {
            return buckets;
        }

        @Override
        public synchronized void reset() {
            buckets.clear();
        }
    }

    public P2PSeedNodeBSQBlocksSnapshot(Reporter reporter) {
        super(reporter);

        statistics = new MyStatistics();
    }

    protected NetworkEnvelope getFreshRequest(NodeAddress nodeAddress) {
        return new GetBlocksRequest(0, 0, nodeAddress);
    }

    @Override
    public boolean treatMessage(NetworkEnvelope networkEnvelope, Connection connection) {
        if (networkEnvelope instanceof GetBlocksResponse) {
            Statistics result = this.statistics.create();

            result.log(((GetBlocksResponse) networkEnvelope).getBlocks().size());

            checkNotNull(connection.getPeersNodeAddressProperty(),
                    "although the property is nullable, we need it to not be null");
            blocksPerHost.put(connection.getPeersNodeAddressProperty().getValue(), result);
            return true;
        }

        return false;
    }
}
