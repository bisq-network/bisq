package io.bisq.messages.p2p.messaging;


import io.bisq.messages.DecryptedMsgWithPubKey;
import io.bisq.messages.NodeAddress;

public interface DecryptedMailboxListener {

    void onMailboxMessageAdded(DecryptedMsgWithPubKey decryptedMsgWithPubKey, NodeAddress senderNodeAddress);
}
