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

import bisq.core.dao.burningman.model.BurningManCandidate;
import bisq.core.dao.state.DaoStateListener;
import bisq.core.dao.state.DaoStateService;
import bisq.core.dao.state.model.blockchain.Block;

import javax.inject.Inject;
import javax.inject.Singleton;

import com.google.common.annotations.VisibleForTesting;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Singleton
public class BtcFeeReceiverService implements DaoStateListener {
    private final BurningManService burningManService;

    private int currentChainHeight;

    @Inject
    public BtcFeeReceiverService(DaoStateService daoStateService, BurningManService burningManService) {
        this.burningManService = burningManService;

        daoStateService.addDaoStateListener(this);
        daoStateService.getLastBlock().ifPresent(this::applyBlock);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // DaoStateListener
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void onParseBlockCompleteAfterBatchProcessing(Block block) {
        applyBlock(block);
    }

    private void applyBlock(Block block) {
        currentChainHeight = block.getHeight();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // API
    ///////////////////////////////////////////////////////////////////////////////////////////

    public String getAddress() {
        List<BurningManCandidate> activeBurningManCandidates = new ArrayList<>(burningManService.getActiveBurningManCandidates(currentChainHeight));
        if (activeBurningManCandidates.isEmpty()) {
            // If there are no compensation requests (e.g. at dev testing) we fall back to the default address
            return burningManService.getLegacyBurningManAddress(currentChainHeight);
        }

        // It might be that we do not reach 100% if some entries had a cappedBurnAmountShare.
        // In that case we fill up the gap to 100% with the legacy BM.
        // cappedBurnAmountShare is a % value represented as double. Smallest supported value is 0.01% -> 0.0001.
        // By multiplying it with 10000 and using Math.floor we limit the candidate to 0.01%.
        // Entries with 0 will be ignored in the selection method, so we do not need to filter them out.
        int ceiling = 10000;
        List<Long> amountList = activeBurningManCandidates.stream()
                .map(BurningManCandidate::getCappedBurnAmountShare)
                .map(cappedBurnAmountShare -> (long) Math.floor(cappedBurnAmountShare * ceiling))
                .collect(Collectors.toList());
        long sum = amountList.stream().mapToLong(e -> e).sum();
        // If we have not reached the 100% we fill the missing gap with the legacy BM
        if (sum < ceiling) {
            amountList.add(ceiling - sum);
        }

        int winnerIndex = getRandomIndex(amountList, new Random());
        if (winnerIndex == activeBurningManCandidates.size()) {
            // If we have filled up the missing gap to 100% with the legacy BM we would get an index out of bounds of
            // the burningManCandidates as we added for the legacy BM an entry at the end.
            return burningManService.getLegacyBurningManAddress(currentChainHeight);
        }
        // For the fee selection we do not need to wait for activation date of the bugfix for
        // the receiver address (https://github.com/bisq-network/bisq/issues/6699) as it has no impact on the trade protocol.
        return activeBurningManCandidates.get(winnerIndex).getReceiverAddress(true)
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
