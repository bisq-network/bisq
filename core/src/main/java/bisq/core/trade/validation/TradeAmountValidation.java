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

package bisq.core.trade.validation;

import org.bitcoinj.core.Coin;

import static bisq.core.util.Validator.checkIsPositive;
import static com.google.common.base.Preconditions.checkArgument;

public class TradeAmountValidation {
    public static Coin checkTradeAmount(Coin tradeAmount, Coin offerMinAmount, Coin offerMaxAmount) {
        checkIsPositive(tradeAmount, "tradeAmount");
        checkIsPositive(offerMinAmount, "offerMinAmount");
        checkIsPositive(offerMaxAmount, "offerMaxAmount");

        checkArgument(!tradeAmount.isLessThan(offerMinAmount),
                "Trade amount must not be less than minimum offer amount. tradeAmount=%s, offerMinAmount=%s",
                tradeAmount.toFriendlyString(), offerMinAmount.toFriendlyString());
        checkArgument(!tradeAmount.isGreaterThan(offerMaxAmount),
                "Trade amount must not be higher than maximum offer amount. tradeAmount=%s, offerMaxAmount=%s",
                tradeAmount.toFriendlyString(), offerMaxAmount.toFriendlyString());
        return tradeAmount;
    }
}
