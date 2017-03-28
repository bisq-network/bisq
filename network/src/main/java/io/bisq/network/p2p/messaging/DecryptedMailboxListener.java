package io.bisq.network.p2p.messaging;


import io.bisq.network.p2p.DecryptedMsgWithPubKey;
import io.bisq.network.p2p.NodeAddress;

public interface DecryptedMailboxListener {

    void onMailboxMessageAdded(DecryptedMsgWithPubKey decryptedMsgWithPubKey, NodeAddress senderNodeAddress);
}
