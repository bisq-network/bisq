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

package bisq.core.network.p2p.inventory.model;

import java.util.Map;

import lombok.Getter;
import lombok.Setter;

import org.jetbrains.annotations.Nullable;

@Getter
public class RequestInfo {
    private final long requestStartTime;
    @Setter
    private long responseTime;
    @Nullable
    @Setter
    private Map<InventoryItem, String> inventory;
    @Nullable
    @Setter
    private String errorMessage;

    public RequestInfo(long requestStartTime) {
        this.requestStartTime = requestStartTime;
    }

    public String getDisplayValue(InventoryItem inventoryItem) {
        String value = getValue(inventoryItem);
        return value != null ? value : "n/a";
    }

    @Nullable
    public String getValue(InventoryItem inventoryItem) {
        return inventory != null && inventory.containsKey(inventoryItem) ?
                inventory.get(inventoryItem) :
                null;
    }
}
