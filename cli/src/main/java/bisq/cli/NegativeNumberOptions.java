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

package bisq.cli;

import java.math.BigDecimal;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

import static java.util.Arrays.stream;

class NegativeNumberOptions {

    private final Map<Integer, String> negativeNumberParams = new HashMap<>();

    String[] removeNegativeNumberOptions(String[] args) {
        // Cache any negative number params that will be rejected by the parser.
        // This should be called before command line parsing.
        for (int i = 0; i < args.length; i++) {
            if (isNegativeNumber.test(args[i])) {
                String param = args[i];
                negativeNumberParams.put(i - 1, new BigDecimal(param).toString());
                args[i] = "0";  // Substitute a zero so the options parser won't barf.
            }
        }
        return args;
    }

    List<String> restoreNegativeNumberOptions(List<String> nonOptionArgs) {
        // Put cached negative number params into a clone of the nonOptionArgs list.
        // This should be called after command line parsing.
        if (!negativeNumberParams.isEmpty()) {
            List<String> nonOptionArgsClone = new ArrayList<>(nonOptionArgs);
            negativeNumberParams.forEach((k, v) -> {
                int idx = k;
                nonOptionArgsClone.remove(idx);
                nonOptionArgsClone.add(idx, v);
            });
            return Collections.unmodifiableList(nonOptionArgsClone);
        } else {
            // This should never happen.  Instances of this class should not be created
            // if there are no negative number options.
            return nonOptionArgs;
        }
    }

    static boolean hasNegativeNumberOptions(String[] args) {
        return stream(args).anyMatch(isNegativeNumber);
    }

    private static final Predicate<String> isNegativeNumber = (param) -> {
        if (param.length() > 1 && param.startsWith("-")) {
            try {
                new BigDecimal(param);
                return true;
            } catch (NumberFormatException ignored) {
                // empty
            }
        }
        return false;
    };
}
