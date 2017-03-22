package io.bisq.network.p2p;

import io.bisq.protobuffer.payload.p2p.NodeAddress;

public interface DecryptedDirectMessageListener {

    void onDirectMessage(DecryptedMsgWithPubKey decryptedMsgWithPubKey, NodeAddress peerNodeAddress);
}
