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

package bisq.desktop.main.presentation;

import bisq.desktop.main.overlays.popups.Popup;

import bisq.core.locale.Res;
import bisq.core.user.DontShowAgainLookup;
import bisq.core.user.Preferences;

import bisq.common.app.DevEnv;

import javax.inject.Inject;
import javax.inject.Singleton;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;

import javafx.collections.MapChangeListener;


@Singleton
public class AccountPresentation {

    public static final String ACCOUNT_NEWS = "accountNews_XmrSubAddresses";

    private Preferences preferences;

    private final SimpleBooleanProperty showNotification = new SimpleBooleanProperty(false);

    @Inject
    public AccountPresentation(Preferences preferences) {

        this.preferences = preferences;

        preferences.getDontShowAgainMapAsObservable().addListener((MapChangeListener<? super String, ? super Boolean>) change -> {
            if (change.getKey().equals(ACCOUNT_NEWS)) {
                // devs enable this when a news badge is required
                // showNotification.set(!change.wasAdded());
                showNotification.set(false);
            }
        });
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Public
    ///////////////////////////////////////////////////////////////////////////////////////////

    public BooleanProperty getShowAccountUpdatesNotification() {
        return showNotification;
    }

    public void setup() {
        // devs enable this when a news badge is required
        //showNotification.set(preferences.showAgain(ACCOUNT_NEWS));
        showNotification.set(false);
    }

    public void showOneTimeAccountSigningPopup(String key, String message) {
        showOneTimeAccountSigningPopup(key, message, null);
    }

    public void showOneTimeAccountSigningPopup(String key, String message, String optionalParam) {
        if (!DevEnv.isDevMode()) {

            DontShowAgainLookup.dontShowAgain(ACCOUNT_NEWS, false);
            showNotification.set(true);

            DontShowAgainLookup.dontShowAgain(key, true);
            String information = optionalParam != null ?
                    Res.get(message, optionalParam, Res.get("popup.accountSigning.generalInformation")) :
                    Res.get(message, Res.get("popup.accountSigning.generalInformation"));

            new Popup().information(information).show();
        }
    }
}
