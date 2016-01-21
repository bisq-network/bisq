package io.bitsquare.p2p.messaging;

import io.bitsquare.p2p.NodeAddress;

public interface DecryptedMailListener {

    void onMailMessage(DecryptedMsgWithPubKey decryptedMsgWithPubKey, NodeAddress peerNodeAddress);
}
