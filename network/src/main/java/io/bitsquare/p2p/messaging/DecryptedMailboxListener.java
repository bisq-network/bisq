package io.bitsquare.p2p.messaging;

import io.bitsquare.p2p.Address;

public interface DecryptedMailboxListener {

    void onMailboxMessageAdded(DecryptedMsgWithPubKey decryptedMsgWithPubKey, Address senderAddress);
}
