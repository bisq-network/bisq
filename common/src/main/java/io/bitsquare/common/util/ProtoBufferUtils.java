package io.bitsquare.common.util;

import io.bitsquare.app.Version;
import io.bitsquare.common.wire.proto.Messages;

/**
 * Created by mike on 07/02/2017.
 */
public class ProtoBufferUtils {
    public static Messages.Envelope.Builder getBaseEnvelope() {
        return Messages.Envelope.newBuilder().setP2PNetworkVersion(Version.P2P_NETWORK_VERSION);
    }
}
