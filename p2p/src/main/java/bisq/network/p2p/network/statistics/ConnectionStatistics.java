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

import bisq.common.proto.network.NetworkEnvelope;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import lombok.Getter;
import lombok.Setter;

@Getter
public class ConnectionStatistics {

    public interface Listener {
        void onNewSentBytes(long numberOfNewBytes);

        void onNewReceivedBytes(long numberOfNewBytes);

        void onAddSentMessage(NetworkEnvelope networkEnvelope);

        void onAddReceivedMessage(NetworkEnvelope networkEnvelope);
    }

    private final Date creationDate = new Date();
    private final List<Listener> allListeners = new ArrayList<>();
    private final Map<String, Integer> receivedMessages = new HashMap<>();
    private final Map<String, Integer> sentMessages = new HashMap<>();

    private long lastActivityTimestamp = System.currentTimeMillis();
    private long sentBytes;
    private long receivedBytes;
    private int totalSentMessages;
    private int totalReceivedMessages;
    @Setter
    private int roundTripTime;

    public void addListener(Listener listener) {
        allListeners.add(listener);
    }

    public void removeListener(Listener listener) {
        allListeners.remove(listener);
    }

    void updateLastActivityTimestamp() {
        lastActivityTimestamp = System.currentTimeMillis();
    }

    void addSentBytes(int value) {
        sentBytes += value;
        allListeners.forEach(listener -> listener.onNewSentBytes(value));
    }

    void addReceivedBytes(int value) {
        receivedBytes += value;
        allListeners.forEach(listener -> listener.onNewReceivedBytes(value));
    }

    void addSentMessage(NetworkEnvelope networkEnvelope) {
        String messageClassName = networkEnvelope.getClass().getSimpleName();
        sentMessages.merge(messageClassName, 1, Integer::sum);

        totalSentMessages++;
        allListeners.forEach(listener -> listener.onAddSentMessage(networkEnvelope));
    }

    void addReceivedMessage(NetworkEnvelope networkEnvelope) {
        String messageClassName = networkEnvelope.getClass().getSimpleName();
        receivedMessages.merge(messageClassName, 1, Integer::sum);

        totalReceivedMessages++;
        allListeners.forEach(listener -> listener.onAddReceivedMessage(networkEnvelope));
    }

    public long getLastActivityAge() {
        return System.currentTimeMillis() - lastActivityTimestamp;
    }

    @Override
    public String toString() {
        return "ConnectionStatistics{" +
                "\n     creationDate=" + creationDate +
                ",\n     lastActivityTimestamp=" + lastActivityTimestamp +
                ",\n     sentBytes=" + sentBytes +
                ",\n     receivedBytes=" + receivedBytes +
                ",\n     receivedMessages=" + receivedMessages +
                ",\n     sentMessages=" + sentMessages +
                ",\n     roundTripTime=" + roundTripTime +
                "\n}";
    }
}
