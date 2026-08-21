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
public enum LongValueReportingItem implements ReportingItem {
    OfferPayload("data", "OfferPayload"),
    BsqSwapOfferPayload("data", "BsqSwapOfferPayload"),
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

    PreliminaryGetDataRequest("network", "PreliminaryGetDataRequest"),
    GetUpdatedDataRequest("network", "GetUpdatedDataRequest"),
    GetBlocksRequest("network", "GetBlocksRequest"),
    GetDaoStateHashesRequest("network", "GetDaoStateHashesRequest"),
    GetProposalStateHashesRequest("network", "GetProposalStateHashesRequest"),
    GetBlindVoteStateHashesRequest("network", "GetBlindVoteStateHashesRequest"),

    GetDataResponse("network", "GetDataResponse"),
    GetBlocksResponse("network", "GetBlocksResponse"),
    GetDaoStateHashesResponse("network", "GetDaoStateHashesResponse"),
    GetProposalStateHashesResponse("network", "GetProposalStateHashesResponse"),
    GetBlindVoteStateHashesResponse("network", "GetBlindVoteStateHashesResponse"),

    failedResponseClassName("network", "failedResponseClassName"),

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

    public static Optional<LongValueReportingItem> from(String key, long value) {
        try {
            LongValueReportingItem item = LongValueReportingItem.valueOf(key);
            item.setValue(value);
            return Optional.of(item);
        } catch (Throwable t) {
            log.warn("No enum value with {}", key);
            return Optional.empty();
        }
    }

    @Override
    public protobuf.ReportingItem toProtoMessage() {
        return getBuilder().setLongValueReportingItem(protobuf.LongValueReportingItem.newBuilder()
                        .setValue(value))
                .build();
    }

    public static Optional<LongValueReportingItem> fromProto(protobuf.ReportingItem baseProto,
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
