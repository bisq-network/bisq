package io.bisq.protobuffer.message.p2p.peers.getdata;

import io.bisq.protobuffer.message.Message;

import java.util.Set;

public interface GetDataRequest extends Message {
    int getNonce();

    Set<byte[]> getExcludedKeys();
}
