package io.bisq.network.p2p.mocks;

import io.bisq.common.app.Version;
import io.bisq.common.proto.network.NetworkEnvelope;
import io.bisq.generated.protobuffer.PB;
import io.bisq.network.p2p.MailboxMessage;
import io.bisq.network.p2p.NodeAddress;
import io.bisq.network.p2p.storage.payload.ExpirablePayload;
import lombok.EqualsAndHashCode;
import lombok.Value;
import org.apache.commons.lang3.NotImplementedException;

import java.util.UUID;

@EqualsAndHashCode(callSuper = true)
@Value
public final class MockMailboxPayload extends NetworkEnvelope implements MailboxMessage, ExpirablePayload {
    private final int messageVersion = Version.getP2PMessageVersion();
    public final String msg;
    public final NodeAddress senderNodeAddress;
    public long ttl = 0;
    private final String uid;

    public MockMailboxPayload(String msg, NodeAddress senderNodeAddress) {
        super(0);
        this.msg = msg;
        this.senderNodeAddress = senderNodeAddress;
        uid = UUID.randomUUID().toString();
    }


    @Override
    public PB.NetworkEnvelope toProtoNetworkEnvelope() {
        throw new NotImplementedException("toProtoNetworkEnvelope not impl.");
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

}
