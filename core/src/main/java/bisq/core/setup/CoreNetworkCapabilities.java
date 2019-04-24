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

import java.util.ArrayList;
import java.util.Arrays;

public class CoreNetworkCapabilities {
    public static void setSupportedCapabilities(BisqEnvironment bisqEnvironment) {
        final ArrayList<Capability> supportedCapabilities = new ArrayList<>(Arrays.asList(
                Capability.TRADE_STATISTICS,
                Capability.TRADE_STATISTICS_2,
                Capability.ACCOUNT_AGE_WITNESS,
                Capability.ACK_MSG
        ));

        if (BisqEnvironment.isDaoActivated(bisqEnvironment)) {
            supportedCapabilities.add(Capability.PROPOSAL);
            supportedCapabilities.add(Capability.BLIND_VOTE);
            supportedCapabilities.add(Capability.BSQ_BLOCK);

            String isFullDaoNode = bisqEnvironment.getProperty(DaoOptionKeys.FULL_DAO_NODE, String.class, "");
            if (isFullDaoNode != null && !isFullDaoNode.isEmpty())
                supportedCapabilities.add(Capability.DAO_FULL_NODE);
        }

        Capabilities.app.resetCapabilities(supportedCapabilities);
    }
}
