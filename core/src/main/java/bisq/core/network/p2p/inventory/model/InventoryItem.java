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

import java.util.Collection;
import java.util.List;
import java.util.Map;

import lombok.Getter;

import org.jetbrains.annotations.Nullable;

public enum InventoryItem {
    // Percentage deviation
    OfferPayload("OfferPayload",
            true,
            new DeviationByPercentage(0.9, 1.1, 0.95, 1.05)),
    MailboxStoragePayload("MailboxStoragePayload",
            true,
            new DeviationByPercentage(0.9, 1.1, 0.95, 1.05)),
    TradeStatistics3("TradeStatistics3",
            true,
            new DeviationByPercentage(0.9, 1.1, 0.95, 1.05)),
    AccountAgeWitness("AccountAgeWitness",
            true,
            new DeviationByPercentage(0.9, 1.1, 0.95, 1.05)),
    SignedWitness("SignedWitness",
            true,
            new DeviationByPercentage(0.9, 1.1, 0.95, 1.05)),

    // Should be same value
    Alert("Alert",
            true,
            new DeviationByIntegerDiff(1, 1)),
    Filter("Filter",
            true,
            new DeviationByIntegerDiff(1, 1)),
    Mediator("Mediator",
            true,
            new DeviationByIntegerDiff(1, 1)),
    RefundAgent("RefundAgent",
            true,
            new DeviationByIntegerDiff(1, 1)),

    // Should be very close values
    TempProposalPayload("TempProposalPayload",
            true,
            new DeviationByIntegerDiff(3, 5)),
    ProposalPayload("ProposalPayload",
            true,
            new DeviationByIntegerDiff(1, 2)),
    BlindVotePayload("BlindVotePayload",
            true,
            new DeviationByIntegerDiff(1, 2)),

    // Should be very close values
    daoStateChainHeight("daoStateChainHeight",
            true,
            new DeviationByIntegerDiff(2, 4)),
    numBsqBlocks("numBsqBlocks",
            true,
            new DeviationByIntegerDiff(2, 4)),

    // Has to be same values at same block
    daoStateHash("daoStateHash",
            false,
            new DeviationOfHashes()),
    proposalHash("proposalHash",
            false,
            new DeviationOfHashes()),
    blindVoteHash("blindVoteHash",
            false,
            new DeviationOfHashes()),

    // Percentage deviation
    maxConnections("maxConnections",
            true,
            new DeviationByPercentage(0.33, 3, 0.4, 2.5)),
    numConnections("numConnections",
            true,
            new DeviationByPercentage(0, Double.MAX_VALUE, 0.4, 2.5)),
    peakNumConnections("peakNumConnections",
            true,
            new DeviationByPercentage(0.33, 3, 0.4, 2.5)),
    numAllConnectionsLostEvents("numAllConnectionsLostEvents",
            true,
            new DeviationByIntegerDiff(1, 2)),
    sentBytesPerSec("sentBytesPerSec",
            true,
            new DeviationByPercentage()),
    receivedBytesPerSec("receivedBytesPerSec",
            true,
            new DeviationByPercentage()),
    receivedMessagesPerSec("receivedMessagesPerSec",
            true,
            new DeviationByPercentage()),
    sentMessagesPerSec("sentMessagesPerSec",
            true,
            new DeviationByPercentage()),

    // No deviation check
    sentBytes("sentBytes", true),
    receivedBytes("receivedBytes", true),

    // No deviation check
    version("version", false),
    usedMemory("usedMemory", true),
    jvmStartTime("jvmStartTime", true);

    @Getter
    private final String key;
    @Getter
    private final boolean isNumberValue;
    @Getter
    @Nullable
    private DeviationType deviationType;

    InventoryItem(String key, boolean isNumberValue) {
        this.key = key;
        this.isNumberValue = isNumberValue;
    }

    InventoryItem(String key, boolean isNumberValue, DeviationType deviationType) {
        this(key, isNumberValue);
        this.deviationType = deviationType;
    }

    @Nullable
    public Double getDeviation(Map<InventoryItem, Double> averageValues, @Nullable String value) {
        if (averageValues.containsKey(this) && value != null) {
            double averageValue = averageValues.get(this);
            return getDeviation(value, averageValue);
        }
        return null;
    }

    @Nullable
    public Double getDeviation(@Nullable String value, double average) {
        if (deviationType != null && value != null && average != 0 && isNumberValue) {
            return Double.parseDouble(value) / average;
        }
        return null;
    }

    public DeviationSeverity getDeviationSeverity(Double deviation,
                                                  Collection<List<RequestInfo>> collection,
                                                  @Nullable String value,
                                                  String currentBlockHeight) {
        if (deviationType == null || deviation == null || value == null) {
            return DeviationSeverity.OK;
        }

        if (deviationType instanceof DeviationByPercentage) {
            return ((DeviationByPercentage) deviationType).getDeviationSeverity(deviation);
        } else if (deviationType instanceof DeviationByIntegerDiff) {
            return ((DeviationByIntegerDiff) deviationType).getDeviationSeverity(collection, value, this);
        } else if (deviationType instanceof DeviationOfHashes) {
            return ((DeviationOfHashes) deviationType).getDeviationSeverity(collection, value, this, currentBlockHeight);
        } else {
            return DeviationSeverity.OK;
        }
    }
}
