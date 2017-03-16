package io.bisq.p2p.messaging;


import io.bisq.NodeAddress;
import io.bisq.p2p.DecryptedMsgWithPubKey;

public interface DecryptedMailboxListener {

    void onMailboxMessageAdded(DecryptedMsgWithPubKey decryptedMsgWithPubKey, NodeAddress senderNodeAddress);
}
