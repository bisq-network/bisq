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

package bisq.btcnodemonitor.btc;

import org.bitcoinj.core.Peer;
import org.bitcoinj.core.PeerAddress;
import org.bitcoinj.core.VersionMessage;

import com.google.common.base.Joiner;

import java.net.InetAddress;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import lombok.Getter;
import lombok.Setter;

@Getter
public class PeerConncetionInfo {
    private final List<ConnectionAttempt> connectionAttempts = new ArrayList<>();
    private final PeerAddress peerAddress;
    private final Runnable onChangeHandler;
    private Optional<ConnectionAttempt> currentConnectionAttempt = Optional.empty();

    public PeerConncetionInfo(PeerAddress peerAddress, Runnable onChangeHandler) {
        this.peerAddress = peerAddress;
        this.onChangeHandler = onChangeHandler;
    }

    public ConnectionAttempt newConnectionAttempt(Peer peer) {
        currentConnectionAttempt = Optional.of(new ConnectionAttempt(peer, onChangeHandler));
        connectionAttempts.add(currentConnectionAttempt.get());
        onChangeHandler.run();
        return currentConnectionAttempt.get();
    }


    public String getAddress() {
        InetAddress inetAddress = peerAddress.getAddr();
        if (inetAddress != null) {
            return inetAddress.getHostAddress();
        } else {
            return peerAddress.getHostname();
        }
    }

    public String getShortId() {
        String address = getAddress();
        int endIndex = Math.min(address.length(), 12);
        return address.substring(0, endIndex) + "...";
    }

    public int getNumConnectionAttempts() {
        return connectionAttempts.size();
    }

    public int getNumConnections() {
        return (int) connectionAttempts.stream().filter(e -> e.isConnected).count();
    }

    public int getNumDisconnections() {
        return (int) connectionAttempts.stream().filter(e -> !e.isConnected).count();
    }

    public int getNumFailures() {
        return (int) connectionAttempts.stream().filter(e -> e.exception.isPresent()).count();
    }

    public int getNumSuccess() {
        return (int) connectionAttempts.stream().filter(e -> e.versionMessage.isPresent()).count();
    }

    public List<ConnectionAttempt> getReverseConnectionAttempts() {
        List<ConnectionAttempt> reverseConnectionAttempts = new ArrayList<>(connectionAttempts);
        Collections.reverse(reverseConnectionAttempts);
        return reverseConnectionAttempts;
    }

    public Optional<ConnectionAttempt> getLastSuccessfulConnected() {
        return getReverseConnectionAttempts().stream().filter(e -> e.versionMessage.isPresent()).findFirst();
    }

    public int getIndex(ConnectionAttempt connectionAttempt) {
        return connectionAttempts.indexOf(connectionAttempt);
    }

    public long getLastSuccessfulConnectTime() {
        return getReverseConnectionAttempts().stream().filter(e -> e.versionMessage.isPresent()).findFirst()
                .map(ConnectionAttempt::getDurationUntilConnection)
                .orElse(0L);
    }

    public double getAverageTimeToConnect() {
        return connectionAttempts.stream().mapToLong(ConnectionAttempt::getDurationUntilConnection).average().orElse(0d);
    }

    public Optional<String> getLastExceptionMessage() {
        return getLastAttemptWithException()
                .flatMap(ConnectionAttempt::getException)
                .map(Throwable::getMessage);
    }

    public Optional<ConnectionAttempt> getLastAttemptWithException() {
        return getReverseConnectionAttempts().stream()
                .filter(e -> e.exception.isPresent())
                .findFirst();
    }

    public String getAllExceptionMessages() {
        return Joiner.on(",\n")
                .join(getReverseConnectionAttempts().stream()
                        .filter(e -> e.exception.isPresent())
                        .flatMap(e -> e.getException().stream())
                        .map(Throwable::getMessage)
                        .collect(Collectors.toList()));
    }

    public double getFailureRate() {
        if (getNumConnectionAttempts() == 0) {
            return 0;
        }
        return getNumFailures() / (double) getNumConnectionAttempts();
    }

    @Override
    public String toString() {
        return getShortId();
    }

    @Getter
    public static class ConnectionAttempt {
        private final Peer peer;
        private final Runnable updateHandler;
        private final long connectTs;
        private boolean isConnected;
        @Setter
        private long connectionStartedTs;
        @Setter
        private long connectionSuccessTs;
        @Setter
        private long durationUntilConnection;
        @Setter
        private long durationUntilDisConnection;
        @Setter
        private long durationUntilFailure;
        private Optional<Throwable> exception = Optional.empty();
        private Optional<VersionMessage> versionMessage = Optional.empty();

        public ConnectionAttempt(Peer peer, Runnable updateHandler) {
            this.peer = peer;
            this.updateHandler = updateHandler;
            connectTs = System.currentTimeMillis();
        }

        public void onConnected() {
            // We clone to avoid change of fields when disconnect happens
            VersionMessage peerVersionMessage = peer.getPeerVersionMessage().duplicate();
            versionMessage = Optional.of(peerVersionMessage);
            isConnected = true;
            updateHandler.run();
        }

        public void onDisconnected() {
            isConnected = false;
            updateHandler.run();
        }

        public void onException(Throwable exception) {
            this.exception = Optional.of(exception);
            isConnected = false;
            updateHandler.run();
        }
    }
}
