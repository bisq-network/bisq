package io.bitsquare.p2p;

import io.bitsquare.app.Version;
import io.bitsquare.common.crypto.Hash;
import io.bitsquare.common.persistance.Persistable;
import io.bitsquare.common.wire.Payload;
import io.bitsquare.common.wire.proto.Messages;
import lombok.Getter;

import java.util.regex.Pattern;

public final class NodeAddress implements Persistable, Payload {
    // That object is sent over the wire, so we need to take care of version compatibility.
    private static final long serialVersionUID = Version.P2P_NETWORK_VERSION;

    @Getter
    public final String hostName;
    @Getter
    public final int port;
    transient private byte[] addressPrefixHash;

    public NodeAddress(String hostName, int port) {
        this.hostName = hostName;
        this.port = port;
    }

    public NodeAddress(String fullAddress) {
        final String[] split = fullAddress.split(Pattern.quote(":"));
        this.hostName = split[0];
        this.port = Integer.parseInt(split[1]);
    }

    public String getFullAddress() {
        return hostName + ":" + port;
    }

    // We use just a few chars from the full address to blur the potential receiver for sent messages
    public byte[] getAddressPrefixHash() {
        if (addressPrefixHash == null)
            addressPrefixHash = Hash.getHash(getFullAddress().substring(0, Math.min(2, getFullAddress().length())));
        return addressPrefixHash;
    }

    public String getHostNameWithoutPostFix() {
        return hostName.replace(".onion", "");
    }


    public Messages.NodeAddress toProtoBuf() {
        return Messages.NodeAddress.newBuilder().setHostName(hostName).setPort(port).build();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof NodeAddress)) return false;

        NodeAddress nodeAddress = (NodeAddress) o;

        //noinspection SimplifiableIfStatement
        if (port != nodeAddress.port) return false;
        return !(hostName != null ? !hostName.equals(nodeAddress.hostName) : nodeAddress.hostName != null);

    }

    @Override
    public int hashCode() {
        int result = hostName != null ? hostName.hashCode() : 0;
        result = 31 * result + port;
        return result;
    }

    @Override
    public String toString() {
        return getFullAddress();
    }
}
