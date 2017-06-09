package io.bitsquare.p2p.storage.mocks;

import io.bitsquare.p2p.storage.payload.StoragePayload;

import java.security.PublicKey;

public class MockData implements StoragePayload {
    public final String msg;
    public final PublicKey publicKey;
    public long ttl;

    public MockData(String msg, PublicKey publicKey) {
        this.msg = msg;
        this.publicKey = publicKey;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || this.getClass() != o.getClass()) return false;

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

    @Override
    public long getTTL() {
        return ttl;
    }

    @Override
    public PublicKey getOwnerPubKey() {
        return publicKey;
    }
}
