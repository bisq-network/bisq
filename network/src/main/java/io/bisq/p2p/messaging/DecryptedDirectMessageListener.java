package io.bisq.p2p.messaging;

import io.bisq.crypto.DecryptedMsgWithPubKey;
import io.bisq.p2p.NodeAddress;

public interface DecryptedDirectMessageListener {

    void onDirectMessage(DecryptedMsgWithPubKey decryptedMsgWithPubKey, NodeAddress peerNodeAddress);
}
