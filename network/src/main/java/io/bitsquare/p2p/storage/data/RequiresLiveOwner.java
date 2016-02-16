package io.bitsquare.p2p.storage.data;

import io.bitsquare.p2p.NodeAddress;

import java.io.Serializable;

/**
 * Used for messages which require that the data owner is online.
 * <p>
 * This is used for the offers to avoid dead offers in case the offerer is in sleep/hibernate mode or the app has
 * terminated without sending the remove message (e.g. in case of a crash).
 */
public interface RequiresLiveOwner extends Serializable {
    /**
     * @return NodeAddress of the data owner
     */
    NodeAddress getOwnerNodeAddress();
}
