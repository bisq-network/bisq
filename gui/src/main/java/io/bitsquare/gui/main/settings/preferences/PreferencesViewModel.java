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

package io.bitsquare.gui.main.settings.preferences;

import com.google.inject.Inject;
import io.bitsquare.gui.common.model.ActivatableViewModel;
import io.bitsquare.locale.LanguageUtil;
import io.bitsquare.locale.TradeCurrency;
import io.bitsquare.user.BlockChainExplorer;
import io.bitsquare.user.Preferences;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

import java.util.Locale;

class PreferencesViewModel extends ActivatableViewModel {

    private final Preferences preferences;
    final ObservableList<String> btcDenominations = FXCollections.observableArrayList(Preferences.getBtcDenominations());
    final ObservableList<BlockChainExplorer> blockExplorers;
    final ObservableList<TradeCurrency> tradeCurrencies;
    final ObservableList<String> languageCodes;
    

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor, initialisation
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    public PreferencesViewModel(Preferences preferences) {
        this.preferences = preferences;

        blockExplorers = FXCollections.observableArrayList(preferences.getBlockChainExplorers());
        tradeCurrencies = preferences.getTradeCurrenciesAsObservable();
        languageCodes = FXCollections.observableArrayList(LanguageUtil.getAllLanguageCodes());
    }

    @Override
    protected void activate() {
    }

    @Override
    protected void deactivate() {
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // UI actions
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void onSelectBtcDenomination(String selectedItem) {
        preferences.setBtcDenomination(selectedItem);
    }

    public void onSelectUseEffects(boolean selected) {
        preferences.setUseEffects(selected);
    }

    public void onSelectUseAnimations(boolean selected) {
        preferences.setUseAnimations(selected);
    }

    public void onSelectShowPlaceOfferConfirmation(boolean selected) {
        preferences.setShowPlaceOfferConfirmation(selected);
    }

    public void onSelectShowTakeOfferConfirmation(boolean selected) {
        preferences.setShowTakeOfferConfirmation(selected);
    }

    public void onSelectAutoSelectArbitratorsCheckBox(boolean selected) {
        preferences.setAutoSelectArbitrators(selected);
    }

    public void onSelectBlockExplorer(BlockChainExplorer selectedItem) {
        preferences.setBlockChainExplorer(selectedItem);
    }

    public void onSelectTradeCurrency(TradeCurrency selectedItem) {
        preferences.setPreferredTradeCurrency(selectedItem);
    }

    public void onSelectLanguageCode(String code) {
        preferences.setPreferredLocale(new Locale(code, preferences.getPreferredLocale().getCountry()));
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Getters
    ///////////////////////////////////////////////////////////////////////////////////////////

    public String getBtcDenomination() {
        return preferences.getBtcDenomination();
    }

    public boolean getUseAnimations() {
        return preferences.getUseAnimations();
    }

    public boolean getUseEffects() {
        return preferences.getUseEffects();
    }

    public boolean getShowPlaceOfferConfirmation() {
        return preferences.getShowPlaceOfferConfirmation();
    }

    public boolean getShowTakeOfferConfirmation() {
        return preferences.getShowTakeOfferConfirmation();
    }

    public boolean getAutoSelectArbitrators() {
        return preferences.getAutoSelectArbitrators();
    }

    public BlockChainExplorer getBlockExplorer() {
        return preferences.getBlockChainExplorer();
    }

    public String getLanguageCode() {
        return preferences.getPreferredLocale().getLanguage();
    }

    public TradeCurrency getTradeCurrency() {
        return preferences.getPreferredTradeCurrency();
    }
}
