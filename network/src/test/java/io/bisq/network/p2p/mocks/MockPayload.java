package io.bisq.network.p2p.mocks;

import io.bisq.common.app.Version;
import io.bisq.common.network.NetworkEnvelope;
import io.bisq.generated.protobuffer.PB;
import io.bisq.network.p2p.storage.payload.ExpirablePayload;
import sun.reflect.generics.reflectiveObjects.NotImplementedException;

public final class MockPayload implements NetworkEnvelope, ExpirablePayload {
    public final String msg;
    public long ttl;
    private final int messageVersion = Version.getP2PMessageVersion();

    public MockPayload(String msg) {
        this.msg = msg;
    }

    @Override
    public int getMsgVersion() {
        return messageVersion;
    }

    @Override
    public PB.NetworkEnvelope toProtoMsg() {
        throw new NotImplementedException();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof MockPayload)) return false;

        MockPayload that = (MockPayload) o;

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
