package io.bitsquare.p2p.messaging;

import io.bitsquare.p2p.Address;

public interface DecryptedMailListener {

    void onMailMessage(DecryptedMessageWithPubKey decryptedMessageWithPubKey, Address peerAddress);
}
