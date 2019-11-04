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

package bisq.core.setup;

import bisq.core.app.BisqEnvironment;
import bisq.core.dao.DaoOptionKeys;

import bisq.common.app.Capabilities;
import bisq.common.app.Capability;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class CoreNetworkCapabilities {
    static void setSupportedCapabilities(BisqEnvironment bisqEnvironment) {
        Capabilities.app.addAll(
                Capability.TRADE_STATISTICS,
                Capability.TRADE_STATISTICS_2,
                Capability.ACCOUNT_AGE_WITNESS,
                Capability.ACK_MSG,
                Capability.PROPOSAL,
                Capability.BLIND_VOTE,
                Capability.DAO_STATE,
                Capability.BUNDLE_OF_ENVELOPES,
                Capability.MEDIATION,
                Capability.SIGNED_ACCOUNT_AGE_WITNESS,
                Capability.REFUND_AGENT,
                Capability.TRADE_STATISTICS_HASH_UPDATE
        );

        if (BisqEnvironment.isDaoActivated(bisqEnvironment)) {
            maybeApplyDaoFullMode(bisqEnvironment);
        }
    }

    public static void maybeApplyDaoFullMode(BisqEnvironment bisqEnvironment) {
        // If we set dao full mode at the preferences view we add the capability there. We read the preferences a
        // bit later than we call that method so we have to add DAO_FULL_NODE Capability at preferences as well to
        // be sure it is set in both cases.
        String isFullDaoNode = bisqEnvironment.getProperty(DaoOptionKeys.FULL_DAO_NODE, String.class, "false");
        if (isFullDaoNode != null && !isFullDaoNode.isEmpty() && isFullDaoNode.toLowerCase().equals("true")) {
            log.info("Set Capability.DAO_FULL_NODE");
            Capabilities.app.addAll(Capability.DAO_FULL_NODE);
        } else {
            // A lite node has the capability to receive bsq blocks. We do not want to send BSQ blocks to full nodes
            // as they ignore them anyway.
            log.info("Set Capability.RECEIVE_BSQ_BLOCK");
            Capabilities.app.addAll(Capability.RECEIVE_BSQ_BLOCK);
        }
    }
}
