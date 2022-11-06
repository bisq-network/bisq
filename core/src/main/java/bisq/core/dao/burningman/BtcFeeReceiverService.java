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

package bisq.core.dao.burningman;

import bisq.core.dao.state.DaoStateListener;
import bisq.core.dao.state.DaoStateService;
import bisq.core.dao.state.model.blockchain.Block;

import javax.inject.Inject;
import javax.inject.Singleton;

import com.google.common.annotations.VisibleForTesting;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.stream.Collectors;

@Singleton
public class BtcFeeReceiverService implements DaoStateListener {
    private final BurningManService burningManService;

    private int currentChainHeight;

    @Inject
    public BtcFeeReceiverService(DaoStateService daoStateService, BurningManService burningManService) {
        this.burningManService = burningManService;

        daoStateService.addDaoStateListener(this);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // DaoStateListener
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void onParseBlockCompleteAfterBatchProcessing(Block block) {
        currentChainHeight = block.getHeight();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // API
    ///////////////////////////////////////////////////////////////////////////////////////////

    public String getAddress() {
        Map<String, BurningManCandidate> burningManCandidatesByName = burningManService.getBurningManCandidatesByName(currentChainHeight);
        if (burningManCandidatesByName.isEmpty()) {
            // If there are no compensation requests (e.g. at dev testing) we fall back to the default address
            return burningManService.getLegacyBurningManAddress(currentChainHeight);
        }

        // It might be that we do not reach 100% if some entries had a capped effectiveBurnOutputShare.
        // We ignore that here as there is no risk for abuse. Each entry in the group would have a higher chance in
        // that case.
        // effectiveBurnOutputShare is a % value represented as double. Smallest supported value is 0.01% -> 0.0001.
        // By multiplying it with 10000 and using Math.floor we limit the candidate to 0.01%.
        // Entries with 0 will be ignored in the selection method.
        List<BurningManCandidate> burningManCandidates = new ArrayList<>(burningManCandidatesByName.values());
        List<Long> amountList = burningManCandidates.stream()
                .map(BurningManCandidate::getEffectiveBurnOutputShare)
                .map(effectiveBurnOutputShare -> (long) Math.floor(effectiveBurnOutputShare * 10000))
                .collect(Collectors.toList());
        if (amountList.isEmpty()) {
            return burningManService.getLegacyBurningManAddress(currentChainHeight);
        }
        int winnerIndex = getRandomIndex(amountList, new Random());
        if (winnerIndex == -1) {
            return burningManService.getLegacyBurningManAddress(currentChainHeight);
        }
        return burningManCandidates.get(winnerIndex).getMostRecentAddress()
                .orElse(burningManService.getLegacyBurningManAddress(currentChainHeight));
    }

    @VisibleForTesting
    static int getRandomIndex(List<Long> weights, Random random) {
        long sum = weights.stream().mapToLong(n -> n).sum();
        if (sum == 0) {
            return -1;
        }
        long target = random.longs(0, sum).findFirst().orElseThrow() + 1;
        return findIndex(weights, target);
    }

    @VisibleForTesting
    static int findIndex(List<Long> weights, long target) {
        int currentRange = 0;
        for (int i = 0; i < weights.size(); i++) {
            currentRange += weights.get(i);
            if (currentRange >= target) {
                return i;
            }
        }
        return 0;
    }
}
