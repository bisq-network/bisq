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

package bisq.seednode.reporting;

import java.util.Optional;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public enum DoubleValueReportingItem implements ReportingItem {
    sentBytesPerSec("network", "sentBytesPerSec"),
    receivedBytesPerSec("network", "receivedBytesPerSec"),
    receivedMessagesPerSec("network", "receivedMessagesPerSec"),
    sentMessagesPerSec("network", "sentMessagesPerSec");


    @Getter
    private final String key;
    @Getter
    private final String group;
    @Getter
    @Setter
    private double value;

    DoubleValueReportingItem(String group, String key) {
        this.group = group;
        this.key = key;
    }

    public DoubleValueReportingItem withValue(double value) {
        setValue(value);
        return this;
    }

    public static Optional<DoubleValueReportingItem> from(String key, double value) {
        try {
            DoubleValueReportingItem item = DoubleValueReportingItem.valueOf(key);
            item.setValue(value);
            return Optional.of(item);
        } catch (Throwable t) {
            log.warn("No enum value with {}", key);
            return Optional.empty();
        }
    }

    @Override
    public protobuf.ReportingItem toProtoMessage() {
        return getBuilder().setDoubleValueReportingItem(protobuf.DoubleValueReportingItem.newBuilder()
                        .setValue(value))
                .build();
    }

    public static Optional<DoubleValueReportingItem> fromProto(protobuf.ReportingItem baseProto,
                                                               protobuf.DoubleValueReportingItem proto) {
        return DoubleValueReportingItem.from(baseProto.getKey(), proto.getValue());
    }

    @Override
    public String getPath() {
        return group + "." + key;
    }


    @Override
    public String toString() {
        return name() + "=" + value;
    }
}
