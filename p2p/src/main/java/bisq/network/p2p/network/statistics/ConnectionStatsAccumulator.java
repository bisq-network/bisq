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

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import lombok.Getter;

@Getter
public class ConnectionStatsAccumulator implements Runnable {
    private final long startTime = System.currentTimeMillis();

    private final List<ConnectionStatistics> allConnectionStatistics;
    private final List<NetworkStatisticsService.Listener> allListeners = new CopyOnWriteArrayList<>();

    private long totalSentBytes;
    private long totalReceivedBytes;

    private int totalSentMessages;
    private int totalReceivedMessages;

    private double totalSentMessagesPerSec;
    private double totalReceivedMessagesPerSec;

    private double totalSentBytesPerSec;
    private double totalReceivedBytesPerSec;

    public ConnectionStatsAccumulator(List<ConnectionStatistics> allConnectionStatistics) {
        this.allConnectionStatistics = allConnectionStatistics;
    }

    @Override
    public void run() {
        long totalSentBytes = 0;
        long totalReceivedBytes = 0;

        int totalSentMessages = 0;
        int totalReceivedMessages = 0;

        for (ConnectionStatistics statistic : allConnectionStatistics) {
            totalSentBytes += statistic.getSentBytes();
            totalReceivedBytes += statistic.getReceivedBytes();

            totalSentMessages += statistic.getTotalSentMessages();
            totalReceivedMessages += statistic.getTotalReceivedMessages();
        }

        this.totalSentBytes = totalSentBytes;
        this.totalReceivedBytes = totalReceivedBytes;

        this.totalSentMessages = totalSentMessages;
        this.totalReceivedMessages = totalReceivedMessages;

        long passed = (System.currentTimeMillis() - startTime) / 1000;
        totalSentMessagesPerSec = ((double) totalSentMessages / passed);
        totalReceivedMessagesPerSec = ((double) totalReceivedMessages) / passed;

        totalSentBytesPerSec = ((double) totalSentBytes) / passed;
        totalReceivedBytesPerSec = ((double) totalReceivedBytes) / passed;

        callListeners();
    }

    private void callListeners() {
        allListeners.forEach(listener -> listener.onTotalSentStatsChanged(totalSentBytes, totalSentMessages, totalSentMessagesPerSec));
    }

    public void addListener(NetworkStatisticsService.Listener listener) {
        allListeners.add(listener);
    }

    public void removeListener(NetworkStatisticsService.Listener listener) {
        allListeners.remove(listener);
    }
}
