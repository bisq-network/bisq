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

package bisq.core.monitor;

import lombok.Getter;
import lombok.Setter;


public enum DoubleValueItem implements ReportingItem {
    Unspecified("", "Unspecified"),
    sentBytesPerSec("network", "sentBytesPerSec"),
    receivedBytesPerSec("network", "receivedBytesPerSec"),
    receivedMessagesPerSec("network", "receivedMessagesPerSec"),
    sentMessagesPerSec("network", "sentMessagesPerSec");


    @Getter
    @Setter
    private String key;
    @Getter
    @Setter
    private String group;
    @Getter
    @Setter
    private double value;

    DoubleValueItem(String group, String key) {
        this.group = group;
        this.key = key;
    }

    public DoubleValueItem withValue(double value) {
        setValue(value);
        return this;
    }

    public static DoubleValueItem from(String key, double value) {
        DoubleValueItem item;
        try {
            item = DoubleValueItem.valueOf(key);
        } catch (Throwable t) {
            item = DoubleValueItem.Unspecified;
            item.setKey(key);
        }

        item.setValue(value);
        return item;
    }

    @Override
    public protobuf.ReportingItem toProtoMessage() {
        return getBuilder().setDoubleValueItem(protobuf.DoubleValueItem.newBuilder()
                        .setValue(value))
                .build();
    }

    public static DoubleValueItem fromProto(protobuf.ReportingItem baseProto, protobuf.DoubleValueItem proto) {
        return DoubleValueItem.from(baseProto.getKey(), proto.getValue());
    }

    @Override
    public String getPath() {
        return group + "." + key;
    }


    @Override
    public String toString() {
        return name() + "= " + value;
    }
}
