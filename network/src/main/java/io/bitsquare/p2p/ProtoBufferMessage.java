package io.bitsquare.p2p;

import io.bitsquare.common.wire.proto.Messages;

/**
 * Created by mike on 30/01/2017.
 */
public interface ProtoBufferMessage {
    Messages.Envelope toProtoBuf();
}
