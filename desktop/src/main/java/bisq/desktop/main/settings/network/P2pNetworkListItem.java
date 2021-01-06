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

import bisq.desktop.util.DisplayUtils;

import bisq.core.locale.Res;
import bisq.core.util.FormattingUtils;

import bisq.network.p2p.network.Connection;
import bisq.network.p2p.network.ConnectionState;
import bisq.network.p2p.network.OutboundConnection;
import bisq.network.p2p.network.PeerType;
import bisq.network.p2p.network.Statistic;

import bisq.common.ClockWatcher;

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
    private final ClockWatcher clockWatcher;

    private final StringProperty lastActivity = new SimpleStringProperty();
    private final StringProperty sentBytes = new SimpleStringProperty();
    private final StringProperty receivedBytes = new SimpleStringProperty();
    private final StringProperty peerType = new SimpleStringProperty();
    private final StringProperty connectionType = new SimpleStringProperty();
    private final StringProperty roundTripTime = new SimpleStringProperty();
    private final StringProperty onionAddress = new SimpleStringProperty();
    private final ClockWatcher.Listener listener;

    P2pNetworkListItem(Connection connection, ClockWatcher clockWatcher) {
        this.connection = connection;
        this.clockWatcher = clockWatcher;
        this.statistic = connection.getStatistic();

        sentBytesSubscription = EasyBind.subscribe(statistic.sentBytesProperty(),
                e -> sentBytes.set(FormattingUtils.formatBytes((long) e)));
        receivedBytesSubscription = EasyBind.subscribe(statistic.receivedBytesProperty(),
                e -> receivedBytes.set(FormattingUtils.formatBytes((long) e)));
        onionAddressSubscription = EasyBind.subscribe(connection.getPeersNodeAddressProperty(),
                nodeAddress -> onionAddress.set(nodeAddress != null ? nodeAddress.getFullAddress() : Res.get("settings.net.notKnownYet")));
        roundTripTimeSubscription = EasyBind.subscribe(statistic.roundTripTimeProperty(),
                roundTripTime -> this.roundTripTime.set((int) roundTripTime == 0 ? "-" : roundTripTime + " ms"));

        listener = new ClockWatcher.Listener() {
            @Override
            public void onSecondTick() {
                onLastActivityChanged(statistic.getLastActivityTimestamp());
                updatePeerType();
                updateConnectionType();
            }

            @Override
            public void onMinuteTick() {
            }
        };
        clockWatcher.addListener(listener);
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
        clockWatcher.removeListener(listener);
    }

    public void updateConnectionType() {
        connectionType.set(connection instanceof OutboundConnection ?
                Res.get("settings.net.outbound") : Res.get("settings.net.inbound"));
    }

    public void updatePeerType() {
        ConnectionState connectionState = connection.getConnectionState();
        if (connectionState.getPeerType() == PeerType.DIRECT_MSG_PEER) {
            peerType.set(Res.get("settings.net.directPeer"));
        } else {
            String peerOrSeed = connectionState.isSeedNode() ? Res.get("settings.net.seedNode") : Res.get("settings.net.peer");
            if (connectionState.getPeerType() == PeerType.INITIAL_DATA_EXCHANGE) {
                peerType.set(Res.get("settings.net.initialDataExchange", peerOrSeed));
            } else {
                peerType.set(peerOrSeed);
            }
        }
    }

    public String getCreationDate() {
        return DisplayUtils.formatDateTime(statistic.getCreationDate());
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
