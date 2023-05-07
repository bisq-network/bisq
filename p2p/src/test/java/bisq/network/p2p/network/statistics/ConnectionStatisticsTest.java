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

import bisq.common.proto.network.NetworkEnvelope;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

public class ConnectionStatisticsTest {
    private final ConnectionStatistics statistics = new ConnectionStatistics();

    @Test
    public void updateLastActivityTimestampTest() throws InterruptedException {
        var statistics = new ConnectionStatistics();
        long firstTimestamp = statistics.getLastActivityTimestamp();

        Thread.sleep(200);
        statistics.updateLastActivityTimestamp();

        assertTrue(statistics.getLastActivityTimestamp() > firstTimestamp);
    }

    @Test
    public void addSentBytesTest() {
        for (int i = 0; i < 5; i++) {
            statistics.addSentBytes(100);
        }

        assertEquals(500, statistics.getSentBytes());
    }

    @Test
    public void addReceivedBytesTest() {
        for (int i = 0; i < 5; i++) {
            statistics.addReceivedBytes(100);
        }

        assertEquals(500, statistics.getReceivedBytes());
    }

    @Test
    public void addSentMessageTest() {
        AckMessage ackMessage = mock(AckMessage.class);
        for (int i = 0; i < 3; i++) {
            statistics.addSentMessage(ackMessage);
        }

        AddDataMessage addDataMessage = mock(AddDataMessage.class);
        for (int i = 0; i < 5; i++) {
            statistics.addSentMessage(addDataMessage);
        }

        Map<String, Integer> countByMessageClassName = statistics.getSentMessages();

        String ackMessageClassName = ackMessage.getClass().getSimpleName();
        int counter = countByMessageClassName.get(ackMessageClassName);
        assertEquals(3, counter);

        String addDataMessageClassName = addDataMessage.getClass().getSimpleName();
        counter = countByMessageClassName.get(addDataMessageClassName);
        assertEquals(5, counter);
    }

    @Test
    public void addReceivedMessageTest() {
        AckMessage ackMessage = mock(AckMessage.class);
        for (int i = 0; i < 3; i++) {
            statistics.addReceivedMessage(ackMessage);
        }

        AddDataMessage addDataMessage = mock(AddDataMessage.class);
        for (int i = 0; i < 5; i++) {
            statistics.addReceivedMessage(addDataMessage);
        }

        Map<String, Integer> countByMessageClassName = statistics.getReceivedMessages();

        String ackMessageClassName = ackMessage.getClass().getSimpleName();
        int counter = countByMessageClassName.get(ackMessageClassName);
        assertEquals(3, counter);

        String addDataMessageClassName = addDataMessage.getClass().getSimpleName();
        counter = countByMessageClassName.get(addDataMessageClassName);
        assertEquals(5, counter);
    }

    @Test
    public void numberOfTotalSentMessages() {
        var statistics = new ConnectionStatistics();

        AckMessage ackMessage = mock(AckMessage.class);
        for (int i = 0; i < 3; i++) {
            statistics.addSentMessage(ackMessage);
        }

        AddDataMessage addDataMessage = mock(AddDataMessage.class);
        for (int i = 0; i < 5; i++) {
            statistics.addSentMessage(addDataMessage);
        }

        assertEquals(8, statistics.getTotalSentMessages());
    }

    @Test
    public void numberOfTotalReceivedMessages() {
        var statistics = new ConnectionStatistics();

        AckMessage ackMessage = mock(AckMessage.class);
        for (int i = 0; i < 3; i++) {
            statistics.addReceivedMessage(ackMessage);
        }

        AddDataMessage addDataMessage = mock(AddDataMessage.class);
        for (int i = 0; i < 5; i++) {
            statistics.addReceivedMessage(addDataMessage);
        }

        assertEquals(8, statistics.getTotalReceivedMessages());
    }

    @Test
    public void getLastActivityAge() throws InterruptedException {
        var statistics = new ConnectionStatistics();
        Thread.sleep(200);
        assertTrue(statistics.getLastActivityAge() > 100);
    }

    @Test
    public void onSentBytesUpdatedListenerTest() {
        var listener = new ConnectionStatistics.Listener() {
            long onSentBytes;

            @Override
            public void onNewSentBytes(long numberOfNewBytes) {
                onSentBytes += numberOfNewBytes;
            }

            @Override
            public void onNewReceivedBytes(long numberOfNewBytes) {
            }

            @Override
            public void onAddSentMessage(NetworkEnvelope networkEnvelope) {
            }

            @Override
            public void onAddReceivedMessage(NetworkEnvelope networkEnvelope) {
            }
        };
        statistics.addListener(listener);

        for (int i = 0; i < 5; i++) {
            statistics.addSentBytes(100);
        }

        assertEquals(500, listener.onSentBytes);
    }

    @Test
    public void onReceivedBytesUpdatedListenerTest() {
        var listener = new ConnectionStatistics.Listener() {
            long onReceivedBytes;

            @Override
            public void onNewSentBytes(long numberOfNewBytes) {
            }

            @Override
            public void onNewReceivedBytes(long numberOfNewBytes) {
                onReceivedBytes += numberOfNewBytes;
            }

            @Override
            public void onAddSentMessage(NetworkEnvelope networkEnvelope) {
            }

            @Override
            public void onAddReceivedMessage(NetworkEnvelope networkEnvelope) {
            }
        };
        statistics.addListener(listener);

        for (int i = 0; i < 3; i++) {
            statistics.addReceivedBytes(100);
        }

        assertEquals(300, listener.onReceivedBytes);
    }

    @Test
    public void onAddSentMessageListenerTest() {
        var listener = new ConnectionStatistics.Listener() {
            final Map<String, Integer> counterByClassName = new HashMap<>();

            @Override
            public void onNewSentBytes(long numberOfNewBytes) {
            }

            @Override
            public void onNewReceivedBytes(long numberOfNewBytes) {
            }

            @Override
            public void onAddSentMessage(NetworkEnvelope networkEnvelope) {
                String messageClassName = networkEnvelope.getClass().getSimpleName();
                counterByClassName.merge(messageClassName, 1, Integer::sum);
            }

            @Override
            public void onAddReceivedMessage(NetworkEnvelope networkEnvelope) {
            }
        };
        statistics.addListener(listener);

        AckMessage ackMessage = mock(AckMessage.class);
        for (int i = 0; i < 3; i++) {
            statistics.addSentMessage(ackMessage);
        }

        AddDataMessage addDataMessage = mock(AddDataMessage.class);
        for (int i = 0; i < 5; i++) {
            statistics.addSentMessage(addDataMessage);
        }

        Map<String, Integer> countByMessageClassName = listener.counterByClassName;

        String ackMessageClassName = ackMessage.getClass().getSimpleName();
        int counter = countByMessageClassName.get(ackMessageClassName);
        assertEquals(3, counter);

        String addDataMessageClassName = addDataMessage.getClass().getSimpleName();
        counter = countByMessageClassName.get(addDataMessageClassName);
        assertEquals(5, counter);
    }

    @Test
    public void onAddReceivedMessageListenerTest() {
        var listener = new ConnectionStatistics.Listener() {
            final Map<String, Integer> counterByClassName = new HashMap<>();

            @Override
            public void onNewSentBytes(long numberOfNewBytes) {
            }

            @Override
            public void onNewReceivedBytes(long numberOfNewBytes) {
            }

            @Override
            public void onAddSentMessage(NetworkEnvelope networkEnvelope) {
            }

            @Override
            public void onAddReceivedMessage(NetworkEnvelope networkEnvelope) {
                String messageClassName = networkEnvelope.getClass().getSimpleName();
                counterByClassName.merge(messageClassName, 1, Integer::sum);
            }
        };
        statistics.addListener(listener);

        AckMessage ackMessage = mock(AckMessage.class);
        for (int i = 0; i < 3; i++) {
            statistics.addReceivedMessage(ackMessage);
        }

        AddDataMessage addDataMessage = mock(AddDataMessage.class);
        for (int i = 0; i < 5; i++) {
            statistics.addReceivedMessage(addDataMessage);
        }

        Map<String, Integer> countByMessageClassName = listener.counterByClassName;

        String ackMessageClassName = ackMessage.getClass().getSimpleName();
        int counter = countByMessageClassName.get(ackMessageClassName);
        assertEquals(3, counter);

        String addDataMessageClassName = addDataMessage.getClass().getSimpleName();
        counter = countByMessageClassName.get(addDataMessageClassName);
        assertEquals(5, counter);
    }
}
