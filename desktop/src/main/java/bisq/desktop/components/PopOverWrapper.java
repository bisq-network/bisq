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

package bisq.desktop.components;

import bisq.desktop.components.controlsfx.control.PopOver;

import bisq.common.UserThread;

import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

public class PopOverWrapper {

    private PopOver popover;
    private Supplier<PopOver> popoverSupplier;
    private boolean hidePopover;
    private PopOverState state = PopOverState.HIDDEN;

    enum PopOverState {
        HIDDEN, SHOWING, SHOWN, HIDING
    }

    public void showPopOver(Supplier<PopOver> popoverSupplier) {
        this.popoverSupplier = popoverSupplier;
        hidePopover = false;

        if (state == PopOverState.HIDDEN) {
            state = PopOverState.SHOWING;
            popover = popoverSupplier.get();

            UserThread.runAfter(() -> {
                state = PopOverState.SHOWN;
                if (hidePopover) {
                    // For some reason, this can result in a brief flicker when invoked
                    // from a 'runAfter' callback, rather than directly. So make the delay
                    // very short (25ms) so that we don't reach here often:
                    hidePopOver();
                }
            }, 25, TimeUnit.MILLISECONDS);
        }
    }

    public void hidePopOver() {
        hidePopover = true;

        if (state == PopOverState.SHOWN) {
            state = PopOverState.HIDING;
            popover.hide();

            UserThread.runAfter(() -> {
                state = PopOverState.HIDDEN;
                if (!hidePopover) {
                    showPopOver(popoverSupplier);
                }
            }, 250, TimeUnit.MILLISECONDS);
        }
    }
}
