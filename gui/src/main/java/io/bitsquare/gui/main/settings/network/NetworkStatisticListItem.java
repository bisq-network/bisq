/*
 * This file is part of Bitsquare.
 *
 * Bitsquare is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * Bitsquare is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Bitsquare. If not, see <http://www.gnu.org/licenses/>.
 */

package io.bitsquare.gui.main.settings.network;

import io.bitsquare.common.UserThread;
import io.bitsquare.gui.util.BSFormatter;
import io.bitsquare.p2p.network.Connection;
import io.bitsquare.p2p.network.OutboundConnection;
import io.bitsquare.p2p.network.Statistic;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import org.apache.commons.lang3.time.DurationFormatUtils;
import org.fxmisc.easybind.EasyBind;
import org.fxmisc.easybind.Subscription;
import org.reactfx.util.FxTimer;
import org.reactfx.util.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;

public class NetworkStatisticListItem {
    private static final Logger log = LoggerFactory.getLogger(NetworkStatisticListItem.class);

    private final Statistic statistic;
    private final Connection connection;
    private final Subscription sentBytesSubscription, receivedBytesSubscription;
    private final Timer timer;
    private final BSFormatter formatter;

    private final StringProperty lastActivity = new SimpleStringProperty();
    private final StringProperty sentBytes = new SimpleStringProperty();
    private final StringProperty receivedBytes = new SimpleStringProperty();

    public NetworkStatisticListItem(Connection connection, BSFormatter formatter) {
        this.connection = connection;
        this.formatter = formatter;
        this.statistic = connection.getStatistic();

        sentBytesSubscription = EasyBind.subscribe(statistic.sentBytesProperty(),
                e -> sentBytes.set(formatter.formatBytes((int) e)));
        receivedBytesSubscription = EasyBind.subscribe(statistic.receivedBytesProperty(),
                e -> receivedBytes.set(formatter.formatBytes((int) e)));

        timer = FxTimer.runPeriodically(Duration.ofMillis(1000),
                () -> UserThread.execute(() -> onLastActivityChanged(statistic.getLastActivityTimestamp())));
        onLastActivityChanged(statistic.getLastActivityTimestamp());
    }

    private void onLastActivityChanged(long timeStamp) {
        lastActivity.set(DurationFormatUtils.formatDuration(System.currentTimeMillis() - timeStamp, "mm:ss.SSS"));
    }

    public void cleanup() {
        sentBytesSubscription.unsubscribe();
        receivedBytesSubscription.unsubscribe();
        timer.stop();
    }

    public String getOnionAddress() {
        if (connection.getPeersNodeAddressOptional().isPresent())
            return connection.getPeersNodeAddressOptional().get().getFullAddress();
        else
            return "";
    }

    public String getConnectionType() {
        return connection instanceof OutboundConnection ? "outbound" : "inbound";
    }

    public String getCreationDate() {
        return formatter.formatDateTime(statistic.getCreationDate());
    }

    public String getPeerType() {
        if (connection.getPeerType() == Connection.PeerType.SEED_NODE)
            return "Seed node";
        else if (connection.getPeerType() == Connection.PeerType.DIRECT_MSG_PEER)
            return "Peer (direct)";
        else
            return "Peer";
    }

    public String getLastActivity() {
        return lastActivity.get();
    }

    public StringProperty lastActivityProperty() {
        return lastActivity;
    }

    public String getSentBytes() {
        return sentBytes.get();
    }

    public StringProperty sentBytesProperty() {
        return sentBytes;
    }

    public String getReceivedBytes() {
        return receivedBytes.get();
    }

    public StringProperty receivedBytesProperty() {
        return receivedBytes;
    }

}
