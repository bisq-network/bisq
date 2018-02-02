package io.bisq.network.p2p.network;

import io.bisq.common.proto.network.NetworkEnvelope;

import javax.annotation.concurrent.ThreadSafe;
import java.io.OutputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@ThreadSafe
class SynchronizedProtoOutputStream extends ProtoOutputStream {
    private final ExecutorService executorService;

    SynchronizedProtoOutputStream(OutputStream delegate, Statistic statistic) {
        super(delegate, statistic);
        this.executorService = Executors.newSingleThreadExecutor();
    }

    @Override
    void writeEnvelope(NetworkEnvelope envelope) {
        executorService.submit(() -> super.writeEnvelope(envelope));
    }

    void onConnectionShutdown() {
        executorService.shutdownNow();
        super.onConnectionShutdown();
    }
}
