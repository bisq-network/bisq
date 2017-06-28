package io.bisq.network.p2p.messaging;


import io.bisq.network.p2p.DecryptedMessageWithPubKey;
import io.bisq.network.p2p.NodeAddress;

public interface DecryptedMailboxListener {

    void onMailboxMessageAdded(DecryptedMessageWithPubKey decryptedMessageWithPubKey, @SuppressWarnings("UnusedParameters") NodeAddress senderNodeAddress);
}
