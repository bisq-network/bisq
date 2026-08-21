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

package bisq.core.util;

import org.bitcoinj.core.Coin;

import java.util.Collection;
import java.util.Objects;

import javax.annotation.Nullable;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

public class Validator {

    public static String nonEmptyStringOf(String value) {
        checkNotNull(value);
        checkArgument(!value.isEmpty());
        return value;
    }

    public static String checkNonEmptyString(String value, String fieldName) {
        checkNotNull(value, "%s must not be null", fieldName);
        checkArgument(!value.isEmpty(), "%s must not be empty", fieldName);
        return value;
    }

    public static String checkNonBlankString(String value, String fieldName) {
        checkNotNull(value, "%s must not be null", fieldName);
        checkArgument(!value.isBlank(), "%s must not be blank", fieldName);
        return value;
    }

    public static void checkNullableString(@Nullable String value, String fieldName) {
        if (value != null) {
            checkArgument(!value.isEmpty(), "%s must not be empty", fieldName);
        }
    }

    public static byte[] checkNonEmptyBytes(byte[] value, String fieldName) {
        checkNotNull(value, "%s must not be null", fieldName);
        checkArgument(value.length > 0, "%s must not be empty", fieldName);
        return value;
    }

    public static void checkNullableBytes(@Nullable byte[] value, String fieldName) {
        if (value != null) {
            checkArgument(value.length > 0, "%s must not be empty", fieldName);
        }
    }

    public static <T extends Collection<?>> T checkList(T values, boolean requireNonEmpty, String fieldName) {
        checkNotNull(values, "%s must not be null", fieldName);
        if (requireNonEmpty) {
            checkArgument(!values.isEmpty(), "%s must not be empty", fieldName);
        }
        checkArgument(values.stream().noneMatch(Objects::isNull), "%s must not contain null entries", fieldName);
        return values;
    }

    public static long checkIsPositive(long value, String fieldName) {
        checkArgument(value > 0, "%s must be positive", fieldName);
        return value;
    }

    public static long checkIsNotNegative(long value, String fieldName) {
        checkArgument(value >= 0, "%s must not be negative", fieldName);
        return value;
    }

    public static Coin checkIsPositive(Coin value, String fieldName) {
        checkNotNull(value, "%s must not be null", fieldName);
        checkArgument(value.isPositive(), "%s must be positive", fieldName);
        return value;
    }

    public static Coin checkIsNotNegative(Coin value, String fieldName) {
        checkNotNull(value, "%s must not be null", fieldName);
        checkArgument(!value.isNegative(), "%s must not be negative", fieldName);
        return value;
    }

    public static Coin checkIsNotZero(Coin value, String fieldName) {
        checkNotNull(value, "%s must not be null", fieldName);
        checkArgument(!value.isZero(), "%s must not be zero", fieldName);
        return value;
    }
}
