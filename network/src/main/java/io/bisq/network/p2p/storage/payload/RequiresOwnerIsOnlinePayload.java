package io.bisq.network.p2p.storage.payload;


import io.bisq.common.proto.network.NetworkPayload;
import io.bisq.network.p2p.NodeAddress;

/**
 * Used for network_messages which require that the data owner is online.
 * <p/>
 * This is used for the offers to avoid dead offers in case the maker is in standby mode or the app has
 * terminated without sending the remove message (e.g. network connection lost or in case of a crash).
 */
public interface RequiresOwnerIsOnlinePayload extends NetworkPayload {
    /**
     * @return NodeAddress of the data owner
     */
    NodeAddress getOwnerNodeAddress();
}
