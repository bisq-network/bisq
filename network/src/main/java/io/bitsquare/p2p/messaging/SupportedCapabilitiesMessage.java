package io.bitsquare.p2p.messaging;

import io.bitsquare.messages.Message;

import javax.annotation.Nullable;
import java.util.ArrayList;

public interface SupportedCapabilitiesMessage extends Message {
    @Nullable
    ArrayList<Integer> getSupportedCapabilities();
}
