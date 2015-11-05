package io.bitsquare.p2p.peer.messages;

import io.bitsquare.app.Version;
import io.bitsquare.p2p.Address;

public final class ChallengeMessage implements AuthenticationMessage {
    // That object is sent over the wire, so we need to take care of version compatibility.
    private static final long serialVersionUID = Version.NETWORK_PROTOCOL_VERSION;

    public final Address address;
    public final long requesterNonce;
    public final long challengerNonce;

    public ChallengeMessage(Address address, long requesterNonce, long challengerNonce) {
        this.address = address;
        this.requesterNonce = requesterNonce;
        this.challengerNonce = challengerNonce;
    }

    @Override
    public String toString() {
        return "ChallengeMessage{" +
                "address=" + address +
                ", requesterNonce=" + requesterNonce +
                ", challengerNonce=" + challengerNonce +
                '}';
    }
}
