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

package bisq.common.util;

import java.util.HashMap;
import java.util.Map;

import lombok.extern.slf4j.Slf4j;

import javax.annotation.Nullable;

import static com.google.common.base.Preconditions.checkArgument;

/**
 * Validator for extraDataMap fields used in network payloads.
 * Ensures that we don't get the network attacked by huge data inserted there.
 */
@Slf4j
public class ExtraDataMapValidator {
    // ExtraDataMap is only used for exceptional cases to not break backward compatibility.
    // We don't expect many entries there.
    public final static int MAX_SIZE = 10;
    public final static int MAX_KEY_LENGTH = 100;
    public final static int MAX_VALUE_LENGTH = 100000; // 100 kb

    public static Map<String, String> getValidatedExtraDataMap(@Nullable Map<String, String> extraDataMap) {
        return getValidatedExtraDataMap(extraDataMap, MAX_SIZE, MAX_KEY_LENGTH, MAX_VALUE_LENGTH);
    }

    public static Map<String, String> getValidatedExtraDataMap(@Nullable Map<String, String> extraDataMap, int maxSize,
                                                               int maxKeyLength, int maxValueLength) {
        if (extraDataMap == null)
            return null;

        try {
            checkArgument(extraDataMap.entrySet().size() <= maxSize,
                    "Size of map must not exceed " + maxSize);
            extraDataMap.forEach((key, value) -> {
                checkArgument(key.length() <= maxKeyLength,
                        "Length of key must not exceed " + maxKeyLength);
                checkArgument(value.length() <= maxValueLength,
                        "Length of value must not exceed " + maxValueLength);
            });
            return extraDataMap;
        } catch (Throwable t) {
            return new HashMap<>();
        }
    }

    public static void validate(@Nullable Map<String, String> extraDataMap) {
        validate(extraDataMap, MAX_SIZE, MAX_KEY_LENGTH, MAX_VALUE_LENGTH);
    }

    public static void validate(@Nullable Map<String, String> extraDataMap, int maxSize, int maxKeyLength,
                                int maxValueLength) {
        if (extraDataMap == null)
            return;

        checkArgument(extraDataMap.entrySet().size() <= maxSize,
                "Size of map must not exceed " + maxSize);
        extraDataMap.forEach((key, value) -> {
            checkArgument(key.length() <= maxKeyLength,
                    "Length of key must not exceed " + maxKeyLength);
            checkArgument(value.length() <= maxValueLength,
                    "Length of value must not exceed " + maxValueLength);
        });
    }
}
