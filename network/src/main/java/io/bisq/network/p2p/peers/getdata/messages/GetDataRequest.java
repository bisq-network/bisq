package io.bisq.network.p2p.peers.getdata.messages;

import io.bisq.common.proto.network.NetworkEnvelope;
import io.bisq.network.p2p.ExtendedDataSizePermission;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

import java.util.Set;

@EqualsAndHashCode(callSuper = true)
@Getter
@ToString
public abstract class GetDataRequest extends NetworkEnvelope implements ExtendedDataSizePermission {
    protected final int nonce;
    // Keys for ProtectedStorageEntry items to be excluded from the request because the peer has them already
    protected final Set<byte[]> excludedKeys;

    public GetDataRequest(int messageVersion,
                          int nonce,
                          Set<byte[]> excludedKeys) {
        super(messageVersion);
        this.nonce = nonce;
        this.excludedKeys = excludedKeys;
    }
}
