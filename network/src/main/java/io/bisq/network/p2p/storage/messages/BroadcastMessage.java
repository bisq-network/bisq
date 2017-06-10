package io.bisq.network.p2p.storage.messages;

import io.bisq.common.proto.network.NetworkEnvelope;
import lombok.EqualsAndHashCode;

@EqualsAndHashCode(callSuper = true)
public abstract class BroadcastMessage extends NetworkEnvelope {
    protected BroadcastMessage(int messageVersion) {
        super(messageVersion);
    }
}