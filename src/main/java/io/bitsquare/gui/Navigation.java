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
import io.bitsquare.persistence.Persistence;

import com.google.inject.Inject;

import java.net.URL;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class Navigation {
    // New listeners can be added during iteration so we use CopyOnWriteArrayList to prevent invalid array
    // modification
    private final List<Listener> listeners = new CopyOnWriteArrayList<>();
    private final Persistence persistence;
    private Item[] currentItems;

    // Used for returning to the last important view
    // After setup is done we want to return to the last opened view (e.g. sell/buy)
    private Item[] itemsForReturning;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    public Navigation(Persistence persistence) {
        this.persistence = persistence;
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Public methods
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void navigationTo(Item... items) {
        List<Item> temp = new ArrayList<>();
        if (items != null) {
            for (int i = 0; i < items.length; i++) {
                Item item = items[i];
                temp.add(item);
                if (currentItems == null ||
                        (currentItems != null &&
                                currentItems.length > i &&
                                item != currentItems[i] &&
                                i != items.length - 1)) {
                    List<Item> temp2 = new ArrayList<>(temp);
                    for (int n = i + 1; n < items.length; n++) {
                        Item[] newTemp = new Item[i + 1];
                        currentItems = temp2.toArray(newTemp);
                        navigationTo(currentItems);
                        item = items[n];
                        temp2.add(item);
                    }
                }
            }

            currentItems = items;

            persistence.write(this, "navigationItems", items);
            listeners.stream().forEach((e) -> e.onNavigationRequested(items));
        }
    }

    public void navigateToLastStoredItem() {
        Item[] items = (Item[]) persistence.read(this, "navigationItems");
        // TODO we set BUY as default yet, should be HOME later
        if (items == null || items.length == 0)
            items = new Item[]{Item.MAIN, Item.BUY};

        navigationTo(items);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Listeners
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void addListener(Listener listener) {
        listeners.add(listener);
    }

    public void removeListener(Listener listener) {
        listeners.remove(listener);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Getters
    ///////////////////////////////////////////////////////////////////////////////////////////

    public Item[] getItemsForReturning() {
        return itemsForReturning;
    }

    public Item[] getCurrentItems() {
        return currentItems;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Setters
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void setItemsForReturning(Item[] itemsForReturning) {
        this.itemsForReturning = itemsForReturning;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Interface
    ///////////////////////////////////////////////////////////////////////////////////////////

    public static interface Listener {
        void onNavigationRequested(Item... items);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Enum
    ///////////////////////////////////////////////////////////////////////////////////////////

    public static enum Item {

        ///////////////////////////////////////////////////////////////////////////////////////////
        // Application
        ///////////////////////////////////////////////////////////////////////////////////////////

        MAIN("/io/bitsquare/gui/main/MainView.fxml"),


        ///////////////////////////////////////////////////////////////////////////////////////////
        // Main menu screens
        ///////////////////////////////////////////////////////////////////////////////////////////

        HOME("/io/bitsquare/gui/main/home/HomeView.fxml", "Overview"),
        BUY("/io/bitsquare/gui/main/trade/BuyView.fxml", "Buy BTC"),
        SELL("/io/bitsquare/gui/main/trade/SellView.fxml", "Sell BTC"),
        PORTFOLIO("/io/bitsquare/gui/main/portfolio/PortfolioView.fxml", "Portfolio"),
        FUNDS("/io/bitsquare/gui/main/funds/FundsView.fxml", "Funds"),
        MSG("/io/bitsquare/gui/main/msg/MsgView.fxml", "Messages"),
        SETTINGS("/io/bitsquare/gui/main/settings/SettingsView.fxml", "Settings"),
        ACCOUNT("/io/bitsquare/gui/main/account/AccountView.fxml", "Account"),


        ///////////////////////////////////////////////////////////////////////////////////////////
        // Sub  menus
        ///////////////////////////////////////////////////////////////////////////////////////////

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


        ///////////////////////////////////////////////////////////////////////////////////////////
        // Content in sub  menus
        ///////////////////////////////////////////////////////////////////////////////////////////

        // account content
        SEED_WORDS("/io/bitsquare/gui/main/account/content/seedwords/SeedWordsView.fxml"),
        ADD_PASSWORD("/io/bitsquare/gui/main/account/content/password/PasswordView.fxml"),
        CHANGE_PASSWORD("/io/bitsquare/gui/main/account/content/password/PasswordView.fxml"),
        RESTRICTIONS("/io/bitsquare/gui/main/account/content/restrictions/RestrictionsView.fxml"),
        REGISTRATION("/io/bitsquare/gui/main/account/content/registration/RegistrationView.fxml"),
        FIAT_ACCOUNT("/io/bitsquare/gui/main/account/content/irc/IrcAccountView.fxml"),


        ///////////////////////////////////////////////////////////////////////////////////////////
        // Popups
        ///////////////////////////////////////////////////////////////////////////////////////////

        // arbitrator

        ARBITRATOR_PROFILE("/io/bitsquare/gui/main/account/arbitrator/profile/ArbitratorProfileView.fxml"),
        ARBITRATOR_BROWSER("/io/bitsquare/gui/main/account/arbitrator/browser/ArbitratorBrowserView.fxml"),
        ARBITRATOR_REGISTRATION(
                "/io/bitsquare/gui/main/account/arbitrator/registration/ArbitratorRegistrationView.fxml"),

        // for testing, does not actually exist
        BOGUS("/io/bitsquare/BogusView.fxml");


        private final String displayName;
        private final String fxmlUrl;

        Item(String fxmlUrl) {
            this(fxmlUrl, "NONE");
        }

        Item(String fxmlUrl, String displayName) {
            this.displayName = displayName;
            this.fxmlUrl = fxmlUrl;
        }

        public URL getFxmlUrl() {
            URL url = Navigation.class.getResource(fxmlUrl);
            if (url == null)
                throw new BitsquareException("'%s' could not be loaded as a resource", fxmlUrl);
            return url;
        }

        public String getDisplayName() {
            return displayName;
        }

        public String getId() {
            return fxmlUrl.substring(fxmlUrl.lastIndexOf("/") + 1, fxmlUrl.lastIndexOf("View.fxml")).toLowerCase();
        }
    }
}
