package io.bisq.network.p2p;

import io.bisq.common.persistance.Msg;

import javax.annotation.Nullable;
import java.util.ArrayList;

public interface SupportedCapabilitiesMsg extends Msg {
    @Nullable
    ArrayList<Integer> getSupportedCapabilities();
}
