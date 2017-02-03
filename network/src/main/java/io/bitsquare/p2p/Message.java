package io.bitsquare.p2p;

import io.bitsquare.common.wire.proto.Messages;

import java.io.Serializable;

public interface Message extends Serializable {
    int getMessageVersion();

    //Messages.Envelope toProtoBuf();
}
