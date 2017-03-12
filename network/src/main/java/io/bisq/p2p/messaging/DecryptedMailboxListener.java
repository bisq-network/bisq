package io.bisq.p2p.messaging;

import io.bisq.crypto.DecryptedMsgWithPubKey;
import io.bisq.p2p.NodeAddress;

public interface DecryptedMailboxListener {

    void onMailboxMessageAdded(DecryptedMsgWithPubKey decryptedMsgWithPubKey, NodeAddress senderNodeAddress);
}
