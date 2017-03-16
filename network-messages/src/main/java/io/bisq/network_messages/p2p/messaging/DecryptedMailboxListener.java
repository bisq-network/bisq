package io.bisq.network_messages.p2p.messaging;


import io.bisq.network_messages.DecryptedMsgWithPubKey;
import io.bisq.network_messages.NodeAddress;

public interface DecryptedMailboxListener {

    void onMailboxMessageAdded(DecryptedMsgWithPubKey decryptedMsgWithPubKey, NodeAddress senderNodeAddress);
}
