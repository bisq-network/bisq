package io.bisq.network.p2p;

import io.bisq.common.network.NetworkEnvelope;

import javax.annotation.Nullable;
import java.util.ArrayList;

public interface SupportedCapabilitiesMsg extends NetworkEnvelope {
    @Nullable
    ArrayList<Integer> getSupportedCapabilities();
}
