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

package bisq.desktop.main.overlays.windows.supporttool;

import javafx.scene.layout.GridPane;

public abstract class CommonPane extends GridPane {

    protected static final int HEX_HASH_LENGTH = 32 * 2;
    protected static final int HEX_PUBKEY_LENGTH = 33 * 2;

    public void activate() {
        this.setVisible(true);
    }

    public void cleanup() {
    }

    public abstract String getName();

    protected static String findPrivForPubOrAddress(String walletInfo, String searchKey) {
        // split the walletInfo into lines, strip whitespace
        // look for lines beginning "  addr:" followed by "DeterministicKey{pub HEX=" .... ", priv HEX="
        int lineIndex = 0;
        while (lineIndex < walletInfo.length() && lineIndex != -1) {
            lineIndex = walletInfo.indexOf("  addr:", lineIndex);
            if (lineIndex == -1) {
                return  null;
            }
            int toIndex = walletInfo.indexOf("}", lineIndex);
            if (toIndex == -1) {
                return  null;
            }
            String candidate1 = walletInfo.substring(lineIndex, toIndex);
            lineIndex = toIndex;
            // do we have the search key?
            if (candidate1.indexOf(searchKey, 0) > -1) {
                int startOfPriv = candidate1.indexOf("priv HEX=", 0);
                if (startOfPriv > -1) {
                    return candidate1.substring(startOfPriv + 9, startOfPriv + 9 + HEX_HASH_LENGTH);
                }
            }
        }
        return null;
    }
}
