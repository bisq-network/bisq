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

package bisq.core.offer;

import bisq.core.offer.bisq_v1.OfferPayloadExtraDataMap;

import bisq.common.app.Capabilities;
import bisq.common.app.Capability;
import bisq.common.config.Config;

import org.bitcoinj.core.Coin;

import static bisq.core.offer.bisq_v1.OfferPayloadExtraDataMap.Keys.*;

public class OfferRestrictions {
    // Traders without a Tor v3 node address cannot take offers and their offers are invisible. Regtest uses
    // localhost addresses.
    public static boolean requiresNodeAddressUpdate() {
        return !Config.baseCurrencyNetwork().isRegtest();
    }

    public static final Coin TOLERATED_SMALL_TRADE_AMOUNT = Coin.parseCoin("0.002");

    static boolean hasOfferMandatoryCapability(Offer offer, Capability mandatoryCapability) {
        OfferPayloadExtraDataMap extraDataMap = offer.getOfferPayloadExtraDataMap();
        if (extraDataMap != null && extraDataMap.containsKey(CAPABILITIES)) {
            String commaSeparatedOrdinals = extraDataMap.get(CAPABILITIES);
            Capabilities capabilities = Capabilities.fromStringList(commaSeparatedOrdinals);
            return Capabilities.hasMandatoryCapability(capabilities, mandatoryCapability);
        }
        return false;
    }
}
