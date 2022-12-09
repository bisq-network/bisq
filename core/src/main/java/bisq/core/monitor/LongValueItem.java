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


public enum LongValueItem implements ReportingItem {
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
    @Setter
    private String key;
    @Getter
    @Setter
    private String group;
    @Getter
    @Setter
    private long value;

    LongValueItem(String group, String key) {
        this.group = group;
        this.key = key;
    }

    public LongValueItem withValue(long value) {
        setValue(value);
        return this;
    }

    public static LongValueItem from(String key, long value) {
        LongValueItem item;
        try {
            item = LongValueItem.valueOf(key);
        } catch (Throwable t) {
            item = LongValueItem.Unspecified;
            item.setKey(key);
        }

        item.setValue(value);
        return item;
    }

    @Override
    public protobuf.ReportingItem toProtoMessage() {
        return getBuilder().setLongValueItem(protobuf.LongValueItem.newBuilder()
                        .setValue(value))
                .build();
    }

    public static LongValueItem fromProto(protobuf.ReportingItem baseProto, protobuf.LongValueItem proto) {
        return LongValueItem.from(baseProto.getKey(), proto.getValue());
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
