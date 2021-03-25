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

package bisq.cli;

import bisq.proto.grpc.OfferInfo;

import java.util.List;
import java.util.function.Function;

import static bisq.cli.ColumnHeaderConstants.COL_HEADER_DIRECTION;
import static java.lang.String.format;
import static protobuf.OfferPayload.Direction.BUY;
import static protobuf.OfferPayload.Direction.SELL;

class DirectionFormat {

    static int getLongestDirectionColWidth(List<OfferInfo> offers) {
        if (offers.isEmpty() || offers.get(0).getBaseCurrencyCode().equals("BTC"))
            return COL_HEADER_DIRECTION.length();
        else
            return 18;  // .e.g., "Sell BSQ (Buy BTC)".length()
    }

    static final Function<OfferInfo, String> directionFormat = (offer) -> {
        String baseCurrencyCode = offer.getBaseCurrencyCode();
        boolean isCryptoCurrencyOffer = !baseCurrencyCode.equals("BTC");
        if (!isCryptoCurrencyOffer) {
            return baseCurrencyCode;
        } else {
            // Return "Sell BSQ (Buy BTC)", or "Buy BSQ (Sell BTC)".
            String direction = offer.getDirection();
            String mirroredDirection = getMirroredDirection(direction);
            Function<String, String> mixedCase = (word) -> word.charAt(0) + word.substring(1).toLowerCase();
            return format("%s %s (%s %s)",
                    mixedCase.apply(mirroredDirection),
                    baseCurrencyCode,
                    mixedCase.apply(direction),
                    offer.getCounterCurrencyCode());
        }
    };

    static String getMirroredDirection(String directionAsString) {
        return directionAsString.equalsIgnoreCase(BUY.name()) ? SELL.name() : BUY.name();
    }
}
