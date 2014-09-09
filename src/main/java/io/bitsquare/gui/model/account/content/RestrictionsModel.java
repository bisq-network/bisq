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

package io.bitsquare.gui.model.account.content;

import io.bitsquare.arbitrator.Arbitrator;
import io.bitsquare.arbitrator.Reputation;
import io.bitsquare.gui.UIModel;
import io.bitsquare.locale.Country;
import io.bitsquare.locale.CountryUtil;
import io.bitsquare.locale.LanguageUtil;
import io.bitsquare.locale.Region;
import io.bitsquare.msg.MessageFacade;
import io.bitsquare.persistence.Persistence;
import io.bitsquare.settings.Settings;
import io.bitsquare.user.User;
import io.bitsquare.util.DSAKeyUtil;

import com.google.bitcoin.core.ECKey;
import com.google.bitcoin.core.Utils;

import com.google.inject.Inject;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RestrictionsModel extends UIModel {
    private static final Logger log = LoggerFactory.getLogger(RestrictionsModel.class);

    private final User user;
    private final Settings settings;
    private final Persistence persistence;
    private final MessageFacade messageFacade;

    public final ObservableList<Locale> languageList = FXCollections.observableArrayList();
    public final ObservableList<Country> countryList = FXCollections.observableArrayList();
    public final ObservableList<Arbitrator> arbitratorList = FXCollections.observableArrayList();
    public final ObservableList<Locale> allLanguages = FXCollections.observableArrayList(LanguageUtil
            .getAllLanguageLocales());
    public final ObservableList<Region> allRegions = FXCollections.observableArrayList(CountryUtil.getAllRegions());


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    private RestrictionsModel(User user, Settings settings, Persistence persistence, MessageFacade messageFacade) {
        this.user = user;
        this.settings = settings;
        this.persistence = persistence;
        this.messageFacade = messageFacade;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Lifecycle
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void initialized() {
        super.initialized();

        Settings persistedSettings = (Settings) persistence.read(settings);
        if (persistedSettings != null) {
            settings.applyPersistedSettings(persistedSettings);
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
        super.activate();
        languageList.setAll(settings.getAcceptedLanguageLocales());
        countryList.setAll(settings.getAcceptedCountries());
        arbitratorList.setAll(settings.getAcceptedArbitrators());
    }

    @SuppressWarnings("EmptyMethod")
    @Override
    public void deactivate() {
        super.deactivate();
    }


    @SuppressWarnings("EmptyMethod")
    @Override
    public void terminate() {
        super.terminate();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Public
    ///////////////////////////////////////////////////////////////////////////////////////////

    public ObservableList<Country> getAllCountriesFor(Region selectedRegion) {
        return FXCollections.observableArrayList(CountryUtil.getAllCountriesFor(selectedRegion));
    }

    public void updateArbitratorList() {
        arbitratorList.setAll(settings.getAcceptedArbitrators());
    }

    public void addLanguage(Locale locale) {
        if (locale != null && !languageList.contains(locale)) {
            languageList.add(locale);
            settings.addAcceptedLanguageLocale(locale);
        }
    }

    public void removeLanguage(Locale locale) {
        languageList.remove(locale);
        settings.removeAcceptedLanguageLocale(locale);
        saveSettings();
    }

    public void addCountry(Country country) {
        if (!countryList.contains(country) && country != null) {
            countryList.add(country);
            settings.addAcceptedCountry(country);
            saveSettings();
        }
    }

    public ObservableList<Country> getListWithAllEuroCountries() {
        // TODO use Set instead of List
        // In addAcceptedCountry there is a check to no add duplicates, so it works correctly for now
        CountryUtil.getAllEuroCountries().stream().forEach(settings::addAcceptedCountry);
        countryList.setAll(settings.getAcceptedCountries());
        saveSettings();
        return countryList;
    }

    public void removeCountry(Country country) {
        countryList.remove(country);
        settings.removeAcceptedCountry(country);
        saveSettings();
    }

    public void removeArbitrator(Arbitrator arbitrator) {
        arbitratorList.remove(arbitrator);
        settings.removeAcceptedArbitrator(arbitrator);
        saveSettings();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Private 
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void saveSettings() {
        persistence.write(settings);
    }

    // TODO Remove mock later
    private void addMockArbitrator() {
        if (settings.getAcceptedArbitrators().isEmpty() && user.getMessageKeyPair() != null) {
            String pubKeyAsHex = Utils.HEX.encode(new ECKey().getPubKey());
            String messagePubKeyAsHex = DSAKeyUtil.getHexStringFromPublicKey(user.getMessagePublicKey());
            List<Locale> languages = new ArrayList<>();
            languages.add(LanguageUtil.getDefaultLanguageLocale());
            List<Arbitrator.METHOD> arbitrationMethods = new ArrayList<>();
            arbitrationMethods.add(Arbitrator.METHOD.TLS_NOTARY);
            List<Arbitrator.ID_VERIFICATION> idVerifications = new ArrayList<>();
            idVerifications.add(Arbitrator.ID_VERIFICATION.PASSPORT);
            idVerifications.add(Arbitrator.ID_VERIFICATION.GOV_ID);

            Arbitrator arbitrator = new Arbitrator(pubKeyAsHex,
                    messagePubKeyAsHex,
                    "Manfred Karrer",
                    Arbitrator.ID_TYPE.REAL_LIFE_ID,
                    languages,
                    new Reputation(),
                    1,
                    0.01,
                    0.001,
                    10,
                    0.1,
                    arbitrationMethods,
                    idVerifications,
                    "http://bitsquare.io/",
                    "Bla bla...");

            arbitratorList.add(arbitrator);
            settings.addAcceptedArbitrator(arbitrator);
            persistence.write(settings);

            messageFacade.addArbitrator(arbitrator);
        }
    }
}
