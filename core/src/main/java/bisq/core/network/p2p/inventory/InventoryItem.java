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

package bisq.core.network.p2p.inventory;

import lombok.Getter;

public enum InventoryItem {
    OfferPayload("OfferPayload", Integer.class),
    MailboxStoragePayload("MailboxStoragePayload", Integer.class),
    TradeStatistics3("TradeStatistics3", Integer.class),
    Alert("Alert", Integer.class),
    Filter("Filter", Integer.class),
    Mediator("Mediator", Integer.class),
    RefundAgent("RefundAgent", Integer.class),
    AccountAgeWitness("AccountAgeWitness", Integer.class),
    SignedWitness("SignedWitness", Integer.class),

    TempProposalPayload("TempProposalPayload", Integer.class),
    ProposalPayload("ProposalPayload", Integer.class),
    BlindVotePayload("BlindVotePayload", Integer.class),

    daoStateChainHeight("daoStateChainHeight", Integer.class),
    numBsqBlocks("numBsqBlocks", Integer.class),
    daoStateHash("daoStateHash", String.class),
    proposalHash("proposalHash", String.class),
    blindVoteHash("blindVoteHash", String.class),

    maxConnections("maxConnections", Integer.class),
    numConnections("numConnections", Integer.class),
    sentData("sentData", String.class), // todo should be long
    receivedData("receivedData", String.class),// todo  should be long
    receivedMessagesPerSec("receivedMessagesPerSec", Double.class),
    sentMessagesPerSec("sentMessagesPerSec", Double.class),

    usedMemory("usedMemory", Long.class, 0, 3, 0, 2),
    jvmStartTime("jvmStartTime", Long.class);

    @Getter
    private final String key;
    @Getter
    private final Class type;
    private double lowerAlertTrigger = 0.7;
    private double upperAlertTrigger = 1.5;
    private double lowerWarnTrigger = 0.8;
    private double upperWarnTrigger = 1.3;

    InventoryItem(String key, Class type) {
        this.key = key;
        this.type = type;
    }

    InventoryItem(String key,
                  Class type,
                  double lowerAlertTrigger,
                  double upperAlertTrigger,
                  double lowerWarnTrigger,
                  double upperWarnTrigger) {
        this.key = key;
        this.type = type;
        this.lowerAlertTrigger = lowerAlertTrigger;
        this.upperAlertTrigger = upperAlertTrigger;
        this.lowerWarnTrigger = lowerWarnTrigger;
        this.upperWarnTrigger = upperWarnTrigger;
    }

    public DeviationSeverity getDeviationSeverity(double deviation) {
        if (deviation <= lowerAlertTrigger || deviation >= upperAlertTrigger) {
            return DeviationSeverity.ALERT;
        }

        if (deviation <= lowerWarnTrigger || deviation >= upperWarnTrigger) {
            return DeviationSeverity.WARN;
        }

        return DeviationSeverity.OK;
    }
}
