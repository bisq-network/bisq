package io.bisq.protobuffer.message.p2p;

import io.bisq.protobuffer.message.Message;

import javax.annotation.Nullable;
import java.util.ArrayList;

public interface SupportedCapabilitiesMessage extends Message {
    @Nullable
    ArrayList<Integer> getSupportedCapabilities();
}
