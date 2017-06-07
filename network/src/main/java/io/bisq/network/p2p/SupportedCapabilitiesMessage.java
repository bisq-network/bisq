package io.bisq.network.p2p;

import io.bisq.common.proto.network.NetworkEnvelope;

import javax.annotation.Nullable;
import java.util.ArrayList;

public interface SupportedCapabilitiesMessage extends NetworkEnvelope {
    @Nullable
    ArrayList<Integer> getSupportedCapabilities();
}
