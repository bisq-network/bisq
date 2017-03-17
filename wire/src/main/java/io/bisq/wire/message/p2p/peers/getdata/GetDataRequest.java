package io.bisq.wire.message.p2p.peers.getdata;

import io.bisq.wire.message.Message;

import java.util.Set;

public interface GetDataRequest extends Message {
    int getNonce();

    Set<byte[]> getExcludedKeys();
}
