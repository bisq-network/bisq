package io.bisq.network_messages.payload;

import io.bisq.network_messages.NodeAddress;
import io.bisq.network_messages.wire.Payload;

/**
 * Used for network_messages which require that the data owner is online.
 * <p>
 * This is used for the offers to avoid dead offers in case the offerer is in standby mode or the app has
 * terminated without sending the remove message (e.g. network connection lost or in case of a crash).
 */
public interface RequiresOwnerIsOnlinePayload extends Payload {
    /**
     * @return NodeAddress of the data owner
     */
    NodeAddress getOwnerNodeAddress();
}
