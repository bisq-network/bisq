package io.bisq.wire.message.p2p;

import io.bisq.wire.message.Message;

import javax.annotation.Nullable;
import java.util.ArrayList;

public interface SupportedCapabilitiesMessage extends Message {
    @Nullable
    ArrayList<Integer> getSupportedCapabilities();
}
