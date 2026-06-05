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

package bisq.core.filter;

import bisq.core.payment.payload.PaymentAccountPayload;

import bisq.common.crypto.Hash;
import bisq.common.util.Utilities;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;

import lombok.extern.slf4j.Slf4j;

import javax.annotation.Nullable;

import static com.google.common.base.Preconditions.checkArgument;

@Slf4j
public final class PaymentAccountFilterMatcher {
    public static final String HASH_PREFIX = "sha256-v1:";

    private static final String PREIMAGE_DOMAIN = "bisq:PaymentAccountFilter:v1";
    private static final Pattern HASH_VALUE_PATTERN = Pattern.compile("^" + HASH_PREFIX + "[0-9a-fA-F]{64}$");

    private PaymentAccountFilterMatcher() {
    }

    public static String hashValue(String paymentMethodId, String getMethodName, String value) {
        checkArgument(paymentMethodId != null, "paymentMethodId must not be null");
        checkArgument(getMethodName != null, "getMethodName must not be null");
        checkArgument(value != null, "value must not be null");

        String preimage = PREIMAGE_DOMAIN + "\n" +
                paymentMethodId + "\n" +
                getMethodName + "\n" +
                canonicalize(value);
        return HASH_PREFIX + Utilities.bytesAsHexString(Hash.getSha256Hash(preimage)).toLowerCase(Locale.ROOT);
    }

    public static boolean isValidHashValue(@Nullable String value) {
        return value != null && HASH_VALUE_PATTERN.matcher(value).matches();
    }

    public static String normalizeHashValue(String value) {
        checkArgument(isValidHashValue(value), "Payment account filter value must be a sha256-v1 hash");
        return value.toLowerCase(Locale.ROOT);
    }

    public static boolean matches(@Nullable PaymentAccountPayload paymentAccountPayload,
                                  @Nullable PaymentAccountFilter paymentAccountFilter) {
        if (paymentAccountPayload == null || paymentAccountFilter == null) {
            return false;
        }

        if (!Objects.equals(paymentAccountFilter.getPaymentMethodId(), paymentAccountPayload.getPaymentMethodId())) {
            return false;
        }

        Optional<String> runtimeValue = readRuntimeValue(paymentAccountPayload, paymentAccountFilter);
        return runtimeValue
                .map(value -> matchesValue(paymentAccountFilter, value))
                .orElse(false);
    }

    private static boolean matchesValue(PaymentAccountFilter paymentAccountFilter, String runtimeValue) {
        String filterValue = paymentAccountFilter.getValue();
        if (filterValue == null) {
            log.warn("Invalid payment account filter with null value for {} {}",
                    paymentAccountFilter.getPaymentMethodId(), paymentAccountFilter.getGetMethodName());
            return false;
        }

        if (filterValue.startsWith(HASH_PREFIX)) {
            if (!isValidHashValue(filterValue)) {
                log.warn("Invalid payment account filter hash for {} {}",
                        paymentAccountFilter.getPaymentMethodId(), paymentAccountFilter.getGetMethodName());
                return false;
            }
            return hashValue(paymentAccountFilter.getPaymentMethodId(),
                    paymentAccountFilter.getGetMethodName(),
                    runtimeValue).equalsIgnoreCase(filterValue);
        }

        // Transitional support for already-published plaintext filters. New filters should use sha256-v1 values.
        return runtimeValue.equalsIgnoreCase(filterValue);
    }

    private static Optional<String> readRuntimeValue(PaymentAccountPayload paymentAccountPayload,
                                                     PaymentAccountFilter paymentAccountFilter) {
        String getMethodName = paymentAccountFilter.getGetMethodName();
        try {
            Method method = paymentAccountPayload.getClass().getMethod(getMethodName);
            if (method.getParameterCount() != 0) {
                log.warn("Payment account filter getter {} on {} must not have parameters",
                        getMethodName, paymentAccountPayload.getClass().getName());
                return Optional.empty();
            }
            if (!String.class.equals(method.getReturnType())) {
                log.warn("Payment account filter getter {} on {} must return String",
                        getMethodName, paymentAccountPayload.getClass().getName());
                return Optional.empty();
            }

            String value = (String) method.invoke(paymentAccountPayload);
            if (value == null) {
                log.warn("Payment account filter getter {} on {} returned null",
                        getMethodName, paymentAccountPayload.getClass().getName());
                return Optional.empty();
            }
            return Optional.of(value);
        } catch (NoSuchMethodException e) {
            log.warn("Payment account filter getter {} not found on {}",
                    getMethodName, paymentAccountPayload.getClass().getName());
        } catch (IllegalAccessException | InvocationTargetException e) {
            log.warn("Could not invoke payment account filter getter {} on {}: {}",
                    getMethodName, paymentAccountPayload.getClass().getName(), e.getClass().getSimpleName());
        }
        return Optional.empty();
    }

    private static String canonicalize(String value) {
        return value.trim().toLowerCase(Locale.ROOT);
    }
}
