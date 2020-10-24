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

package bisq.network.p2p.storage.payload;

import java.util.Date;

/**
 * Marker interface for PersistableNetworkPayloads which get truncated at initial data response in case we exceed
 * the max items defined for that type of object. The truncation happens on a sorted list where we use the date for
 * sorting so in case of truncation we prefer to receive the most recent data.
 */
public interface DateSortedTruncatablePayload extends PersistableNetworkPayload {
    Date getDate();

    int maxItems();
}
