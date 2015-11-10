package io.bitsquare.p2p.mocks;

import io.bitsquare.app.Version;
import io.bitsquare.p2p.Address;
import io.bitsquare.p2p.messaging.MailboxMessage;
import io.bitsquare.p2p.storage.data.ExpirablePayload;

public class MockMailboxMessage implements MailboxMessage, ExpirablePayload {
    private final int networkId = Version.NETWORK_ID;
    public String msg;
    public Address senderAddress;
    public long ttl;

    public MockMailboxMessage(String msg, Address senderAddress) {
        this.msg = msg;
        this.senderAddress = senderAddress;
    }

    @Override
    public int networkId() {
        return networkId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof MockMailboxMessage)) return false;

        MockMailboxMessage that = (MockMailboxMessage) o;

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
    public Address getSenderAddress() {
        return senderAddress;
    }
}
