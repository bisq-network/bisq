package io.bisq.network_messages;

public interface DecryptedDirectMessageListener {

    void onDirectMessage(DecryptedMsgWithPubKey decryptedMsgWithPubKey, NodeAddress peerNodeAddress);
}
