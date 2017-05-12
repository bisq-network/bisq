package io.bisq.network.p2p.peers.getdata.messages;

import io.bisq.common.network.NetworkEnvelope;
import io.bisq.network.p2p.ExtendedDataSizePermission;

import java.util.Set;

public interface GetDataRequest extends NetworkEnvelope, ExtendedDataSizePermission {
    int getNonce();

    Set<byte[]> getExcludedKeys();
}
