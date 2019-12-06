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

import bisq.monitor.AvailableTor;
import bisq.monitor.Metric;
import bisq.monitor.Monitor;
import bisq.monitor.OnionParser;
import bisq.monitor.Reporter;
import bisq.monitor.ThreadGate;

import bisq.core.proto.network.CoreNetworkProtoResolver;

import bisq.network.p2p.CloseConnectionMessage;
import bisq.network.p2p.NodeAddress;
import bisq.network.p2p.network.Connection;
import bisq.network.p2p.network.MessageListener;
import bisq.network.p2p.network.NetworkNode;
import bisq.network.p2p.network.TorNetworkNode;

import bisq.common.proto.network.NetworkEnvelope;

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.SettableFuture;

import java.time.Clock;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import lombok.extern.slf4j.Slf4j;

import org.jetbrains.annotations.NotNull;

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
public abstract class P2PSeedNodeSnapshotBase extends Metric implements MessageListener {

    private static final String HOSTS = "run.hosts";
    private static final String TOR_PROXY_PORT = "run.torProxyPort";
    Statistics statistics;
    final Map<NodeAddress, Statistics<?>> bucketsPerHost = new ConcurrentHashMap<>();
    private final ThreadGate gate = new ThreadGate();

    /**
     * Statistics Interface for use with derived classes.
     *
     * @param <T> the value type of the statistics implementation
     */
    protected interface Statistics<T> {

        Statistics create();

        void log(Object message);

        Map<String, T> values();

        void reset();
    }

    public P2PSeedNodeSnapshotBase(Reporter reporter) {
        super(reporter);
    }

    @Override
    protected void execute() {
        // start the network node
        final NetworkNode networkNode = new TorNetworkNode(Integer.parseInt(configuration.getProperty(TOR_PROXY_PORT, "9054")),
                new CoreNetworkProtoResolver(Clock.systemDefaultZone()), false,
                new AvailableTor(Monitor.TOR_WORKING_DIR, "unused"));
        // we do not need to start the networkNode, as we do not need the HS
        //networkNode.start(this);

        // clear our buckets
        bucketsPerHost.clear();

        getRequests().forEach(getDataRequest -> send(networkNode, getDataRequest));

        report();
    }

    protected abstract List<NetworkEnvelope> getRequests();

    protected void send(NetworkNode networkNode, NetworkEnvelope message) {

        ArrayList<Thread> threadList = new ArrayList<>();

        // for each configured host
        for (String current : configuration.getProperty(HOSTS, "").split(",")) {
            threadList.add(new Thread(() -> {

            try {
                // parse Url
                NodeAddress target = OnionParser.getNodeAddress(current);

                // do the data request
                aboutToSend(message);
                SettableFuture<Connection> future = networkNode.sendMessage(target, message);

                Futures.addCallback(future, new FutureCallback<>() {
                    @Override
                    public void onSuccess(Connection connection) {
                        connection.addMessageListener(P2PSeedNodeSnapshotBase.this);
                    }

                    @Override
                    public void onFailure(@NotNull Throwable throwable) {
                        gate.proceed();
                        log.error(
                                "Sending {} failed. That is expected if the peer is offline.\n\tException={}", message.getClass().getSimpleName(), throwable.getMessage());
                    }
                });

            } catch (Exception e) {
                gate.proceed(); // release the gate on error
                e.printStackTrace();
            }
            }, current));
        }

        gate.engage(threadList.size());

        // start all threads and wait until they all finished. We do that so we can
        // minimize the time between querying the hosts and therefore the chance of
        // inconsistencies.
        threadList.forEach(Thread::start);

        gate.await();
    }

    protected void aboutToSend(NetworkEnvelope message) { };

    /**
     * Report all the stuff. Uses the configured reporter directly.
     */
    abstract void report();

    @Override
    public void onMessage(NetworkEnvelope networkEnvelope, Connection connection) {
        if(treatMessage(networkEnvelope, connection)) {
            gate.proceed();
        } else if (networkEnvelope instanceof CloseConnectionMessage) {
            gate.unlock();
        } else {
            log.warn("Got an unexpected message of type <{}>",
                    networkEnvelope.getClass().getSimpleName());
        }
        connection.removeMessageListener(this);
    }

    protected abstract boolean treatMessage(NetworkEnvelope networkEnvelope, Connection connection);
}
