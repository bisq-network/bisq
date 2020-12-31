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

import bisq.common.util.Tuple2;

import java.util.Collection;
import java.util.List;
import java.util.Map;

import lombok.Getter;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public enum InventoryItem {
    // Percentage deviation
    OfferPayload("OfferPayload",
            true,
            new DeviationByPercentage(0.5, 1.5, 0.75, 1.25), 10),
    MailboxStoragePayload("MailboxStoragePayload",
            true,
            new DeviationByPercentage(0.9, 1.1, 0.95, 1.05), 2),
    TradeStatistics3("TradeStatistics3",
            true,
            new DeviationByPercentage(0.9, 1.1, 0.95, 1.05), 2),
    AccountAgeWitness("AccountAgeWitness",
            true,
            new DeviationByPercentage(0.9, 1.1, 0.95, 1.05), 2),
    SignedWitness("SignedWitness",
            true,
            new DeviationByPercentage(0.9, 1.1, 0.95, 1.05), 2),

    // Should be same value
    Alert("Alert",
            true,
            new DeviationByIntegerDiff(1, 1), 2),
    Filter("Filter",
            true,
            new DeviationByIntegerDiff(1, 1), 2),
    Mediator("Mediator",
            true,
            new DeviationByIntegerDiff(1, 1), 2),
    RefundAgent("RefundAgent",
            true,
            new DeviationByIntegerDiff(1, 1), 2),

    // Should be very close values
    TempProposalPayload("TempProposalPayload",
            true,
            new DeviationByIntegerDiff(3, 5), 2),
    ProposalPayload("ProposalPayload",
            true,
            new DeviationByIntegerDiff(1, 2), 2),
    BlindVotePayload("BlindVotePayload",
            true,
            new DeviationByIntegerDiff(1, 2), 2),

    // Should be very close values
    daoStateChainHeight("daoStateChainHeight",
            true,
            new DeviationByIntegerDiff(2, 4), 3),
    numBsqBlocks("numBsqBlocks",
            true,
            new DeviationByIntegerDiff(2, 4), 3),

    // Has to be same values at same block
    daoStateHash("daoStateHash",
            false,
            new DeviationOfHashes(), 1),
    proposalHash("proposalHash",
            false,
            new DeviationOfHashes(), 1),
    blindVoteHash("blindVoteHash",
            false,
            new DeviationOfHashes(), 1),

    // Percentage deviation
    maxConnections("maxConnections",
            true,
            new DeviationByPercentage(0.33, 3, 0.4, 2.5), 2),
    numConnections("numConnections",
            true,
            new DeviationByPercentage(0, 3, 0, 2.5), 2),
    peakNumConnections("peakNumConnections",
            true,
            new DeviationByPercentage(0, 3, 0, 2.5), 2),
    numAllConnectionsLostEvents("numAllConnectionsLostEvents",
            true,
            new DeviationByIntegerDiff(1, 2), 1),
    sentBytesPerSec("sentBytesPerSec",
            true,
            new DeviationByPercentage(), 5),
    receivedBytesPerSec("receivedBytesPerSec",
            true,
            new DeviationByPercentage(), 5),
    receivedMessagesPerSec("receivedMessagesPerSec",
            true,
            new DeviationByPercentage(), 5),
    sentMessagesPerSec("sentMessagesPerSec",
            true,
            new DeviationByPercentage(), 5),

    // No deviation check
    sentBytes("sentBytes", true),
    receivedBytes("receivedBytes", true),

    // No deviation check
    version("version", false),
    commitHash("commitHash", false),
    usedMemory("usedMemory", true),
    jvmStartTime("jvmStartTime", true),
    filteredSeeds("filteredSeeds", false);

    @Getter
    private final String key;
    @Getter
    private final boolean isNumberValue;
    @Getter
    @Nullable
    private DeviationType deviationType;

    // The number of past requests we check to see if there have been repeated alerts or warnings. The higher the
    // number the more repeated alert need to have happened to cause a notification alert.
    // Smallest number is 1, as that takes only the last request data and does not look further back.
    @Getter
    private int deviationTolerance = 1;

    InventoryItem(String key, boolean isNumberValue) {
        this.key = key;
        this.isNumberValue = isNumberValue;
    }

    InventoryItem(String key, boolean isNumberValue, @NotNull DeviationType deviationType, int deviationTolerance) {
        this(key, isNumberValue);
        this.deviationType = deviationType;
        this.deviationTolerance = deviationTolerance;
    }

    @Nullable
    public Tuple2<Double, Double> getDeviationAndAverage(Map<InventoryItem, Double> averageValues,
                                                         @Nullable String value) {
        if (averageValues.containsKey(this) && value != null) {
            double averageValue = averageValues.get(this);
            return new Tuple2<>(getDeviation(value, averageValue), averageValue);
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
