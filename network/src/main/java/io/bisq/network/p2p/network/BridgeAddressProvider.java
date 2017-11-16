package io.bisq.network.p2p.network;

import org.jetbrains.annotations.Nullable;

import java.util.List;

public interface BridgeAddressProvider {
    @Nullable
    List<String> getBridgeAddresses();
}
