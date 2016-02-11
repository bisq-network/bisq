package io.bitsquare.p2p.mocks;

import io.bitsquare.app.Version;
import io.bitsquare.p2p.Message;
import io.bitsquare.p2p.storage.data.ExpirableMessage;

public final class MockMessage implements Message, ExpirableMessage {
    public final String msg;
    public long ttl;
    private final int messageVersion = Version.getP2PMessageVersion();

    public MockMessage(String msg) {
        this.msg = msg;
    }

    @Override
    public int getMessageVersion() {
        return messageVersion;
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
