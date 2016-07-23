package io.bitsquare.p2p.peers.keepalive.messages;

import io.bitsquare.app.Version;
import io.bitsquare.p2p.Message;

import javax.annotation.Nullable;
import java.util.ArrayList;

public abstract class KeepAliveMessage implements Message {
    private final int messageVersion = Version.getP2PMessageVersion();

    @Override
    public int getMessageVersion() {
        return messageVersion;
    }

    @Nullable
    private ArrayList<Integer> supportedCapabilities = Version.getCapabilities();

    @Override
    @Nullable
    public ArrayList<Integer> getSupportedCapabilities() {
        return supportedCapabilities;
    }

    @Override
    public String toString() {
        return "KeepAliveMessage{" +
                "messageVersion=" + getMessageVersion() +
                '}';
    }
}
