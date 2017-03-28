package io.bisq.network.p2p;

public interface DecryptedDirectMessageListener {

    void onDirectMessage(DecryptedMsgWithPubKey decryptedMsgWithPubKey, NodeAddress peerNodeAddress);
}
