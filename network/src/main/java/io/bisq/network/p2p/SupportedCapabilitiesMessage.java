package io.bisq.network.p2p;

import javax.annotation.Nullable;
import java.util.List;

public interface SupportedCapabilitiesMessage {
    @Nullable
    List<Integer> getSupportedCapabilities();
}
