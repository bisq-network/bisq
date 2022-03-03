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

import bisq.core.user.Preferences;

import javax.inject.Inject;
import javax.inject.Singleton;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;

import javafx.collections.MapChangeListener;


@Singleton
public class SettingsPresentation {

    public static final String SETTINGS_BADGE_KEY = "settingsPrivacyFeature";

    private Preferences preferences;

    private final SimpleBooleanProperty showNotification = new SimpleBooleanProperty(false);

    @Inject
    public SettingsPresentation(Preferences preferences) {

        this.preferences = preferences;

        preferences.getDontShowAgainMapAsObservable().addListener((MapChangeListener<? super String, ? super Boolean>) change -> {
            if (change.getKey().equals(SETTINGS_BADGE_KEY)) {
                showNotification.set(!change.wasAdded());
            }
        });
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Public
    ///////////////////////////////////////////////////////////////////////////////////////////

    public BooleanProperty getShowSettingsUpdatesNotification() {
        return showNotification;
    }

    public void setup() {
        showNotification.set(preferences.showAgain(SETTINGS_BADGE_KEY));
    }
}
