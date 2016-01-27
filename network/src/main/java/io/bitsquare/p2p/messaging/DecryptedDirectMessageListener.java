package io.bitsquare.p2p.messaging;

import io.bitsquare.p2p.NodeAddress;

public interface DecryptedDirectMessageListener {

    void onDirectMessage(DecryptedMsgWithPubKey decryptedMsgWithPubKey, NodeAddress peerNodeAddress);
}
