/*
 * This file is part of Bitsquare.
 *
 * Bitsquare is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * Bitsquare is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Bitsquare. If not, see <http://www.gnu.org/licenses/>.
 */

package io.bitsquare.gui.util.validation;

import io.bitsquare.gui.util.BSFormatter;
import io.bitsquare.locale.BSResources;
import org.bitcoinj.core.Coin;

import javax.annotation.Nullable;
import javax.inject.Inject;
import java.math.BigDecimal;

public class BtcValidator extends NumberValidator {

    private final BSFormatter formatter;


    @Nullable
    private Coin maxTradeLimitInBitcoin;

    @Inject
    public BtcValidator(BSFormatter formatter) {
        this.formatter = formatter;
    }

    public void setMaxTradeLimitInBitcoin(Coin maxTradeLimitInBitcoin) {
        this.maxTradeLimitInBitcoin = maxTradeLimitInBitcoin;
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
                    .and(validateIfNotExceedsMaxBtcValue(input));
        }

        return result;
    }

    protected ValidationResult validateIfNotFractionalBtcValue(String input) {
        BigDecimal bd = new BigDecimal(input);
        final BigDecimal satoshis = bd.movePointRight(8);
        if (satoshis.scale() > 0)
            return new ValidationResult(false, BSResources.get("validation.btc.toSmall"));
        else
            return new ValidationResult(true);
    }

    protected ValidationResult validateIfNotExceedsMaxBtcValue(String input) {
        try {
            final Coin coin = Coin.parseCoin(input);
            if (maxTradeLimitInBitcoin != null && coin.compareTo(maxTradeLimitInBitcoin) > 0)
                return new ValidationResult(false, BSResources.get("validation.btc.toLarge", formatter.formatCoinWithCode(maxTradeLimitInBitcoin)));
            else
                return new ValidationResult(true);
        } catch (Throwable t) {
            return new ValidationResult(false, "Invalid input: " + t.getMessage());
        }
    }
}
