package io.bisq.network.p2p;

import io.bisq.common.app.Version;
import io.bisq.common.crypto.Hash;
import io.bisq.common.network.NetworkPayload;
import io.bisq.common.persistable.PersistablePayload;
import io.bisq.generated.protobuffer.PB;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.util.regex.Pattern;

@EqualsAndHashCode
@Slf4j
public final class NodeAddress implements PersistablePayload, NetworkPayload {
    // That object is sent over the wire, so we need to take care of version compatibility.
    private static final long serialVersionUID = Version.P2P_NETWORK_VERSION;

    // Payload
    @Getter
    public final String hostName;
    @Getter
    public final int port;

    // Domain
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

    // We use just a few chars from the full address to blur the potential receiver for sent network_messages
    public byte[] getAddressPrefixHash() {
        if (addressPrefixHash == null)
            addressPrefixHash = Hash.getHash(getFullAddress().substring(0, Math.min(2, getFullAddress().length())));
        return addressPrefixHash;
    }

    public String getHostNameWithoutPostFix() {
        return hostName.replace(".onion", "");
    }

    public PB.NodeAddress toProtoMessage() {
        return PB.NodeAddress.newBuilder().setHostName(hostName).setPort(port).build();
    }

    public static NodeAddress fromProto(PB.NodeAddress nodeAddress){
        return new NodeAddress(nodeAddress.getHostName(), nodeAddress.getPort());
    }

    @Override
    public String toString() {
        return getFullAddress();
    }
}
