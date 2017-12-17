/*
 * This file is part of bisq.
 *
 * bisq is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * bisq is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with bisq. If not, see <http://www.gnu.org/licenses/>.
 */

package io.bisq.seednode_monitor;

import io.bisq.network.p2p.NodeAddress;
import javafx.beans.property.ReadOnlyStringProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;

import javax.inject.Inject;
import java.util.HashMap;

public class MetricsByNodeAddressMap extends HashMap<NodeAddress, Metrics> {
    private StringProperty resultAsStringProperty = new SimpleStringProperty();

    @Inject
    public MetricsByNodeAddressMap() {
    }

    public ReadOnlyStringProperty resultAsStringProperty() {
        return resultAsStringProperty;
    }

    public void setResultAsString(String result) {
        this.resultAsStringProperty.set(result);
    }
}
