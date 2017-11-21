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

package io.bisq.gui.util.validation;

import io.bisq.common.locale.Res;
import io.bisq.core.btc.Restrictions;
import io.bisq.gui.util.BSFormatter;
import lombok.Setter;
import org.bitcoinj.core.Coin;

import javax.annotation.Nullable;
import javax.inject.Inject;
import java.math.BigDecimal;

public class BtcValidator extends NumberValidator {

    protected final BSFormatter formatter;

    @Nullable
    @Setter
    protected Coin minValue;

    @Nullable
    @Setter
    protected Coin maxValue;

    @Nullable
    @Setter
    protected Coin maxTradeLimit;

    @Inject
    public BtcValidator(BSFormatter formatter) {
        this.formatter = formatter;
    }


    @Override
    public ValidationResult validate(String input) {
        ValidationResult result = validateIfNotEmpty(input);
        if (result.isValid) {
            input = cleanInput(input);
            result = validateIfNumber(input);
        }

        if (result.isValid) {
            result = validateIfNotZero(input)
                    .and(validateIfNotNegative(input))
                    .and(validateIfNotFractionalBtcValue(input))
                    .and(validateIfNotExceedsMaxBtcValue(input))
                    .and(validateIfNotExceedsMaxTradeLimit(input))
                    .and(validateIfNotUnderMinValue(input))
                    .and(validateIfAboveDust(input));
        }

        return result;
    }

    protected ValidationResult validateIfAboveDust(String input) {
        try {
            final Coin coin = Coin.parseCoin(input);
            if (Restrictions.isAboveDust(coin))
                return new ValidationResult(true);
            else
                return new ValidationResult(false, Res.get("validation.btc.amountBelowDust"));
        } catch (Throwable t) {
            return new ValidationResult(false, Res.get("validation.invalidInput", t.getMessage()));
        }
    }

    protected ValidationResult validateIfNotFractionalBtcValue(String input) {
        try {
            BigDecimal bd = new BigDecimal(input);
            final BigDecimal satoshis = bd.movePointRight(8);
            if (satoshis.scale() > 0)
                return new ValidationResult(false, Res.get("validation.btc.fraction"));
            else
                return new ValidationResult(true);
        } catch (Throwable t) {
            return new ValidationResult(false, Res.get("validation.invalidInput", t.getMessage()));
        }
    }

    protected ValidationResult validateIfNotExceedsMaxBtcValue(String input) {
        try {
            final Coin coin = Coin.parseCoin(input);
            if (maxValue != null && coin.compareTo(maxValue) > 0)
                return new ValidationResult(false, Res.get("validation.btc.toLarge", formatter.formatCoinWithCode(maxValue)));
            else
                return new ValidationResult(true);
        } catch (Throwable t) {
            return new ValidationResult(false, Res.get("validation.invalidInput", t.getMessage()));
        }
    }

    protected ValidationResult validateIfNotExceedsMaxTradeLimit(String input) {
        try {
            final Coin coin = Coin.parseCoin(input);
            if (maxTradeLimit != null && coin.compareTo(maxTradeLimit) > 0)
                return new ValidationResult(false, Res.get("validation.btc.exceedsMaxTradeLimit", formatter.formatCoinWithCode(maxTradeLimit)));
            else
                return new ValidationResult(true);
        } catch (Throwable t) {
            return new ValidationResult(false, Res.get("validation.invalidInput", t.getMessage()));
        }
    }

    protected ValidationResult validateIfNotUnderMinValue(String input) {
        try {
            final Coin coin = Coin.parseCoin(input);
            if (minValue != null && coin.compareTo(minValue) < 0)
                return new ValidationResult(false, Res.get("validation.btc.toSmall", formatter.formatCoinWithCode(minValue)));
            else
                return new ValidationResult(true);
        } catch (Throwable t) {
            return new ValidationResult(false, Res.get("validation.invalidInput", t.getMessage()));
        }
    }
}
