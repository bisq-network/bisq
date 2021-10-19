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

package bisq.core.offer.availability;

public enum AvailabilityResult {
    UNKNOWN_FAILURE("cannot take offer for unknown reason"),
    AVAILABLE("offer available"),
    OFFER_TAKEN("offer taken"),
    PRICE_OUT_OF_TOLERANCE("cannot take offer because taker's price is outside tolerance"),
    MARKET_PRICE_NOT_AVAILABLE("cannot take offer because market price for calculating trade price is unavailable"),
    @SuppressWarnings("unused") NO_ARBITRATORS("cannot take offer because no arbitrators are available"),
    NO_MEDIATORS("cannot take offer because no mediators are available"),
    USER_IGNORED("cannot take offer because user is ignored"),
    @SuppressWarnings("unused") MISSING_MANDATORY_CAPABILITY("description not available"),
    @SuppressWarnings("unused") NO_REFUND_AGENTS("cannot take offer because no refund agents are available"),
    UNCONF_TX_LIMIT_HIT("cannot take offer because you have too many unconfirmed transactions at this moment"),
    MAKER_DENIED_API_USER("cannot take offer because maker is api user"),
    PRICE_CHECK_FAILED("cannot take offer because trade price check failed");

    private final String description;

    AvailabilityResult(String description) {
        this.description = description;
    }

    public String description() {
        return description;
    }

    public static AvailabilityResult fromProto(protobuf.AvailabilityResult proto) {
        return AvailabilityResult.valueOf(proto.name());
    }
}
