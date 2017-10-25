package io.bisq.network.p2p.storage.mocks;

import io.bisq.generated.protobuffer.PB;
import io.bisq.network.p2p.storage.payload.ProtectedStoragePayload;
import org.apache.commons.lang3.NotImplementedException;

import javax.annotation.Nullable;
import java.security.PublicKey;
import java.util.Map;

@SuppressWarnings("ALL")
public class MockData implements ProtectedStoragePayload {
    public final String msg;
    public final PublicKey publicKey;
    public long ttl;

    @Nullable
    private Map<String, String> extraDataMap;

    public MockData(String msg, PublicKey publicKey) {
        this.msg = msg;
        this.publicKey = publicKey;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof MockData)) return false;

        MockData that = (MockData) o;

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

    @Nullable
    @Override
    public Map<String, String> getExtraDataMap() {
        return extraDataMap;
    }

    @Override
    public long getTTL() {
        return ttl;
    }

    @Override
    public PublicKey getOwnerPubKey() {
        return publicKey;
    }

    @Override
    public PB.ProtectedMailboxStorageEntry toProtoMessage() {
        throw new NotImplementedException("toProtoMessage not impl.");
    }
}
