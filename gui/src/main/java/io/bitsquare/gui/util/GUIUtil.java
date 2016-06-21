/*
 * This file is part of Bitsquare.
 *
 * Bitsquare is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * Bitsquare is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Bitsquare. If not, see <http://www.gnu.org/licenses/>.
 */

package io.bitsquare.gui.util;

import io.bitsquare.app.DevFlags;
import io.bitsquare.gui.main.overlays.popups.Popup;
import io.bitsquare.user.Preferences;
import javafx.geometry.Orientation;
import javafx.scene.Node;
import javafx.scene.control.ScrollBar;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GUIUtil {
    private static final Logger log = LoggerFactory.getLogger(GUIUtil.class);

    public static double getScrollbarWidth(Node scrollablePane) {
        Node node = scrollablePane.lookup(".scroll-bar");
        if (node instanceof ScrollBar) {
            final ScrollBar bar = (ScrollBar) node;
            if (bar.getOrientation().equals(Orientation.VERTICAL))
                return bar.getWidth();
        }
        return 0;
    }

    public static void showFeeInfoBeforeExecute(Runnable runnable) {
        String key = "miningFeeInfo";
        if (!DevFlags.DEV_MODE && Preferences.INSTANCE.showAgain(key)) {
            new Popup<>().information("Please be sure that the mining fee used at your external wallet is " +
                    "sufficiently high so that the funding transaction will be accepted by the miners.\n" +
                    "Otherwise the trade transactions cannot be confirmed and a trade would end up in a dispute.\n\n" +
                    "The recommended fee is about 0.0001 - 0.0002 BTC.\n\n" +
                    "You can view typically used fees at: https://tradeblock.com/blockchain")
                    .dontShowAgainId(key, Preferences.INSTANCE)
                    .onClose(runnable::run)
                    .closeButtonText("I understand")
                    .show();
        } else {
            runnable.run();
        }
    }
}
