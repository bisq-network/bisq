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

package bisq.desktop.util.validation;

import bisq.core.btc.wallet.Restrictions;
import bisq.core.locale.Res;
import bisq.core.util.FormattingUtils;
import bisq.core.util.coin.CoinFormatter;
import bisq.core.util.validation.NumberValidator;

import org.bitcoinj.core.Coin;

import javax.inject.Inject;
import javax.inject.Named;

import java.math.BigDecimal;

import lombok.Getter;
import lombok.Setter;

import javax.annotation.Nullable;

public class BtcValidator extends NumberValidator {

    protected final CoinFormatter formatter;

    @Nullable
    @Setter
    protected Coin minValue;

    @Nullable
    @Setter
    protected Coin maxValue;

    @Nullable
    @Setter
    @Getter
    protected Coin maxTradeLimit;

    @Inject
    public BtcValidator(@Named(FormattingUtils.BTC_FORMATTER_KEY) CoinFormatter formatter) {
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
            result = result.andValidation(input,
                    this::validateIfNotZero,
                    this::validateIfNotNegative,
                    this::validateIfNotFractionalBtcValue,
                    this::validateIfNotExceedsMaxTradeLimit,
                    this::validateIfNotExceedsMaxBtcValue,
                    this::validateIfNotUnderMinValue,
                    this::validateIfAboveDust);
        }

        return result;
    }

    protected ValidationResult validateIfAboveDust(String input) {
        try {
            final Coin coin = Coin.parseCoin(input);
            if (Restrictions.isAboveDust(coin))
                return new ValidationResult(true);
            else
                return new ValidationResult(false, Res.get("validation.amountBelowDust",
                        formatter.formatCoinWithCode(Restrictions.getMinNonDustOutput())));
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
