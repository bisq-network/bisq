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

import lombok.Getter;

public enum AvailabilityResult {
    UNKNOWN_FAILURE("Cannot take offer for unknown reason"),
    AVAILABLE("Offer is available"),
    OFFER_TAKEN("Offer is taken"),
    PRICE_OUT_OF_TOLERANCE("Cannot take offer because taker's price is outside tolerance"),
    MARKET_PRICE_NOT_AVAILABLE("Cannot take offer because market price for calculating trade price is unavailable"),
    @SuppressWarnings("unused") NO_ARBITRATORS("Cannot take offer because no arbitrators are available"),
    NO_MEDIATORS("Cannot take offer because no mediators are available"),
    USER_IGNORED("Cannot take offer because user is ignored"),
    @SuppressWarnings("unused") MISSING_MANDATORY_CAPABILITY("Missing mandatory capability"),
    @SuppressWarnings("unused") NO_REFUND_AGENTS("Cannot take offer because no refund agents are available"),
    UNCONF_TX_LIMIT_HIT("Cannot take offer because you have too many unconfirmed transactions at this moment"),
    MAKER_DENIED_API_USER("Cannot take offer because maker is api user"),
    PRICE_CHECK_FAILED("Cannot take offer because trade price check failed"),
    INVALID_SNAPSHOT_HEIGHT("Cannot take offer because snapshot height does not match. Probably your DAO data are not synced.");

    @Getter
    private final String description;

    AvailabilityResult(String description) {
        this.description = description;
    }

    public static AvailabilityResult fromProto(protobuf.AvailabilityResult proto) {
        return AvailabilityResult.valueOf(proto.name());
    }
}
