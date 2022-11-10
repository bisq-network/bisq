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


import org.mockito.junit.MockitoJUnitRunner;

import org.junit.Test;
import org.junit.runner.RunWith;

import static org.junit.Assert.assertEquals;

@RunWith(MockitoJUnitRunner.class)
public class DelayedPayoutTxReceiverServiceTest {
    @Test
    public void testGetSnapshotHeight() {
        // up to genesis + 3* grid we use genesis
        assertEquals(102, DelayedPayoutTxReceiverService.getSnapshotHeight(102, 0, 10));
        assertEquals(102, DelayedPayoutTxReceiverService.getSnapshotHeight(102, 100, 10));
        assertEquals(102, DelayedPayoutTxReceiverService.getSnapshotHeight(102, 102, 10));
        assertEquals(102, DelayedPayoutTxReceiverService.getSnapshotHeight(102, 119, 10));
        assertEquals(102, DelayedPayoutTxReceiverService.getSnapshotHeight(102, 120, 10));
        assertEquals(102, DelayedPayoutTxReceiverService.getSnapshotHeight(102, 121, 10));
        assertEquals(102, DelayedPayoutTxReceiverService.getSnapshotHeight(102, 130, 10));
        assertEquals(102, DelayedPayoutTxReceiverService.getSnapshotHeight(102, 131, 10));
        assertEquals(102, DelayedPayoutTxReceiverService.getSnapshotHeight(102, 132, 10));

        assertEquals(120, DelayedPayoutTxReceiverService.getSnapshotHeight(102, 133, 10));
        assertEquals(120, DelayedPayoutTxReceiverService.getSnapshotHeight(102, 134, 10));

        assertEquals(130, DelayedPayoutTxReceiverService.getSnapshotHeight(102, 135, 10));
        assertEquals(130, DelayedPayoutTxReceiverService.getSnapshotHeight(102, 136, 10));
        assertEquals(130, DelayedPayoutTxReceiverService.getSnapshotHeight(102, 139, 10));
        assertEquals(130, DelayedPayoutTxReceiverService.getSnapshotHeight(102, 140, 10));
        assertEquals(130, DelayedPayoutTxReceiverService.getSnapshotHeight(102, 141, 10));

        assertEquals(140, DelayedPayoutTxReceiverService.getSnapshotHeight(102, 149, 10));
        assertEquals(140, DelayedPayoutTxReceiverService.getSnapshotHeight(102, 150, 10));
        assertEquals(140, DelayedPayoutTxReceiverService.getSnapshotHeight(102, 151, 10));

        assertEquals(150, DelayedPayoutTxReceiverService.getSnapshotHeight(102, 159, 10));

        assertEquals(990, DelayedPayoutTxReceiverService.getSnapshotHeight(102, 1000, 10));
    }
}
