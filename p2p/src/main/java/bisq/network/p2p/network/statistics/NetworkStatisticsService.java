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

package bisq.network.p2p.network.statistics;

import bisq.common.util.Utilities;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Getter
public class NetworkStatisticsService {

    public interface Listener {
        void onTotalSentStatsChanged(long totalSentBytes, long totalSentMessages, double totalSentMessagesPerSec);
    }

    private final long startTime = System.currentTimeMillis();

    private final ScheduledExecutorService scheduledExecutorService = Executors.newScheduledThreadPool(1);
    private final List<ConnectionStatistics> allConnectionStatistics = new CopyOnWriteArrayList<>();
    private final ConnectionStatsAccumulator connectionStatsAccumulator =
            new ConnectionStatsAccumulator(allConnectionStatistics);

    private final Map<String, Integer> totalSentMessages = new HashMap<>();
    private final Map<String, Integer> totalReceivedMessages = new HashMap<>();

    public ConnectionStatistics newConnectionStatistics() {
        var connectionStatistics = new ConnectionStatistics();
        allConnectionStatistics.add(connectionStatistics);
        return connectionStatistics;
    }

    public void start() {
        scheduledExecutorService.scheduleAtFixedRate(
                connectionStatsAccumulator, 1, 1, TimeUnit.SECONDS
        );
        scheduledExecutorService.scheduleAtFixedRate(
                createStatisticsLogger(), 1, 1, TimeUnit.HOURS
        );
    }

    public void shutdown() {
        scheduledExecutorService.shutdownNow();
    }

    public void addListener(Listener listener) {
        connectionStatsAccumulator.addListener(listener);
    }

    public void removeListener(Listener listener) {
        connectionStatsAccumulator.removeListener(listener);
    }

    private Runnable createStatisticsLogger() {
        return () -> {
            ConnectionStatsAccumulator allStats = connectionStatsAccumulator;
            String ls = System.lineSeparator();

            log.info("Accumulated network statistics:" + ls +
                            "Bytes sent: {};" + ls +
                            "Number of sent messages/Sent messages: {} / {};" + ls +
                            "Number of sent messages per sec: {};" + ls +
                            "Bytes received: {}" + ls +
                            "Number of received messages/Received messages: {} / {};" + ls +
                            "Number of received messages per sec: {}" + ls,
                    Utilities.readableFileSize(allStats.getTotalSentBytes()),
                    allStats.getTotalSentMessages(), totalSentMessages,
                    allStats.getTotalSentMessagesPerSec(),
                    Utilities.readableFileSize(allStats.getTotalReceivedBytes()),
                    allStats.getTotalReceivedMessages(), totalReceivedMessages,
                    allStats.getTotalReceivedMessagesPerSec());
        };
    }
}
