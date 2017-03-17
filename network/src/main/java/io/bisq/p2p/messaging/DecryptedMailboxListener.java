package io.bisq.p2p.messaging;


import io.bisq.p2p.DecryptedMsgWithPubKey;
import io.bisq.payload.NodeAddress;

public interface DecryptedMailboxListener {

    void onMailboxMessageAdded(DecryptedMsgWithPubKey decryptedMsgWithPubKey, NodeAddress senderNodeAddress);
}
