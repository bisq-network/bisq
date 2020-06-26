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

import bisq.core.dao.DaoFacade;
import bisq.core.dao.governance.param.Param;
import bisq.core.filter.FilterManager;

import org.bitcoinj.core.Coin;

import com.google.common.annotations.VisibleForTesting;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Random;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class FeeReceiverSelector {
    public static String getAddress(DaoFacade daoFacade, FilterManager filterManager) {
        return getAddress(daoFacade, filterManager, new Random());
    }

    @VisibleForTesting
    static String getAddress(DaoFacade daoFacade, FilterManager filterManager, Random rnd) {
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

        // We keep default value as fallback in case no filter value is available or user has old version.
        return daoFacade.getParamValue(Param.RECIPIENT_BTC_ADDRESS);
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
