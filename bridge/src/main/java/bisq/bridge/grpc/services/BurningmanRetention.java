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

package bisq.bridge.grpc.services;

import static com.google.common.base.Preconditions.checkArgument;

public class BurningmanRetention {
    public static boolean includeBlock(int chainHeightTip, int blockHeight) {
        checkArgument(blockHeight <= chainHeightTip, "blockHeight must not be higher than chainHeightTip");
        int cutOffHeightMod10 = Math.max(0, chainHeightTip - 100);
        int cutOffHeightMod100 = Math.max(0, chainHeightTip - 1000);
        int cutOffHeightMod1000 = Math.max(0, chainHeightTip - 10000);
        if (blockHeight > cutOffHeightMod10) {
            return blockHeight % 10 == 0; // evey possible snapshot block of the past 100 blocks (about 16 hours);
        } else if (blockHeight > cutOffHeightMod100) {
            return blockHeight % 100 == 0;  // evey 10th possible snapshot block of the past 1000 blocks (about 7 days)
        } else if (blockHeight > cutOffHeightMod1000) {
            return blockHeight % 1000 == 0;  // evey 100th possible snapshot block of the past 10000 blocks (about 70 days or > 2 months)
        }
        return false;
    }
}
