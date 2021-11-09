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

import bisq.core.filter.FilterManager;

import bisq.common.config.Config;

import org.bitcoinj.core.Coin;

import com.google.common.annotations.VisibleForTesting;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Random;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class FeeReceiverSelector {
    public static final String BTC_FEE_RECEIVER_ADDRESS = "38bZBj5peYS3Husdz7AH3gEUiUbYRD951t";

    public static String getMostRecentAddress() {
        return Config.baseCurrencyNetwork().isMainnet() ? BTC_FEE_RECEIVER_ADDRESS :
                Config.baseCurrencyNetwork().isTestnet() ? "2N4mVTpUZAnhm9phnxB7VrHB4aBhnWrcUrV" :
                        "2MzBNTJDjjXgViKBGnatDU3yWkJ8pJkEg9w";
    }

    public static String getAddress(FilterManager filterManager) {
        return getAddress(filterManager, new Random());
    }

    @VisibleForTesting
    static String getAddress(FilterManager filterManager, Random rnd) {
        List<String> feeReceivers = Optional.ofNullable(filterManager.getFilter())
                .flatMap(f -> Optional.ofNullable(f.getBtcFeeReceiverAddresses()))
                .orElse(List.of());

        List<Long> amountList = new ArrayList<>();
        List<String> receiverAddressList = new ArrayList<>();

        feeReceivers.forEach(e -> {
            try {
                String[] tokens = e.split("#");
                amountList.add(Coin.parseCoin(tokens[1]).longValue()); // total amount the victim should receive
                receiverAddressList.add(tokens[0]);                    // victim's receiver address
            } catch (RuntimeException ignore) {
                // If input format is not as expected we ignore entry
            }
        });

        if (!amountList.isEmpty()) {
            return receiverAddressList.get(weightedSelection(amountList, rnd));
        }

        // If no fee address receiver is defined via filter we use the hard coded recent address
        return getMostRecentAddress();
    }

    @VisibleForTesting
    static int weightedSelection(List<Long> weights, Random rnd) {
        long sum = weights.stream().mapToLong(n -> n).sum();
        long target = rnd.longs(0, sum).findFirst().orElseThrow();
        int i;
        for (i = 0; i < weights.size() && target >= 0; i++) {
            target -= weights.get(i);
        }
        return i - 1;
    }
}
