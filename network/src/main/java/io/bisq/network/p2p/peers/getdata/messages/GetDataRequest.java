package io.bisq.network.p2p.peers.getdata.messages;

import io.bisq.common.network.Msg;

import java.util.Set;

public interface GetDataRequest extends Msg {
    int getNonce();

    Set<byte[]> getExcludedKeys();
}
