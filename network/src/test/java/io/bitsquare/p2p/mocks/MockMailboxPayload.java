package io.bitsquare.p2p.mocks;

import io.bitsquare.app.Version;
import io.bitsquare.p2p.NodeAddress;
import io.bitsquare.p2p.messaging.MailboxMessage;
import io.bitsquare.p2p.storage.payload.ExpirablePayload;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.UUID;

public final class MockMailboxPayload implements MailboxMessage, ExpirablePayload {
    private final int messageVersion = Version.getP2PMessageVersion();
    @Nullable
    private ArrayList<Integer> supportedCapabilities = Version.getCapabilities();

    @Override
    @Nullable
    public ArrayList<Integer> getSupportedCapabilities() {
        return supportedCapabilities;
    }

    public final String msg;
    public final NodeAddress senderNodeAddress;
    public long ttl;
    private String uid;

    public MockMailboxPayload(String msg, NodeAddress senderNodeAddress) {
        this.msg = msg;
        this.senderNodeAddress = senderNodeAddress;
        uid = UUID.randomUUID().toString();
    }

    @Override
    public int getMessageVersion() {
        return messageVersion;
    }

    @Override
    public String getUID() {
        return uid;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof MockMailboxPayload)) return false;

        MockMailboxPayload that = (MockMailboxPayload) o;

        return !(msg != null ? !msg.equals(that.msg) : that.msg != null);

    }

    @Override
    public int hashCode() {
        return msg != null ? msg.hashCode() : 0;
    }

    @Override
    public String toString() {
        return "MockData{" +
                "msg='" + msg + '\'' +
                '}';
    }

    @Override
    public long getTTL() {
        return ttl;
    }

    @Override
    public NodeAddress getSenderNodeAddress() {
        return senderNodeAddress;
    }
}
