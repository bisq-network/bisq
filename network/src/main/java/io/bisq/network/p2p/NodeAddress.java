package io.bisq.network.p2p;

import io.bisq.common.crypto.Hash;
import io.bisq.common.proto.network.NetworkPayload;
import io.bisq.common.proto.persistable.PersistablePayload;
import io.bisq.common.util.JsonExclude;
import io.bisq.consensus.RestrictedByContractJson;
import io.bisq.generated.protobuffer.PB;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.util.regex.Pattern;

import static com.google.common.base.Preconditions.checkArgument;

@Getter
@EqualsAndHashCode
@Slf4j
public final class NodeAddress implements PersistablePayload, NetworkPayload, RestrictedByContractJson {
    private final String hostName;
    private final int port;

    @JsonExclude
    private byte[] addressPrefixHash;

    public NodeAddress(String hostName, int port) {
        this.hostName = hostName;
        this.port = port;
    }

    public NodeAddress(String fullAddress) {
        final String[] split = fullAddress.split(Pattern.quote(":"));
        checkArgument(split.length == 2, "fullAddress must contain ':'");
        this.hostName = split[0];
        this.port = Integer.parseInt(split[1]);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // PROTO BUFFER
    ///////////////////////////////////////////////////////////////////////////////////////////

    public PB.NodeAddress toProtoMessage() {
        return PB.NodeAddress.newBuilder().setHostName(hostName).setPort(port).build();
    }

    public static NodeAddress fromProto(PB.NodeAddress proto) {
        return new NodeAddress(proto.getHostName(), proto.getPort());
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // API
    ///////////////////////////////////////////////////////////////////////////////////////////

    public String getFullAddress() {
        return hostName + ":" + port;
    }

    public String getHostNameWithoutPostFix() {
        return hostName.replace(".onion", "");
    }

    // We use just a few chars from the full address to blur the potential receiver for sent network_messages
    public byte[] getAddressPrefixHash() {
        if (addressPrefixHash == null)
            addressPrefixHash = Hash.getSha256Hash(getFullAddress().substring(0, Math.min(2, getFullAddress().length())));
        return addressPrefixHash;
    }

    @Override
    public String toString() {
        return getFullAddress();
    }
}
