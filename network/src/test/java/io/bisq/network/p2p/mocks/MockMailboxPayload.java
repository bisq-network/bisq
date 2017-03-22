package io.bisq.network.p2p.mocks;

import io.bisq.common.app.Version;
import io.bisq.generated.protobuffer.PB;
import io.bisq.protobuffer.message.p2p.MailboxMessage;
import io.bisq.protobuffer.payload.ExpirablePayload;
import io.bisq.protobuffer.payload.p2p.NodeAddress;
import sun.reflect.generics.reflectiveObjects.NotImplementedException;

import java.util.UUID;

public final class MockMailboxPayload implements MailboxMessage, ExpirablePayload {
    private final int messageVersion = Version.getP2PMessageVersion();
    public final String msg;
    public final NodeAddress senderNodeAddress;
    public long ttl;
    private final String uid;

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
    public PB.Envelope toProto() {
        throw new NotImplementedException();
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
