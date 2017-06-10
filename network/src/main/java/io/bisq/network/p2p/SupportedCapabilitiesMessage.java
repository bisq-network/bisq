package io.bisq.network.p2p;

import javax.annotation.Nullable;
import java.util.ArrayList;

public interface SupportedCapabilitiesMessage {
    @Nullable
    ArrayList<Integer> getSupportedCapabilities();
}
