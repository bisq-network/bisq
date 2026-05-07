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

package bisq.core.trade.validation;

import bisq.core.dao.burningman.DelayedPayoutTxReceiverService;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

public final class DelayedPayoutTxValidation {
    private DelayedPayoutTxValidation() {
    }

    public static int checkBurningManSelectionHeight(int burningManSelectionHeight,
                                                     DelayedPayoutTxReceiverService delayedPayoutTxReceiverService) {
        checkArgument(burningManSelectionHeight > 0,
                "burningManSelectionHeight must be positive");
        checkNotNull(delayedPayoutTxReceiverService, "delayedPayoutTxReceiverService must not be null");

        int expectedBurningManSelectionHeight = delayedPayoutTxReceiverService.getBurningManSelectionHeight();
        checkArgument(expectedBurningManSelectionHeight > 0,
                "expectedBurningManSelectionHeight must be positive");

        if (burningManSelectionHeight != expectedBurningManSelectionHeight) {
            // Allow SNAPSHOT_SELECTION_GRID_SIZE (10 blocks) as tolerance if traders had different heights.
            int diff = Math.abs(burningManSelectionHeight - expectedBurningManSelectionHeight);
            checkArgument(diff == DelayedPayoutTxReceiverService.SNAPSHOT_SELECTION_GRID_SIZE,
                    "If Burning Man selection heights are not the same they have to differ by " +
                            "exactly the snapshot grid size, otherwise we fail. " +
                            "burningManSelectionHeight=%s, expectedBurningManSelectionHeight=%s, diff=%s",
                    burningManSelectionHeight, expectedBurningManSelectionHeight, diff);

        }
        return burningManSelectionHeight;
    }
}
