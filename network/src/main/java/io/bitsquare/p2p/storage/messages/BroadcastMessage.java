package io.bitsquare.p2p.storage.messages;

import io.bitsquare.app.Version;
import io.bitsquare.p2p.Message;

import javax.annotation.Nullable;
import java.util.ArrayList;

public abstract class BroadcastMessage implements Message {
    private final int messageVersion = Version.getP2PMessageVersion();
    @Nullable
    private ArrayList<Integer> supportedCapabilities = Version.getCapabilities();

    @Override
    @Nullable
    public ArrayList<Integer> getSupportedCapabilities() {
        return supportedCapabilities;
    }

    @Override
    public int getMessageVersion() {
        return messageVersion;
    }

    @Override
    public String toString() {
        return "DataBroadcastMessage{" +
                "messageVersion=" + messageVersion +
                '}';
    }
}