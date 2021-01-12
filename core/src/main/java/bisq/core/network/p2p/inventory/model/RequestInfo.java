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

import java.util.HashMap;
import java.util.Map;

import lombok.Getter;
import lombok.Setter;
import lombok.Value;

import org.jetbrains.annotations.Nullable;

@Getter
public class RequestInfo {
    // Carries latest commit hash of feature changes (not latest commit as that is then the commit for editing that field)
    public static final String COMMIT_HASH = "c07d47a8";

    private final long requestStartTime;
    @Setter
    private long responseTime;
    @Nullable
    @Setter
    private String errorMessage;

    private final Map<InventoryItem, Data> dataMap = new HashMap<>();

    public RequestInfo(long requestStartTime) {
        this.requestStartTime = requestStartTime;
    }

    public String getDisplayValue(InventoryItem inventoryItem) {
        String value = getValue(inventoryItem);
        return value != null ? value : "n/a";
    }

    @Nullable
    public String getValue(InventoryItem inventoryItem) {
        return dataMap.containsKey(inventoryItem) ?
                dataMap.get(inventoryItem).getValue() :
                null;
    }

    public boolean hasError() {
        return errorMessage != null && !errorMessage.isEmpty();
    }

    @Value
    public static class Data {
        private final String value;
        @Nullable
        private final Double average;
        private final Double deviation;
        private final DeviationSeverity deviationSeverity;
        private final boolean persistentWarning;
        private final boolean persistentAlert;

        public Data(String value,
                    @Nullable Double average,
                    Double deviation,
                    DeviationSeverity deviationSeverity,
                    boolean persistentWarning,
                    boolean persistentAlert) {
            this.value = value;
            this.average = average;
            this.deviation = deviation;
            this.deviationSeverity = deviationSeverity;
            this.persistentWarning = persistentWarning;
            this.persistentAlert = persistentAlert;
        }

        @Override
        public String toString() {
            return "InventoryData{" +
                    "\n     value='" + value + '\'' +
                    ",\n     average=" + average +
                    ",\n     deviation=" + deviation +
                    ",\n     deviationSeverity=" + deviationSeverity +
                    ",\n     persistentWarning=" + persistentWarning +
                    ",\n     persistentAlert=" + persistentAlert +
                    "\n}";
        }
    }
}
