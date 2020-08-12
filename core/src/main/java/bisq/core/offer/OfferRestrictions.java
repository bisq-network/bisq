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

import bisq.common.app.Capabilities;
import bisq.common.app.Capability;
import bisq.common.util.Utilities;

import org.bitcoinj.core.Coin;

import java.util.Date;
import java.util.GregorianCalendar;
import java.util.Map;

public class OfferRestrictions {
    // The date when traders who have not updated cannot take offers from updated clients and their offers become
    // invisible for updated clients.
    private static final Date REQUIRE_UPDATE_DATE = Utilities.getUTCDate(2019, GregorianCalendar.SEPTEMBER, 19);

    static boolean requiresUpdate() {
        return new Date().after(REQUIRE_UPDATE_DATE);
    }

    public static Coin TOLERATED_SMALL_TRADE_AMOUNT = Coin.parseCoin("0.01");

    static boolean hasOfferMandatoryCapability(Offer offer, Capability mandatoryCapability) {
        Map<String, String> extraDataMap = offer.getOfferPayload().getExtraDataMap();
        if (extraDataMap != null && extraDataMap.containsKey(OfferPayload.CAPABILITIES)) {
            String commaSeparatedOrdinals = extraDataMap.get(OfferPayload.CAPABILITIES);
            Capabilities capabilities = Capabilities.fromStringList(commaSeparatedOrdinals);
            return Capabilities.hasMandatoryCapability(capabilities, mandatoryCapability);
        }
        return false;
    }
}
