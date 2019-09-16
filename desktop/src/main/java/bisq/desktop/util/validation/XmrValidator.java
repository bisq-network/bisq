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

import java.math.BigDecimal;
import java.math.BigInteger;

import javax.annotation.Nullable;
import javax.inject.Inject;

import org.jetbrains.annotations.NotNull;

import static bisq.core.xmr.XmrFormatter.formatAsScaled;

import bisq.core.locale.Res;
import bisq.core.xmr.XmrFormatter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class XmrValidator extends AltcoinValidator {
    private final XmrFormatter xmrFormatter;

    @Nullable
    private BigInteger maxValue;
    @Nullable
    private BigInteger availableBalance;
    private BigInteger minValue = XmrFormatter.MINIMUM_SENDABLE_AMOUNT; // dust
    private BigDecimal minValueScaled = formatAsScaled(minValue);
    private BigDecimal maxValueScaled;
    private BigDecimal availableBalanceScaled;

    @Override
    protected double getMinValue() {
        return minValue.doubleValue();
    }

    @Inject
    public XmrValidator(XmrFormatter xmrFormatter) {
        this.xmrFormatter = xmrFormatter;
        // Limit to avoid overflows
        setMaxValue(new BigInteger("1840000000000000000"));
        maxValueScaled = formatAsScaled(maxValue);
    }

    public void setMinValue(@NotNull BigInteger minValue) {
        this.minValue = XmrFormatter.MINIMUM_SENDABLE_AMOUNT;
    }

    public void setMaxValue(@NotNull BigInteger maxValue) {
        this.maxValue = maxValue;
    }

    public void setAvailableBalance(@NotNull BigInteger availableBalance) {
        this.availableBalance = availableBalance;
        this.availableBalanceScaled = formatAsScaled(availableBalance);
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
                    .and(validateIfNotExceedsMaxValue(input));
        }

        if (result.isValid) {
            result = validateIfNotFractionalXmrValue(input)
                    .and(validateIfNotExceedsMaxXmrValue(input))
                    .and(validateIfSufficientAvailableBalance(input))
                    .and(validateIfAboveDust(input))
                    .and(validateIfNotBelowMinValue(input));
        }

        return result;
    }

    private ValidationResult validateIfAboveDust(String input) {
        final BigDecimal coin = new BigDecimal(input);
        if (coin.compareTo(minValueScaled) > 0) {
            return new ValidationResult(true);
        } else {
            return new ValidationResult(false, Res.get("validation.amountBelowDust",
                    xmrFormatter.formatBigInteger(minValue)));
        }
    }

    private ValidationResult validateIfNotFractionalXmrValue(String input) {
        BigDecimal bd = new BigDecimal(input);
        final BigDecimal satoshis = bd.movePointRight(2);
        if (satoshis.scale() > 0) {
            return new ValidationResult(false, Res.get("shared.account.wallet.validation.fraction"));
        } else {
            return new ValidationResult(true);
        }
    }

    private ValidationResult validateIfNotExceedsMaxXmrValue(String input) {
        try {
            final BigDecimal coin = new BigDecimal(input);
            if (maxValueScaled != null && coin.compareTo(maxValueScaled) > 0) {
                return new ValidationResult(false, Res.get("shared.account.wallet.validation.toLarge", xmrFormatter.formatBigInteger(maxValue)));
            } else {
                return new ValidationResult(true);
            }
        } catch (Throwable t) {
            return new ValidationResult(false, Res.get("shared.account.wallet.validation.invalidInput", t.getMessage()));
        }
    }

    private ValidationResult validateIfSufficientAvailableBalance(String input) {
        try {
            final BigDecimal coin = new BigDecimal(input);
            if (availableBalanceScaled != null && availableBalanceScaled.compareTo(coin) < 0)
                return new ValidationResult(false, Res.get("shared.account.wallet.validation.insufficientBalance",
                        xmrFormatter.formatBigInteger(availableBalance)));
            else
                return new ValidationResult(true);
        } catch (Throwable t) {
            return new ValidationResult(false, Res.get("validation.invalidInput", t.getMessage()));
        }
    }

    private ValidationResult validateIfNotBelowMinValue(String input) {
        try {
            final BigDecimal coin = new BigDecimal(input);
            if (minValueScaled != null && coin.compareTo(minValueScaled) < 0) {
                return new ValidationResult(false, Res.get("shared.account.wallet.validation.amountBelowMinAmount",
                        xmrFormatter.formatBigInteger(minValue)));
            } else {
                return new ValidationResult(true);
            }
        } catch (Throwable t) {
            return new ValidationResult(false, Res.get("validation.invalidInput", t.getMessage()));
        }
    }
}
