package io.bisq.p2p;

import io.bisq.NodeAddress;

public interface DecryptedDirectMessageListener {

    void onDirectMessage(DecryptedMsgWithPubKey decryptedMsgWithPubKey, NodeAddress peerNodeAddress);
}
