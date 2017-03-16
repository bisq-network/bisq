package io.bisq.network_messages;

import com.google.protobuf.Message;

/**
 * Created by mike on 30/01/2017.
 */
public interface ToProtoBuffer {
    Message toProtoBuf();
}
