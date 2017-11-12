package io.bisq.network.p2p.storage.payload;

/**
 * Interface for PersistableNetworkPayload which only get added if the date is inside a tolerance range.
 * Used for AccountAgeWitness.
 */
public interface DateTolerantPayload extends PersistableNetworkPayload {
    boolean isDateInTolerance();
}
