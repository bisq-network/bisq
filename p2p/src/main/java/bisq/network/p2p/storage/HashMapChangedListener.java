/*
 * This file is part of Bisq.
 *
 * Bisq is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * Bisq is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Bisq. If not, see <http://www.gnu.org/licenses/>.
 */

package bisq.network.p2p.storage;

import bisq.network.p2p.storage.payload.ProtectedStorageEntry;

public interface HashMapChangedListener {
    void onAdded(ProtectedStorageEntry data);

    @SuppressWarnings("UnusedParameters")
    void onRemoved(ProtectedStorageEntry data);

    // We process all expired entries after a delay (60 s) after onBootstrapComplete.
    // We notify listeners of start and completion so they can optimize to only update after batch processing is done.
    default void onBatchRemoveExpiredDataStarted() {
    }

    default void onBatchRemoveExpiredDataCompleted() {
    }
}
