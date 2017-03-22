package io.bisq.protobuffer.message;


import io.bisq.generated.protobuffer.PB;
import io.bisq.protobuffer.Marshaller;

import java.io.Serializable;

public interface Message extends Serializable, Marshaller {
    int getMessageVersion();

    PB.Envelope toProto();
}
