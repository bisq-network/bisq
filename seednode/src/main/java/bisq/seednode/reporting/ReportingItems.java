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

import bisq.common.proto.network.NetworkPayload;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

import java.util.ArrayList;
import java.util.Optional;
import java.util.stream.Collectors;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ReportingItems extends ArrayList<ReportingItem> implements NetworkPayload {
    @Getter
    private final String address;

    public ReportingItems(String address) {
        this.address = address;
    }

    @Override
    public protobuf.ReportingItems toProtoMessage() {
        return protobuf.ReportingItems.newBuilder()
                .setAddress(address)
                .addAllReportingItem(this.stream()
                        .map(ReportingItem::toProtoMessage)
                        .collect(Collectors.toList()))
                .build();
    }

    public static ReportingItems fromProto(protobuf.ReportingItems proto) {
        ReportingItems reportingItems = new ReportingItems(proto.getAddress());
        reportingItems.addAll(proto.getReportingItemList().stream()
                .map(ReportingItem::fromProto)
                .filter(Optional::isPresent)
                .map(Optional::get)
                .collect(Collectors.toList()));
        return reportingItems;
    }

    public byte[] toProtoMessageAsBytes() {
        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            toProtoMessage().writeDelimitedTo(outputStream);
            return outputStream.toByteArray();
        } catch (Throwable t) {
            log.error("Error at ", t);
            throw new RuntimeException(t);
        }
    }

    public static ReportingItems fromProtoMessageAsBytes(byte[] protoAsBytes) {
        try (ByteArrayInputStream inputStream = new ByteArrayInputStream(protoAsBytes)) {
            protobuf.ReportingItems proto = protobuf.ReportingItems.parseDelimitedFrom(inputStream);
            return fromProto(proto);
        } catch (Throwable t) {
            log.error("Error at ", t);
            throw new RuntimeException(t);
        }
    }
}
