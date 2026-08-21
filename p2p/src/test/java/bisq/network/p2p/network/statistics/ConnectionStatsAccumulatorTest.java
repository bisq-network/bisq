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

import bisq.network.p2p.AckMessage;
import bisq.network.p2p.storage.messages.AddDataMessage;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;

public class ConnectionStatsAccumulatorTest {

    private final NetworkStatisticsService networkStatisticsService = new NetworkStatisticsService();

    @Test
    public void totalSentBytes() {
        List<ConnectionStatistics> allConnectionStatistics = new ArrayList<>();
        var connectionStatsAccumulator = new ConnectionStatsAccumulator(allConnectionStatistics);

        ConnectionStatistics firstConnectionStatistics = networkStatisticsService.newConnectionStatistics();
        firstConnectionStatistics.addSentBytes(100);

        ConnectionStatistics secondConnectionStatistics = networkStatisticsService.newConnectionStatistics();
        secondConnectionStatistics.addSentBytes(100);

        ConnectionStatistics thirdConnectionStatistics = networkStatisticsService.newConnectionStatistics();
        thirdConnectionStatistics.addSentBytes(100);

        allConnectionStatistics.add(firstConnectionStatistics);
        allConnectionStatistics.add(secondConnectionStatistics);
        allConnectionStatistics.add(thirdConnectionStatistics);

        connectionStatsAccumulator.run();

        assertEquals(300, connectionStatsAccumulator.getTotalSentBytes());
    }

    @Test
    public void totalReceivedBytes() {
        List<ConnectionStatistics> allConnectionStatistics = new ArrayList<>();
        var connectionStatsAccumulator = new ConnectionStatsAccumulator(allConnectionStatistics);

        ConnectionStatistics firstConnectionStatistics = networkStatisticsService.newConnectionStatistics();
        firstConnectionStatistics.addReceivedBytes(100);

        ConnectionStatistics secondConnectionStatistics = networkStatisticsService.newConnectionStatistics();
        secondConnectionStatistics.addReceivedBytes(100);

        ConnectionStatistics thirdConnectionStatistics = networkStatisticsService.newConnectionStatistics();
        thirdConnectionStatistics.addReceivedBytes(100);

        allConnectionStatistics.add(firstConnectionStatistics);
        allConnectionStatistics.add(secondConnectionStatistics);
        allConnectionStatistics.add(thirdConnectionStatistics);

        connectionStatsAccumulator.run();

        assertEquals(300, connectionStatsAccumulator.getTotalReceivedBytes());
    }

    @Test
    public void totalSentMessages() {
        ConnectionStatistics firstConnectionStatistics = networkStatisticsService.newConnectionStatistics();
        ConnectionStatistics secondConnectionStatistics = networkStatisticsService.newConnectionStatistics();
        ConnectionStatistics thirdConnectionStatistics = networkStatisticsService.newConnectionStatistics();

        List<ConnectionStatistics> allConnectionStatistics = List.of(
                firstConnectionStatistics, secondConnectionStatistics, thirdConnectionStatistics
        );
        var connectionStatsAccumulator = new ConnectionStatsAccumulator(allConnectionStatistics);

        AckMessage ackMessage = mock(AckMessage.class);
        for (int i = 0; i < 3; i++) {
            firstConnectionStatistics.addSentMessage(ackMessage);
            secondConnectionStatistics.addSentMessage(ackMessage);
            thirdConnectionStatistics.addSentMessage(ackMessage);
        }

        AddDataMessage addDataMessage = mock(AddDataMessage.class);
        for (int i = 0; i < 5; i++) {
            firstConnectionStatistics.addSentMessage(addDataMessage);
            secondConnectionStatistics.addSentMessage(addDataMessage);
            thirdConnectionStatistics.addSentMessage(addDataMessage);
        }

        connectionStatsAccumulator.run();

        assertEquals(8 * 3, connectionStatsAccumulator.getTotalSentMessages());
    }

    @Test
    public void totalReceivedSentMessages() {
        ConnectionStatistics firstConnectionStatistics = networkStatisticsService.newConnectionStatistics();
        ConnectionStatistics secondConnectionStatistics = networkStatisticsService.newConnectionStatistics();
        ConnectionStatistics thirdConnectionStatistics = networkStatisticsService.newConnectionStatistics();

        List<ConnectionStatistics> allConnectionStatistics = List.of(
                firstConnectionStatistics, secondConnectionStatistics, thirdConnectionStatistics
        );
        var connectionStatsAccumulator = new ConnectionStatsAccumulator(allConnectionStatistics);

        AckMessage ackMessage = mock(AckMessage.class);
        for (int i = 0; i < 3; i++) {
            firstConnectionStatistics.addReceivedMessage(ackMessage);
            secondConnectionStatistics.addReceivedMessage(ackMessage);
            thirdConnectionStatistics.addReceivedMessage(ackMessage);
        }

        AddDataMessage addDataMessage = mock(AddDataMessage.class);
        for (int i = 0; i < 5; i++) {
            firstConnectionStatistics.addReceivedMessage(addDataMessage);
            secondConnectionStatistics.addReceivedMessage(addDataMessage);
            thirdConnectionStatistics.addReceivedMessage(addDataMessage);
        }

        connectionStatsAccumulator.run();

        assertEquals(8 * 3, connectionStatsAccumulator.getTotalReceivedMessages());
    }

    @Test
    public void addListener() {
        var listener = new NetworkStatisticsService.Listener() {
            long totalSentBytes, numTotalSentMessages;

            @Override
            public void onTotalSentStatsChanged(long totalSentBytes,
                                                long totalSentMessages,
                                                double totalSentMessagesPerSec) {
                this.totalSentBytes = totalSentBytes;
                this.numTotalSentMessages = totalSentMessages;
            }
        };

        AckMessage ackMessage = mock(AckMessage.class);

        ConnectionStatistics firstConnectionStatistics = networkStatisticsService.newConnectionStatistics();
        firstConnectionStatistics.addSentBytes(100);
        firstConnectionStatistics.addSentMessage(ackMessage);

        ConnectionStatistics secondConnectionStatistics = networkStatisticsService.newConnectionStatistics();
        secondConnectionStatistics.addSentBytes(100);
        secondConnectionStatistics.addSentMessage(ackMessage);

        ConnectionStatistics thirdConnectionStatistics = networkStatisticsService.newConnectionStatistics();
        thirdConnectionStatistics.addSentBytes(100);
        thirdConnectionStatistics.addSentMessage(ackMessage);

        List<ConnectionStatistics> allConnectionStatistics = List.of(
                firstConnectionStatistics, secondConnectionStatistics, thirdConnectionStatistics
        );

        var connectionStatsAccumulator = new ConnectionStatsAccumulator(allConnectionStatistics);
        connectionStatsAccumulator.addListener(listener);
        connectionStatsAccumulator.run();

        assertEquals(300, listener.totalSentBytes);
        assertEquals(3, listener.numTotalSentMessages);
    }

    @Test
    public void removeListener() {
        var listener = new NetworkStatisticsService.Listener() {
            long totalSentBytes, numTotalSentMessages;

            @Override
            public void onTotalSentStatsChanged(long totalSentBytes,
                                                long totalSentMessages,
                                                double totalSentMessagesPerSec) {
                this.totalSentBytes = totalSentBytes;
                this.numTotalSentMessages = totalSentMessages;
            }
        };

        ConnectionStatistics firstConnectionStatistics = networkStatisticsService.newConnectionStatistics();
        firstConnectionStatistics.addSentBytes(100);

        AckMessage ackMessage = mock(AckMessage.class);
        firstConnectionStatistics.addSentMessage(ackMessage);

        List<ConnectionStatistics> allConnectionStatistics = List.of(firstConnectionStatistics);
        var connectionStatsAccumulator = new ConnectionStatsAccumulator(allConnectionStatistics);

        connectionStatsAccumulator.addListener(listener);
        connectionStatsAccumulator.removeListener(listener);
        connectionStatsAccumulator.run();

        assertEquals(0, listener.totalSentBytes);
        assertEquals(0, listener.numTotalSentMessages);
    }
}
