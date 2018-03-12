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

package bisq.desktop.main.settings.network;

import bisq.desktop.util.BSFormatter;

import bisq.network.p2p.network.Connection;
import bisq.network.p2p.network.OutboundConnection;
import bisq.network.p2p.network.Statistic;

import bisq.common.Clock;
import bisq.common.locale.Res;

import org.apache.commons.lang3.time.DurationFormatUtils;

import org.fxmisc.easybind.EasyBind;
import org.fxmisc.easybind.Subscription;

import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class P2pNetworkListItem {
    private static final Logger log = LoggerFactory.getLogger(P2pNetworkListItem.class);

    private final Statistic statistic;
    private final Connection connection;
    private final Subscription sentBytesSubscription, receivedBytesSubscription, onionAddressSubscription, roundTripTimeSubscription;
    private final Clock clock;
    private final BSFormatter formatter;

    private final StringProperty lastActivity = new SimpleStringProperty();
    private final StringProperty sentBytes = new SimpleStringProperty();
    private final StringProperty receivedBytes = new SimpleStringProperty();
    private final StringProperty peerType = new SimpleStringProperty();
    private final StringProperty connectionType = new SimpleStringProperty();
    private final StringProperty roundTripTime = new SimpleStringProperty();
    private final StringProperty onionAddress = new SimpleStringProperty();
    private final Clock.Listener listener;

    public P2pNetworkListItem(Connection connection, Clock clock, BSFormatter formatter) {
        this.connection = connection;
        this.clock = clock;
        this.formatter = formatter;
        this.statistic = connection.getStatistic();

        sentBytesSubscription = EasyBind.subscribe(statistic.sentBytesProperty(),
                e -> sentBytes.set(formatter.formatBytes((long) e)));
        receivedBytesSubscription = EasyBind.subscribe(statistic.receivedBytesProperty(),
                e -> receivedBytes.set(formatter.formatBytes((long) e)));
        onionAddressSubscription = EasyBind.subscribe(connection.peersNodeAddressProperty(),
                nodeAddress -> onionAddress.set(nodeAddress != null ? nodeAddress.getFullAddress() : Res.get("settings.net.notKnownYet")));
        roundTripTimeSubscription = EasyBind.subscribe(statistic.roundTripTimeProperty(),
                roundTripTime -> this.roundTripTime.set((int) roundTripTime == 0 ? "-" : roundTripTime + " ms"));

        listener = new Clock.Listener() {
            @Override
            public void onSecondTick() {
                onLastActivityChanged(statistic.getLastActivityTimestamp());
                updatePeerType();
                updateConnectionType();
            }

            @Override
            public void onMinuteTick() {
            }

            @Override
            public void onMissedSecondTick(long missed) {
            }
        };
        clock.addListener(listener);
        onLastActivityChanged(statistic.getLastActivityTimestamp());
        updatePeerType();
        updateConnectionType();
    }

    private void onLastActivityChanged(long timeStamp) {
        // TODO
        // Got one case where System.currentTimeMillis() - timeStamp resulted in a negative value,
        // probably caused by a threading issue. Protect it with Math.abs for a quick fix...
        lastActivity.set(DurationFormatUtils.formatDuration(Math.abs(System.currentTimeMillis() - timeStamp), "mm:ss.SSS"));
    }

    public void cleanup() {
        sentBytesSubscription.unsubscribe();
        receivedBytesSubscription.unsubscribe();
        onionAddressSubscription.unsubscribe();
        roundTripTimeSubscription.unsubscribe();
        clock.removeListener(listener);
    }

    public void updateConnectionType() {
        connectionType.set(connection instanceof OutboundConnection ?
                Res.get("settings.net.outbound") : Res.get("settings.net.inbound"));
    }

    public void updatePeerType() {
        if (connection.getPeerType() == Connection.PeerType.SEED_NODE)
            peerType.set(Res.get("settings.net.seedNode"));
        else if (connection.getPeerType() == Connection.PeerType.DIRECT_MSG_PEER)
            peerType.set(Res.get("settings.net.directPeer"));
        else
            peerType.set(Res.get("settings.net.peer"));
    }

    public String getCreationDate() {
        return formatter.formatDateTime(statistic.getCreationDate());
    }

    public String getOnionAddress() {
        return onionAddress.get();
    }

    public StringProperty onionAddressProperty() {
        return onionAddress;
    }

    public String getConnectionType() {
        return connectionType.get();
    }

    public StringProperty connectionTypeProperty() {
        return connectionType;
    }

    public String getPeerType() {
        return peerType.get();
    }

    public StringProperty peerTypeProperty() {
        return peerType;
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

    public String getRoundTripTime() {
        return roundTripTime.get();
    }

    public StringProperty roundTripTimeProperty() {
        return roundTripTime;
    }
}
