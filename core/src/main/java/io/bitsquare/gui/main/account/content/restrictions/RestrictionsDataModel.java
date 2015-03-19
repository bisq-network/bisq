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

package io.bitsquare.gui.main.account.content.restrictions;

import io.bitsquare.arbitration.Arbitrator;
import io.bitsquare.arbitration.ArbitratorService;
import io.bitsquare.arbitration.Reputation;
import io.bitsquare.common.viewfx.model.Activatable;
import io.bitsquare.common.viewfx.model.DataModel;
import io.bitsquare.locale.Country;
import io.bitsquare.locale.CountryUtil;
import io.bitsquare.locale.LanguageUtil;
import io.bitsquare.locale.Region;
import io.bitsquare.persistence.Persistence;
import io.bitsquare.user.AccountSettings;
import io.bitsquare.user.User;
import io.bitsquare.util.DSAKeyUtil;

import org.bitcoinj.core.Coin;
import org.bitcoinj.core.ECKey;

import com.google.inject.Inject;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

class RestrictionsDataModel implements Activatable, DataModel {

    private final User user;
    private final AccountSettings accountSettings;
    private final Persistence persistence;
    private final ArbitratorService messageService;

    final ObservableList<Locale> languageList = FXCollections.observableArrayList();
    final ObservableList<Country> countryList = FXCollections.observableArrayList();
    final ObservableList<Arbitrator> arbitratorList = FXCollections.observableArrayList();
    final ObservableList<Locale> allLanguages = FXCollections.observableArrayList(LanguageUtil
            .getAllLanguageLocales());
    final ObservableList<Region> allRegions = FXCollections.observableArrayList(CountryUtil.getAllRegions());


    @Inject
    public RestrictionsDataModel(User user, AccountSettings accountSettings, Persistence persistence,
                                 ArbitratorService messageService) {
        this.user = user;
        this.accountSettings = accountSettings;
        this.persistence = persistence;
        this.messageService = messageService;

        AccountSettings persistedAccountSettings = (AccountSettings) persistence.read(accountSettings);
        if (persistedAccountSettings != null) {
            accountSettings.applyPersistedAccountSettings(persistedAccountSettings);
        }
        else {
            if (Locale.getDefault() != null) {
                addLanguage(LanguageUtil.getDefaultLanguageLocale());
                addCountry(CountryUtil.getDefaultCountry());
            }

            // Add english as default as well
            addLanguage(LanguageUtil.getEnglishLanguageLocale());
        }

        addMockArbitrator();
    }

    @Override
    public void activate() {
        languageList.setAll(accountSettings.getAcceptedLanguageLocales());
        countryList.setAll(accountSettings.getAcceptedCountries());
        arbitratorList.setAll(accountSettings.getAcceptedArbitrators());
    }

    @Override
    public void deactivate() {
        // no-op
    }

    ObservableList<Country> getAllCountriesFor(Region selectedRegion) {
        return FXCollections.observableArrayList(CountryUtil.getAllCountriesFor(selectedRegion));
    }

    void updateArbitratorList() {
        arbitratorList.setAll(accountSettings.getAcceptedArbitrators());
    }

    void addLanguage(Locale locale) {
        if (locale != null && !languageList.contains(locale)) {
            languageList.add(locale);
            accountSettings.addAcceptedLanguageLocale(locale);
        }
    }

    void removeLanguage(Locale locale) {
        languageList.remove(locale);
        accountSettings.removeAcceptedLanguageLocale(locale);
        saveSettings();
    }

    void addCountry(Country country) {
        if (!countryList.contains(country) && country != null) {
            countryList.add(country);
            accountSettings.addAcceptedCountry(country);
            saveSettings();
        }
    }

    ObservableList<Country> getListWithAllEuroCountries() {
        // TODO use Set instead of List
        // In addAcceptedCountry there is a check to no add duplicates, so it works correctly for now
        CountryUtil.getAllEuroCountries().stream().forEach(accountSettings::addAcceptedCountry);
        countryList.setAll(accountSettings.getAcceptedCountries());
        saveSettings();
        return countryList;
    }

    void removeCountry(Country country) {
        countryList.remove(country);
        accountSettings.removeAcceptedCountry(country);
        saveSettings();
    }

    void removeArbitrator(Arbitrator arbitrator) {
        arbitratorList.remove(arbitrator);
        accountSettings.removeAcceptedArbitrator(arbitrator);
        saveSettings();
    }


    private void saveSettings() {
        persistence.write(accountSettings);
    }

    // TODO Remove mock later
    private void addMockArbitrator() {
        if (accountSettings.getAcceptedArbitrators().isEmpty() && user.getMessageKeyPair() != null) {
            byte[] pubKey = new ECKey().getPubKey();
            String messagePubKeyAsHex = DSAKeyUtil.getHexStringFromPublicKey(user.getMessagePubKey());
            List<Locale> languages = new ArrayList<>();
            languages.add(LanguageUtil.getDefaultLanguageLocale());
            List<Arbitrator.METHOD> arbitrationMethods = new ArrayList<>();
            arbitrationMethods.add(Arbitrator.METHOD.TLS_NOTARY);
            List<Arbitrator.ID_VERIFICATION> idVerifications = new ArrayList<>();
            idVerifications.add(Arbitrator.ID_VERIFICATION.PASSPORT);
            idVerifications.add(Arbitrator.ID_VERIFICATION.GOV_ID);

            // TODO use very small sec. dposit to make testing in testnet less expensive
            // Revert later to 0.1 BTC again
            Arbitrator arbitrator = new Arbitrator(pubKey,
                    messagePubKeyAsHex,
                    "Manfred Karrer",
                    Arbitrator.ID_TYPE.REAL_LIFE_ID,
                    languages,
                    new Reputation(),
                    Coin.parseCoin("0.001"),
                    arbitrationMethods,
                    idVerifications,
                    "http://bitsquare.io/",
                    "Bla bla...");

            arbitratorList.add(arbitrator);
            accountSettings.addAcceptedArbitrator(arbitrator);
            persistence.write(accountSettings);

            messageService.addArbitrator(arbitrator);
        }
    }
}
