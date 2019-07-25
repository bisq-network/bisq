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

import bisq.network.p2p.peers.keepalive.messages.KeepAliveMessage;

import bisq.common.proto.network.NetworkEnvelope;

import java.io.IOException;
import java.io.OutputStream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.concurrent.NotThreadSafe;

@NotThreadSafe
class ProtoOutputStream {
    private static final Logger log = LoggerFactory.getLogger(ProtoOutputStream.class);

    private final OutputStream delegate;
    private final Statistic statistic;

    ProtoOutputStream(OutputStream delegate, Statistic statistic) {
        this.delegate = delegate;
        this.statistic = statistic;
    }

    void writeEnvelope(NetworkEnvelope envelope) {
        try {
            writeEnvelopeOrThrow(envelope);
        } catch (IOException e) {
            log.error("Failed to write envelope", e);
            throw new BisqRuntimeException("Failed to write envelope", e);
        }
    }

    void onConnectionShutdown() {
        try {
            delegate.close();
        } catch (Throwable t) {
            log.error("Failed to close connection", t);
        }
    }

    private void writeEnvelopeOrThrow(NetworkEnvelope envelope) throws IOException {
        protobuf.NetworkEnvelope proto = envelope.toProtoNetworkEnvelope();
        proto.writeDelimitedTo(delegate);
        delegate.flush();

        statistic.addSentBytes(proto.getSerializedSize());
        statistic.addSentMessage(envelope);

        if (!(envelope instanceof KeepAliveMessage)) {
            statistic.updateLastActivityTimestamp();
        }
    }
}
