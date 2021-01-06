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

package bisq.network.p2p.network;

import bisq.network.p2p.BundleOfEnvelopes;
import bisq.network.p2p.InitialDataRequest;
import bisq.network.p2p.InitialDataResponse;
import bisq.network.p2p.PrefixedSealedAndSignedMessage;

import bisq.common.Timer;
import bisq.common.UserThread;
import bisq.common.proto.network.NetworkEnvelope;

import java.util.concurrent.TimeUnit;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

/**
 * Holds state of connection. Data is applied from message handlers which are called on UserThread, so that class
 * is in a single threaded context.
 */
@Slf4j
public class ConnectionState implements MessageListener {
    // We protect the INITIAL_DATA_EXCHANGE PeerType for max. 4 minutes in case not all expected initialDataRequests
    // and initialDataResponses have not been all sent/received. In case the PeerManager need to close connections
    // if it exceeds its limits the connectionCreationTimeStamp and lastInitialDataExchangeMessageTimeStamp can be
    // used to set priorities for closing connections.
    private static final long PEER_RESET_TIMER_DELAY_SEC = TimeUnit.MINUTES.toSeconds(4);
    private static final long COMPLETED_TIMER_DELAY_SEC = 10;

    // Number of expected requests in standard case. Can be different according to network conditions.
    // Is different for LiteDaoNodes and FullDaoNodes
    @Setter
    private static int expectedRequests = 6;

    private final Connection connection;

    @Getter
    private PeerType peerType = PeerType.PEER;
    @Getter
    private int numInitialDataRequests = 0;
    @Getter
    private int numInitialDataResponses = 0;
    @Getter
    private long lastInitialDataMsgTimeStamp;
    @Setter
    @Getter
    private boolean isSeedNode;

    private Timer peerTypeResetDueTimeoutTimer, initialDataExchangeCompletedTimer;

    public ConnectionState(Connection connection) {
        this.connection = connection;

        connection.addMessageListener(this);
    }

    public void shutDown() {
        connection.removeMessageListener(this);
        stopTimer();
    }

    @Override
    public void onMessage(NetworkEnvelope networkEnvelope, Connection connection) {
        if (networkEnvelope instanceof BundleOfEnvelopes) {
            ((BundleOfEnvelopes) networkEnvelope).getEnvelopes().forEach(this::onMessageSentOrReceived);
        } else {
            onMessageSentOrReceived(networkEnvelope);
        }
    }

    @Override
    public void onMessageSent(NetworkEnvelope networkEnvelope, Connection connection) {
        if (networkEnvelope instanceof BundleOfEnvelopes) {
            ((BundleOfEnvelopes) networkEnvelope).getEnvelopes().forEach(this::onMessageSentOrReceived);
        } else {
            onMessageSentOrReceived(networkEnvelope);
        }
    }

    private void onMessageSentOrReceived(NetworkEnvelope networkEnvelope) {
        if (networkEnvelope instanceof InitialDataRequest) {
            numInitialDataRequests++;
            onInitialDataExchange();
        } else if (networkEnvelope instanceof InitialDataResponse) {
            numInitialDataResponses++;
            onInitialDataExchange();
        } else if (networkEnvelope instanceof PrefixedSealedAndSignedMessage &&
                connection.getPeersNodeAddressOptional().isPresent()) {
            peerType = PeerType.DIRECT_MSG_PEER;
        }
    }

    private void onInitialDataExchange() {
        // If we have a higher prio type we do not handle it
        if (peerType == PeerType.DIRECT_MSG_PEER) {
            stopTimer();
            return;
        }

        peerType = PeerType.INITIAL_DATA_EXCHANGE;
        lastInitialDataMsgTimeStamp = System.currentTimeMillis();
        maybeResetInitialDataExchangeType();
        if (peerTypeResetDueTimeoutTimer == null) {
            peerTypeResetDueTimeoutTimer = UserThread.runAfter(this::resetInitialDataExchangeType, PEER_RESET_TIMER_DELAY_SEC);
        }
    }

    private void maybeResetInitialDataExchangeType() {
        if (numInitialDataResponses >= expectedRequests) {
            // We have received the expected messages from initial data requests. We delay a bit the reset
            // to give time for processing the response and more tolerance to edge cases where we expect more responses.
            // Reset to PEER does not mean disconnection as well, but just that this connection has lower priority and
            // runs higher risk for getting disconnected.
            if (initialDataExchangeCompletedTimer == null) {
                initialDataExchangeCompletedTimer = UserThread.runAfter(this::resetInitialDataExchangeType, COMPLETED_TIMER_DELAY_SEC);
            }
        }
    }

    private void resetInitialDataExchangeType() {
        // If we have a higher prio type we do not handle it
        if (peerType == PeerType.DIRECT_MSG_PEER) {
            stopTimer();
            return;
        }

        stopTimer();
        peerType = PeerType.PEER;
        log.info("We have changed the peerType from INITIAL_DATA_EXCHANGE to PEER as we have received all " +
                        "expected initial data responses at connection with peer {}/{}.",
                connection.getPeersNodeAddressOptional(), connection.getUid());
    }

    private void stopTimer() {
        if (peerTypeResetDueTimeoutTimer != null) {
            peerTypeResetDueTimeoutTimer.stop();
            peerTypeResetDueTimeoutTimer = null;
        }
        if (initialDataExchangeCompletedTimer != null) {
            initialDataExchangeCompletedTimer.stop();
            initialDataExchangeCompletedTimer = null;
        }
    }

    @Override
    public String toString() {
        return "ConnectionState{" +
                ",\n     peerType=" + peerType +
                ",\n     numInitialDataRequests=" + numInitialDataRequests +
                ",\n     numInitialDataResponses=" + numInitialDataResponses +
                ",\n     lastInitialDataMsgTimeStamp=" + lastInitialDataMsgTimeStamp +
                ",\n     isSeedNode=" + isSeedNode +
                ",\n     expectedRequests=" + expectedRequests +
                "\n}";
    }
}
