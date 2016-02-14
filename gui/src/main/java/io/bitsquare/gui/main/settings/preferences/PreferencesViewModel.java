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
import io.bitsquare.common.UserThread;
import io.bitsquare.gui.common.model.ActivatableViewModel;
import io.bitsquare.gui.main.popups.Popup;
import io.bitsquare.locale.LanguageUtil;
import io.bitsquare.locale.TradeCurrency;
import io.bitsquare.user.BlockChainExplorer;
import io.bitsquare.user.Preferences;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

import java.util.Locale;
import java.util.concurrent.TimeUnit;

class PreferencesViewModel extends ActivatableViewModel {

    private final Preferences preferences;
    final ObservableList<String> btcDenominations = FXCollections.observableArrayList(Preferences.getBtcDenominations());
    final ObservableList<BlockChainExplorer> blockExplorers;
    final ObservableList<TradeCurrency> tradeCurrencies;
    final ObservableList<String> languageCodes;
    final StringProperty transactionFeePerByte = new SimpleStringProperty();


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
        transactionFeePerByte.set(String.valueOf(preferences.getTxFeePerKB() / 1000));
    }

    @Override
    protected void deactivate() {
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // UI actions
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void onSelectLanguageCode(String code) {
        preferences.setPreferredLocale(new Locale(code, preferences.getPreferredLocale().getCountry()));
    }

    public void onFocusOutTransactionFeeTextField(Boolean oldValue, Boolean newValue) {
        if (oldValue && !newValue) {
            try {
                preferences.setTxFeePerKB(Long.parseLong(transactionFeePerByte.get()) * 1000);
            } catch (Exception e) {
                log.warn("Error at onFocusOutTransactionFeeTextField: " + e.getMessage());
                new Popup().warning(e.getMessage())
                        .onClose(() -> UserThread.runAfter(
                                () -> transactionFeePerByte.set(String.valueOf(preferences.getTxFeePerKB() / 1000)),
                                100, TimeUnit.MILLISECONDS))
                        .show();
            }
        }
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Getters
    ///////////////////////////////////////////////////////////////////////////////////////////

    public String getLanguageCode() {
        return preferences.getPreferredLocale().getLanguage();
    }

}
