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

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class DelayedPayoutTxValidationTest {
    private static final int GENESIS_HEIGHT = 102;
    private static final int GRID_SIZE = DelayedPayoutTxReceiverService.SNAPSHOT_SELECTION_GRID_SIZE;

    @Test
    void checkBurningManSelectionHeightAcceptsSameHeight() {
        assertEquals(130,
                DelayedPayoutTxReceiverService.getSnapshotHeight(GENESIS_HEIGHT, 139, GRID_SIZE, 0));

        assertEquals(130,
                DelayedPayoutTxValidation.checkBurningManSelectionHeight(130, delayedPayoutTxReceiverService(130)));
    }

    @Test
    void checkBurningManSelectionHeightAcceptsMakerOneGridAhead() {
        assertEquals(120,
                DelayedPayoutTxReceiverService.getSnapshotHeight(GENESIS_HEIGHT, 134, GRID_SIZE, 0));
        assertEquals(130,
                DelayedPayoutTxReceiverService.getSnapshotHeight(GENESIS_HEIGHT, 135, GRID_SIZE, 0));

        assertEquals(120,
                DelayedPayoutTxValidation.checkBurningManSelectionHeight(120, delayedPayoutTxReceiverService(130)));
    }

    @Test
    void checkBurningManSelectionHeightAcceptsTakerOneGridAhead() {
        assertEquals(120,
                DelayedPayoutTxReceiverService.getSnapshotHeight(GENESIS_HEIGHT, 134, GRID_SIZE, 0));
        assertEquals(130,
                DelayedPayoutTxReceiverService.getSnapshotHeight(GENESIS_HEIGHT, 135, GRID_SIZE, 0));

        assertEquals(130,
                DelayedPayoutTxValidation.checkBurningManSelectionHeight(130, delayedPayoutTxReceiverService(120)));
    }

    @Test
    void checkBurningManSelectionHeightRejectsPeerHeightZero() {
        assertThrows(IllegalArgumentException.class,
                () -> DelayedPayoutTxValidation.checkBurningManSelectionHeight(0, delayedPayoutTxReceiverService(10)));
    }

    @Test
    void checkBurningManSelectionHeightRejectsHeightsMoreThanOneGridApart() {
        assertThrows(IllegalArgumentException.class,
                () -> DelayedPayoutTxValidation.checkBurningManSelectionHeight(120, delayedPayoutTxReceiverService(140)));
    }

    @Test
    void checkBurningManSelectionHeightRejectsLocalHeightZero() {
        assertThrows(IllegalArgumentException.class,
                () -> DelayedPayoutTxValidation.checkBurningManSelectionHeight(10, delayedPayoutTxReceiverService(0)));
    }

    private static DelayedPayoutTxReceiverService delayedPayoutTxReceiverService(int burningManSelectionHeight) {
        DelayedPayoutTxReceiverService delayedPayoutTxReceiverService = mock(DelayedPayoutTxReceiverService.class);
        when(delayedPayoutTxReceiverService.getBurningManSelectionHeight()).thenReturn(burningManSelectionHeight);
        return delayedPayoutTxReceiverService;
    }
}
