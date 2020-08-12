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

import bisq.core.locale.LanguageUtil;
import bisq.core.support.dispute.mediation.mediator.MediatorManager;
import bisq.core.support.dispute.refund.refundagent.RefundAgentManager;
import bisq.core.user.Preferences;

import com.google.inject.Inject;

import java.util.stream.Collectors;

public class PreferencesViewModel extends ActivatableViewModel {

    private final RefundAgentManager refundAgentManager;
    private final MediatorManager mediationManager;
    private final Preferences preferences;

    @Inject
    public PreferencesViewModel(Preferences preferences,
                                RefundAgentManager refundAgentManager,
                                MediatorManager mediationManager) {
        this.preferences = preferences;
        this.refundAgentManager = refundAgentManager;
        this.mediationManager = mediationManager;
    }

    boolean needsSupportLanguageWarning() {
        return !refundAgentManager.isAgentAvailableForLanguage(preferences.getUserLanguage()) ||
                !mediationManager.isAgentAvailableForLanguage(preferences.getUserLanguage());
    }

    String getArbitrationLanguages() {
        return refundAgentManager.getObservableMap().values().stream()
                .flatMap(arbitrator -> arbitrator.getLanguageCodes().stream())
                .distinct()
                .map(LanguageUtil::getDisplayName)
                .collect(Collectors.joining(", "));
    }

    public String getMediationLanguages() {
        return mediationManager.getObservableMap().values().stream()
                .flatMap(mediator -> mediator.getLanguageCodes().stream())
                .distinct()
                .map(LanguageUtil::getDisplayName)
                .collect(Collectors.joining(", "));
    }
}
