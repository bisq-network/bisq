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

package io.bitsquare.gui;

import io.bitsquare.BitsquareException;

import java.net.URL;

public enum FxmlView {

    // ~ Application ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

    MAIN("/io/bitsquare/gui/main/MainView.fxml"),


    // ~ Main menu ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

    HOME("/io/bitsquare/gui/main/home/HomeView.fxml", "Overview"),
    BUY("/io/bitsquare/gui/main/trade/BuyView.fxml", "Buy BTC"),
    SELL("/io/bitsquare/gui/main/trade/SellView.fxml", "Sell BTC"),
    PORTFOLIO("/io/bitsquare/gui/main/portfolio/PortfolioView.fxml", "Portfolio"),
    FUNDS("/io/bitsquare/gui/main/funds/FundsView.fxml", "Funds"),
    MSG("/io/bitsquare/gui/main/msg/MsgView.fxml", "Messages"),
    SETTINGS("/io/bitsquare/gui/main/settings/SettingsView.fxml", "Settings"),
    ACCOUNT("/io/bitsquare/gui/main/account/AccountView.fxml", "Account"),


    // ~ Sub menus ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

    // buy/sell (trade)
    OFFER_BOOK("/io/bitsquare/gui/main/trade/offerbook/OfferBookView.fxml"),
    CREATE_OFFER("/io/bitsquare/gui/main/trade/createoffer/CreateOfferView.fxml"),
    TAKE_OFFER("/io/bitsquare/gui/main/trade/takeoffer/TakeOfferView.fxml"),

    // portfolio
    OFFERS("/io/bitsquare/gui/main/portfolio/offer/OffersView.fxml"),
    PENDING_TRADES("/io/bitsquare/gui/main/portfolio/pending/PendingTradesView.fxml"),
    CLOSED_TRADES("/io/bitsquare/gui/main/portfolio/closed/ClosedTradesView.fxml"),

    // funds
    WITHDRAWAL("/io/bitsquare/gui/main/funds/withdrawal/WithdrawalView.fxml"),
    TRANSACTIONS("/io/bitsquare/gui/main/funds/transactions/TransactionsView.fxml"),

    // settings
    PREFERENCES("/io/bitsquare/gui/main/settings/application/PreferencesView.fxml"),
    NETWORK_SETTINGS("/io/bitsquare/gui/main/settings/network/NetworkSettingsView.fxml"),

    // account
    ACCOUNT_SETUP("/io/bitsquare/gui/main/account/setup/AccountSetupWizard.fxml"),
    ACCOUNT_SETTINGS("/io/bitsquare/gui/main/account/settings/AccountSettingsView.fxml"),
    ARBITRATOR_SETTINGS("/io/bitsquare/gui/main/account/arbitrator/ArbitratorSettingsView.fxml"),


    // ~ Content in sub menus ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

    // account content
    SEED_WORDS("/io/bitsquare/gui/main/account/content/seedwords/SeedWordsView.fxml"),
    ADD_PASSWORD("/io/bitsquare/gui/main/account/content/password/PasswordView.fxml"),
    CHANGE_PASSWORD("/io/bitsquare/gui/main/account/content/password/PasswordView.fxml"),
    RESTRICTIONS("/io/bitsquare/gui/main/account/content/restrictions/RestrictionsView.fxml"),
    REGISTRATION("/io/bitsquare/gui/main/account/content/registration/RegistrationView.fxml"),
    FIAT_ACCOUNT("/io/bitsquare/gui/main/account/content/irc/IrcAccountView.fxml"),


    // ~ Popups ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

    // arbitration
    ARBITRATOR_PROFILE("/io/bitsquare/gui/main/account/arbitrator/profile/ArbitratorProfileView.fxml"),
    ARBITRATOR_BROWSER("/io/bitsquare/gui/main/account/arbitrator/browser/ArbitratorBrowserView.fxml"),
    ARBITRATOR_REGISTRATION(
            "/io/bitsquare/gui/main/account/arbitrator/registration/ArbitratorRegistrationView.fxml"),


    // ~ Testing ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

    BOGUS("/io/bitsquare/BogusView.fxml");


    private final String location;
    private final String displayName;

    FxmlView(String location) {
        this(location, "NONE");
    }

    FxmlView(String location, String displayName) {
        this.location = location;
        this.displayName = displayName;
    }

    public URL getLocation() {
        URL url = Navigation.class.getResource(location);
        if (url == null)
            throw new BitsquareException("'%s' could not be loaded as a resource", location);
        return url;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getId() {
        return location.substring(location.lastIndexOf("/") + 1, location.lastIndexOf("View.fxml")).toLowerCase();
    }
}
