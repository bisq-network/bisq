package io.bisq.message;

import com.google.protobuf.Message;
import io.bisq.app.Version;
import io.bisq.common.wire.proto.Messages;

/**
 * Created by mike on 30/01/2017.
 */
public interface ToProtoBuffer {
    static Messages.Envelope.Builder getBaseEnvelope() {
        return Messages.Envelope.newBuilder().setP2PNetworkVersion(Version.P2P_NETWORK_VERSION);
    }

    Message toProtoBuf();
}
