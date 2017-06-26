package io.bisq.network.p2p;

public interface DecryptedDirectMessageListener {

    void onDirectMessage(DecryptedMessageWithPubKey decryptedMessageWithPubKey, @SuppressWarnings("UnusedParameters") NodeAddress peerNodeAddress);
}
