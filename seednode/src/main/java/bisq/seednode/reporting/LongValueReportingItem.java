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

import lombok.Getter;
import lombok.Setter;


public enum LongValueReportingItem implements ReportingItem {
    Unspecified("", "Unspecified"),
    OfferPayload("data", "OfferPayload"),
    MailboxStoragePayload("data", "MailboxStoragePayload"),
    TradeStatistics3("data", "TradeStatistics3"),
    AccountAgeWitness("data", "AccountAgeWitness"),
    SignedWitness("data", "SignedWitness"),
    Alert("data", "Alert"),
    Filter("data", "Filter"),
    Arbitrator("data", "Arbitrator"),
    Mediator("data", "Mediator"),
    RefundAgent("data", "RefundAgent"),

    TempProposalPayload("dao", "TempProposalPayload"),
    ProposalPayload("dao", "ProposalPayload"),
    BlindVotePayload("dao", "BlindVotePayload"),
    daoStateChainHeight("dao", "daoStateChainHeight"),
    blockTimeIsSec("dao", "blockTimeIsSec"),

    maxConnections("network", "maxConnections"),
    numConnections("network", "numConnections"),
    peakNumConnections("network", "peakNumConnections"),
    numAllConnectionsLostEvents("network", "numAllConnectionsLostEvents"),
    sentBytes("network", "sentBytes"),
    receivedBytes("network", "receivedBytes"),

    usedMemoryInMB("node", "usedMemoryInMB"),
    totalMemoryInMB("node", "totalMemoryInMB"),
    jvmStartTimeInSec("node", "jvmStartTimeInSec");

    @Getter
    private final String key;
    @Getter
    private final String group;
    @Getter
    @Setter
    private long value;

    LongValueReportingItem(String group, String key) {
        this.group = group;
        this.key = key;
    }

    public LongValueReportingItem withValue(long value) {
        setValue(value);
        return this;
    }

    public static LongValueReportingItem from(String key, long value) {
        LongValueReportingItem item;
        try {
            item = LongValueReportingItem.valueOf(key);
        } catch (Throwable t) {
            item = Unspecified;
        }

        item.setValue(value);
        return item;
    }

    @Override
    public protobuf.ReportingItem toProtoMessage() {
        return getBuilder().setLongValueReportingItem(protobuf.LongValueReportingItem.newBuilder()
                        .setValue(value))
                .build();
    }

    public static LongValueReportingItem fromProto(protobuf.ReportingItem baseProto,
                                                   protobuf.LongValueReportingItem proto) {
        return LongValueReportingItem.from(baseProto.getKey(), proto.getValue());
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
