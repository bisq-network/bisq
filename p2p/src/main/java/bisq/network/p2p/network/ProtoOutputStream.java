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

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.concurrent.ThreadSafe;

@ThreadSafe
class ProtoOutputStream {
    private static final Logger log = LoggerFactory.getLogger(ProtoOutputStream.class);

    private final OutputStream outputStream;
    private final Statistic statistic;

    private final AtomicBoolean isConnectionActive = new AtomicBoolean(true);
    private final Lock lock = new ReentrantLock();

    ProtoOutputStream(OutputStream outputStream, Statistic statistic) {
        this.outputStream = outputStream;
        this.statistic = statistic;
    }

    void writeEnvelope(NetworkEnvelope envelope) {
        lock.lock();

        try {
            writeEnvelopeOrThrow(envelope);
        } catch (IOException e) {
            if (!isConnectionActive.get()) {
                // Connection was closed by us.
                return;
            }

            log.error("Failed to write envelope", e);
            throw new BisqRuntimeException("Failed to write envelope", e);

        } finally {
            lock.unlock();
        }
    }

    void onConnectionShutdown() {
        isConnectionActive.set(false);

        boolean acquiredLock = tryToAcquireLock();
        if (!acquiredLock) {
            return;
        }

        try {
            outputStream.close();
        } catch (Throwable t) {
            log.error("Failed to close connection", t);

        } finally {
            lock.unlock();
        }
    }

    private void writeEnvelopeOrThrow(NetworkEnvelope envelope) throws IOException {
        long ts = System.currentTimeMillis();
        protobuf.NetworkEnvelope proto = envelope.toProtoNetworkEnvelope();
        proto.writeDelimitedTo(outputStream);
        outputStream.flush();
        long duration = System.currentTimeMillis() - ts;
        if (duration > 10000) {
            log.info("Sending {} to peer took {} sec.", envelope.getClass().getSimpleName(), duration / 1000d);
        }
        statistic.addSentBytes(proto.getSerializedSize());
        statistic.addSentMessage(envelope);

        if (!(envelope instanceof KeepAliveMessage)) {
            statistic.updateLastActivityTimestamp();
        }
    }

    private boolean tryToAcquireLock() {
        long shutdownTimeout = Connection.getShutdownTimeout();
        try {
            return lock.tryLock(shutdownTimeout, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            return false;
        }
    }
}
