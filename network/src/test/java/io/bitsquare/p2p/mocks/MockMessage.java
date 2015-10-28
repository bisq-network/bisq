package io.bitsquare.p2p.mocks;

import io.bitsquare.p2p.Message;
import io.bitsquare.p2p.storage.data.ExpirablePayload;

public class MockMessage implements Message, ExpirablePayload {
    public String msg;
    public long ttl;

    public MockMessage(String msg) {
        this.msg = msg;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof MockMessage)) return false;

        MockMessage that = (MockMessage) o;

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
