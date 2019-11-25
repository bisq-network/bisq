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

    public static final String ACCOUNT_NEWS = "accountNews";

    private Preferences preferences;

    private final SimpleBooleanProperty showNotification = new SimpleBooleanProperty(false);

    @Inject
    public AccountPresentation(Preferences preferences) {

        this.preferences = preferences;

        preferences.getDontShowAgainMapAsObservable().addListener((MapChangeListener<? super String, ? super Boolean>) change -> {
            if (change.getKey().equals(ACCOUNT_NEWS)) {
                showNotification.set(!change.wasAdded());
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
        showNotification.set(preferences.showAgain(ACCOUNT_NEWS));
    }

    public void showOneTimeAccountSigningPopup(String key, String s) {
        showOneTimeAccountSigningPopup(key, s, null);
    }

    public void showOneTimeAccountSigningPopup(String key, String s, String optionalParam) {
        if (!DevEnv.isDevMode()) {

            DontShowAgainLookup.dontShowAgain(ACCOUNT_NEWS, false);
            showNotification.set(true);

            DontShowAgainLookup.dontShowAgain(key, true);
            String message = optionalParam != null ?
                    Res.get(s, optionalParam, Res.get("popup.accountSigning.generalInformation")) :
                    Res.get(s, Res.get("popup.accountSigning.generalInformation"));

            new Popup().information(message)
                    .show();
        }
    }
}
