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

package bisq.desktop.main.settings.preferences;


import bisq.desktop.common.model.ActivatableViewModel;

import bisq.core.arbitration.ArbitratorManager;
import bisq.core.locale.LanguageUtil;
import bisq.core.user.Preferences;

import com.google.inject.Inject;

import java.util.stream.Collectors;

public class PreferencesViewModel extends ActivatableViewModel {

    private final ArbitratorManager arbitratorManager;
    private final Preferences preferences;

    @Inject
    public PreferencesViewModel(Preferences preferences, ArbitratorManager arbitratorManager) {
        this.preferences = preferences;
        this.arbitratorManager = arbitratorManager;
    }

    boolean needsArbitrationLanguageWarning() {
        return !arbitratorManager.isArbitratorAvailableForLanguage(preferences.getUserLanguage());
    }

    String getArbitrationLanguages() {
        return arbitratorManager.getArbitratorsObservableMap().values().stream()
                .flatMap(arbitrator -> arbitrator.getLanguageCodes().stream())
                .distinct()
                .map(languageCode -> LanguageUtil.getDisplayName(languageCode))
                .collect(Collectors.joining(", "));
    }
}
