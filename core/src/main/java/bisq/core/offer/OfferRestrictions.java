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

import bisq.core.offer.bisq_v1.OfferPayload;

import bisq.common.app.Capabilities;
import bisq.common.app.Capability;
import bisq.common.config.Config;
import bisq.common.util.Utilities;

import org.bitcoinj.core.Coin;

import java.util.Date;
import java.util.GregorianCalendar;
import java.util.Map;

public class OfferRestrictions {
    // The date when traders who have not upgraded to a Tor v3 Node Address cannot take offers and their offers become
    // invisible.
    private static final Date REQUIRE_TOR_NODE_ADDRESS_V3_DATE = Utilities.getUTCDate(2021, GregorianCalendar.AUGUST, 15);

    public static boolean requiresNodeAddressUpdate() {
        return new Date().after(REQUIRE_TOR_NODE_ADDRESS_V3_DATE) && !Config.baseCurrencyNetwork().isRegtest();
    }

    public static Coin TOLERATED_SMALL_TRADE_AMOUNT = Coin.parseCoin("0.01");

    static boolean hasOfferMandatoryCapability(Offer offer, Capability mandatoryCapability) {
        Map<String, String> extraDataMap = offer.getExtraDataMap();
        if (extraDataMap != null && extraDataMap.containsKey(OfferPayload.CAPABILITIES)) {
            String commaSeparatedOrdinals = extraDataMap.get(OfferPayload.CAPABILITIES);
            Capabilities capabilities = Capabilities.fromStringList(commaSeparatedOrdinals);
            return Capabilities.hasMandatoryCapability(capabilities, mandatoryCapability);
        }
        return false;
    }
}
