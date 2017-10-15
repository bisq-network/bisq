package io.bisq.network.p2p.storage.payload;

import io.bisq.common.proto.network.NetworkPayload;
import io.bisq.common.proto.persistable.PersistablePayload;

/**
 * Marker interface for NetworkPayload which gets persisted in EntryMap.
 */
public interface PersistableNetworkPayload extends NetworkPayload, PersistablePayload {
}
