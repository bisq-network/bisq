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
import io.bisq.gui.util.BsqFormatter;
import lombok.extern.slf4j.Slf4j;
import org.bitcoinj.core.Coin;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nullable;
import javax.inject.Inject;
import java.math.BigDecimal;

@Slf4j
public class BsqValidator extends AltcoinValidator {
    protected final BsqFormatter bsqFormatter;

    @Nullable
    protected Coin maxValue;
    @Nullable
    private Coin availableBalance;

    @Override
    protected double getMinValue() {
        return 2.730; // dust
    }

    @Inject
    public BsqValidator(BsqFormatter bsqFormatter) {
        this.bsqFormatter = bsqFormatter;
        // TODO do we want a limit here?
        //setMaxValue(bsqFormatter.parseToCoin("2500000"));
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
                    .and(validateIfAboveDust(input));
        }

        return result;
    }

    protected ValidationResult validateIfAboveDust(String input) {
        final Coin coin = bsqFormatter.parseToCoin(input);
        if (Restrictions.isAboveDust(coin))
            return new ValidationResult(true);
        else
            return new ValidationResult(false, Res.get("validation.btc.amountBelowDust"));
    }

    protected ValidationResult validateIfNotFractionalBtcValue(String input) {
        BigDecimal bd = new BigDecimal(input);
        final BigDecimal satoshis = bd.movePointRight(3);
        if (satoshis.scale() > 0)
            return new ValidationResult(false, Res.get("validation.btc.fraction"));
        else
            return new ValidationResult(true);
    }

    protected ValidationResult validateIfNotExceedsMaxBtcValue(String input) {
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

    protected ValidationResult validateIfSufficientAvailableBalance(String input) {
        try {
            final Coin coin = bsqFormatter.parseToCoin(input);
            if (availableBalance != null && coin.compareTo(availableBalance) > 0)
                return new ValidationResult(false, Res.get("validation.bsq.insufficientBalance",
                        bsqFormatter.formatCoinWithCode(availableBalance)));
            else
                return new ValidationResult(true);
        } catch (Throwable t) {
            return new ValidationResult(false, Res.get("validation.invalidInput", t.getMessage()));
        }
    }
}
