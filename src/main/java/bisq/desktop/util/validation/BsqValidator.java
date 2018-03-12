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

import bisq.desktop.util.BsqFormatter;

import bisq.core.btc.Restrictions;
import bisq.core.util.CoinUtil;

import bisq.common.locale.Res;

import org.bitcoinj.core.Coin;

import javax.inject.Inject;

import java.math.BigDecimal;

import lombok.extern.slf4j.Slf4j;

import org.jetbrains.annotations.NotNull;

import javax.annotation.Nullable;

@Slf4j
public class BsqValidator extends AltcoinValidator {
    private final BsqFormatter bsqFormatter;

    @Nullable
    private Coin maxValue;
    @Nullable
    private Coin availableBalance;
    private Coin minValue = Coin.valueOf(2730); // dust

    @Override
    protected double getMinValue() {
        return minValue.value;
    }

    @Inject
    public BsqValidator(BsqFormatter bsqFormatter) {
        this.bsqFormatter = bsqFormatter;
        // TODO do we want a limit here?
        //setMaxValue(bsqFormatter.parseToCoin("2500000"));
    }

    public void setMinValue(@NotNull Coin minValue) {
        this.minValue = CoinUtil.maxCoin(minValue, this.minValue);
    }

    public void setMaxValue(@NotNull Coin maxValue) {
        this.maxValue = maxValue;
    }

    public void setAvailableBalance(@NotNull Coin availableBalance) {
        this.availableBalance = availableBalance;
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
                    .and(validateIfSufficientAvailableBalance(input))
                    .and(validateIfAboveDust(input))
                    .and(validateIfNotBelowMinValue(input));
        }

        return result;
    }

    private ValidationResult validateIfAboveDust(String input) {
        final Coin coin = bsqFormatter.parseToCoin(input);
        if (Restrictions.isAboveDust(coin))
            return new ValidationResult(true);
        else
            return new ValidationResult(false, Res.get("validation.btc.amountBelowDust", bsqFormatter.formatCoinWithCode(Restrictions.getMinNonDustOutput())));
    }

    private ValidationResult validateIfNotFractionalBtcValue(String input) {
        BigDecimal bd = new BigDecimal(input);
        final BigDecimal satoshis = bd.movePointRight(2);
        if (satoshis.scale() > 0)
            return new ValidationResult(false, Res.get("validation.btc.fraction"));
        else
            return new ValidationResult(true);
    }

    private ValidationResult validateIfNotExceedsMaxBtcValue(String input) {
        try {
            final Coin coin = bsqFormatter.parseToCoin(input);
            if (maxValue != null && coin.compareTo(maxValue) > 0)
                return new ValidationResult(false, Res.get("validation.btc.toLarge", bsqFormatter.formatCoinWithCode(maxValue)));
            else
                return new ValidationResult(true);
        } catch (Throwable t) {
            return new ValidationResult(false, Res.get("validation.invalidInput", t.getMessage()));
        }
    }

    private ValidationResult validateIfSufficientAvailableBalance(String input) {
        try {
            final Coin coin = bsqFormatter.parseToCoin(input);
            if (availableBalance != null && availableBalance.compareTo(coin) < 0)
                return new ValidationResult(false, Res.get("validation.bsq.insufficientBalance",
                        bsqFormatter.formatCoinWithCode(availableBalance)));
            else
                return new ValidationResult(true);
        } catch (Throwable t) {
            return new ValidationResult(false, Res.get("validation.invalidInput", t.getMessage()));
        }
    }

    private ValidationResult validateIfNotBelowMinValue(String input) {
        try {
            final Coin coin = bsqFormatter.parseToCoin(input);
            if (minValue != null && coin.compareTo(minValue) < 0)
                return new ValidationResult(false, Res.get("validation.bsq.amountBelowMinAmount",
                        bsqFormatter.formatCoinWithCode(minValue)));
            else
                return new ValidationResult(true);
        } catch (Throwable t) {
            return new ValidationResult(false, Res.get("validation.invalidInput", t.getMessage()));
        }
    }
}
