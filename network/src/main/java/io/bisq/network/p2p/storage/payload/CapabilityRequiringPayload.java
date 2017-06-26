package io.bisq.network.p2p.storage.payload;


import io.bisq.common.proto.network.NetworkPayload;

import java.util.List;

/**
 * Used for payloads which requires certain capability.
 * <p/>
 * This is used for TradeStatistics to be able to support old versions which don't know about that class.
 * We only send the data to nodes which are capable to handle that data (e.g. TradeStatistics supported from v. 0.4.9.1 on).
 */
public interface CapabilityRequiringPayload extends NetworkPayload {
    /**
     * @return Capabilities the other node need to support to receive that message
     */
    List<Integer> getRequiredCapabilities();
}
