/*
 * This file is part of bisq.
 *
 * bisq is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * bisq is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with bisq. If not, see <http://www.gnu.org/licenses/>.
 */

package io.bisq.gui.main.account.content.arbitratorselection;

import com.google.inject.Inject;
import io.bisq.common.locale.LanguageUtil;
import io.bisq.core.arbitration.ArbitratorManager;
import io.bisq.core.user.Preferences;
import io.bisq.core.user.User;
import io.bisq.gui.common.model.ActivatableDataModel;
import io.bisq.gui.util.BSFormatter;
import io.bisq.protobuffer.crypto.KeyRing;
import io.bisq.protobuffer.payload.arbitration.Arbitrator;
import io.bisq.protobuffer.payload.p2p.NodeAddress;
import javafx.collections.FXCollections;
import javafx.collections.MapChangeListener;
import javafx.collections.ObservableList;

import java.util.stream.Collectors;

class ArbitratorSelectionViewModel extends ActivatableDataModel {
    private final User user;
    private final ArbitratorManager arbitratorManager;
    private final Preferences preferences;
    private final KeyRing keyRing;
    private final BSFormatter formatter;
    final ObservableList<String> languageCodes = FXCollections.observableArrayList();
    final ObservableList<ArbitratorListItem> arbitratorListItems = FXCollections.observableArrayList();
    final ObservableList<String> allLanguageCodes = FXCollections.observableArrayList(LanguageUtil.getAllLanguageCodes());
    private final MapChangeListener<NodeAddress, Arbitrator> arbitratorMapChangeListener;

    @Inject
    public ArbitratorSelectionViewModel(User user, ArbitratorManager arbitratorManager, Preferences preferences,
                                        KeyRing keyRing, BSFormatter formatter) {
        this.user = user;
        this.arbitratorManager = arbitratorManager;
        this.preferences = preferences;
        this.keyRing = keyRing;
        this.formatter = formatter;

        arbitratorMapChangeListener = change -> applyArbitratorMap();
    }

    private void applyArbitratorMap() {
        arbitratorListItems.clear();
        arbitratorListItems.addAll(arbitratorManager.getArbitratorsObservableMap().values().stream()
                .map(e -> new ArbitratorListItem(e, formatter)).collect(Collectors.toList()));
    }

    @Override
    protected void activate() {
        languageCodes.setAll(user.getAcceptedLanguageLocaleCodes());
        arbitratorManager.getArbitratorsObservableMap().addListener(arbitratorMapChangeListener);
        arbitratorManager.updateArbitratorMap();
        applyArbitratorMap();

        updateAutoSelectArbitrators();
    }

    @Override
    protected void deactivate() {
        arbitratorManager.getArbitratorsObservableMap().removeListener(arbitratorMapChangeListener);
    }

    void onAddLanguage(String code) {
        if (code != null) {
            boolean changed = user.addAcceptedLanguageLocale(code);
            if (changed)
                languageCodes.add(code);
        }

        updateAutoSelectArbitrators();
    }

    void onRemoveLanguage(String code) {
        if (code != null) {
            boolean changed = user.removeAcceptedLanguageLocale(code);
            if (changed)
                languageCodes.remove(code);
        }

        updateAutoSelectArbitrators();
    }

    void onAddArbitrator(Arbitrator arbitrator) {
        if (!arbitratorIsTrader(arbitrator))
            user.addAcceptedArbitrator(arbitrator);
    }

    void onRemoveArbitrator(Arbitrator arbitrator) {
        if (arbitrator != null)
            user.removeAcceptedArbitrator(arbitrator);
    }

    public boolean isDeselectAllowed(ArbitratorListItem arbitratorListItem) {
        return arbitratorListItem != null
                && user.getAcceptedArbitrators() != null
                && user.getAcceptedArbitrators().size() > 1;
    }

    public boolean isAcceptedArbitrator(Arbitrator arbitrator) {
        if (arbitrator != null && user.getAcceptedArbitrators() != null)
            return user.getAcceptedArbitrators().contains(arbitrator) && !isMyOwnRegisteredArbitrator(arbitrator);
        else
            return false;
    }

    public boolean arbitratorIsTrader(Arbitrator arbitrator) {
        return keyRing.getPubKeyRing().equals(arbitrator.getPubKeyRing());
    }

    public boolean hasMatchingLanguage(Arbitrator arbitrator) {
        return user.hasMatchingLanguage(arbitrator);
    }

    public boolean isMyOwnRegisteredArbitrator(Arbitrator arbitrator) {
        return user.isMyOwnRegisteredArbitrator(arbitrator);
    }

    private void updateAutoSelectArbitrators() {
        if (preferences.getAutoSelectArbitrators()) {
            arbitratorListItems.stream().forEach(item -> {
                Arbitrator arbitrator = item.arbitrator;
                if (!isMyOwnRegisteredArbitrator(arbitrator)) {
                    if (hasMatchingLanguage(arbitrator)) {
                        onAddArbitrator(arbitrator);
                        item.setIsSelected(true);
                    } else {
                        onRemoveArbitrator(arbitrator);
                        item.setIsSelected(false);
                    }
                } else {
                    item.setIsSelected(false);
                }
            });
        }
    }

    public void setAutoSelectArbitrators(boolean doAutoSelect) {
        preferences.setAutoSelectArbitrators(doAutoSelect);
        updateAutoSelectArbitrators();
    }

    public boolean getAutoSelectArbitrators() {
        return preferences.getAutoSelectArbitrators();
    }
}
