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

package bisq.desktop.main.offer.offerbook;

import bisq.desktop.Navigation;
import bisq.desktop.main.MainView;
import bisq.desktop.main.overlays.popups.Popup;
import bisq.desktop.main.settings.SettingsView;
import bisq.desktop.main.settings.preferences.PreferencesView;

import bisq.core.locale.Res;
import bisq.core.user.Preferences;

import bisq.common.Timer;
import bisq.common.UserThread;

import java.math.BigDecimal;

import java.util.function.BooleanSupplier;

import javax.annotation.Nullable;

/**
 * The popup which tells the user that the BSQ offer price range hides every offer, with a button to the preferences.
 * A view passes through intermediate states: a tab is selected before its view is activated, an edited offer is
 * removed before its new version is added, and offers arrive one by one. The popup therefore waits until the state
 * has settled, and it is shown at most once until {@link #reset()}.
 */
public class BsqOfferPriceRangeHint {
    private static final long DELAY_SEC = 1;
    private static final String DONT_SHOW_AGAIN_KEY = "bsqOfferBookAllOffersOutsidePriceRange";

    private final Preferences preferences;
    private final Navigation navigation;
    private final BooleanSupplier isApplicable;
    @Nullable
    private Timer timer;
    private boolean isShown;

    /**
     * @param isApplicable whether the view is visible and the range hides every offer which it would show
     */
    public BsqOfferPriceRangeHint(Preferences preferences, Navigation navigation, BooleanSupplier isApplicable) {
        this.preferences = preferences;
        this.navigation = navigation;
        this.isApplicable = isApplicable;
    }

    // To call whenever isApplicable may have changed
    public void update() {
        stopTimer();
        if (isApplicable.getAsBoolean()) {
            timer = UserThread.runAfter(this::showIfStillApplicable, DELAY_SEC);
        }
    }

    // Ends a visit of the view: cancels a pending popup, and the next visit may show it again
    public void reset() {
        stopTimer();
        isShown = false;
    }

    private void stopTimer() {
        if (timer != null) {
            timer.stop();
            timer = null;
        }
    }

    private void showIfStillApplicable() {
        timer = null;
        if (isShown || !isApplicable.getAsBoolean()) {
            return;
        }

        isShown = true;
        String factor = BigDecimal.valueOf(preferences.getBsqOfferPriceRangeFactor()).stripTrailingZeros().toPlainString();
        new Popup().information(Res.get("offerbook.bsq.allOffersOutsidePriceRange", factor))
                .actionButtonTextWithGoTo("navigation.settings.preferences")
                .onAction(() -> navigation.navigateTo(MainView.class, SettingsView.class, PreferencesView.class))
                .closeButtonText(Res.get("shared.close"))
                .dontShowAgainId(DONT_SHOW_AGAIN_KEY)
                .show();
    }
}
