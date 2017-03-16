package io.bisq.network_messages.p2p.messaging;

import io.bisq.network_messages.Message;

import javax.annotation.Nullable;
import java.util.ArrayList;

public interface SupportedCapabilitiesMessage extends Message {
    @Nullable
    ArrayList<Integer> getSupportedCapabilities();
}
