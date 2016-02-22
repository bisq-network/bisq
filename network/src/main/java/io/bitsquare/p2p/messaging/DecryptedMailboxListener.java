package io.bitsquare.p2p.messaging;

import io.bitsquare.crypto.DecryptedMsgWithPubKey;
import io.bitsquare.p2p.NodeAddress;

public interface DecryptedMailboxListener {

    void onMailboxMessageAdded(DecryptedMsgWithPubKey decryptedMsgWithPubKey, NodeAddress senderNodeAddress);
}
