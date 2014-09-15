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

import io.bitsquare.gui.util.ImageUtil;
import io.bitsquare.persistence.Persistence;

import com.google.inject.Inject;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Navigation {
    private static final Logger log = LoggerFactory.getLogger(Navigation.class);


    // New listeners can be added during iteration so we use CopyOnWriteArrayList to prevent invalid array 
    // modification 
    private List<Listener> listeners = new CopyOnWriteArrayList<>();
    private Persistence persistence;
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
        log.trace("navigationTo " + Arrays.asList(items).toString());
        List<Item> temp = new ArrayList<>();
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
        log.trace("navigationTo notify listeners " + Arrays.asList(items).toString() + " / " + listeners
                .size());
        listeners.stream().forEach((e) -> e.onNavigationRequested(items));
    }

    public void navigateToLastStoredItem() {
        Item[] items = (Item[]) persistence.read(this, "navigationItems");
        if (items == null || items.length == 0)
            items = new Item[]{Item.MAIN, Item.HOME};

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

        HOME("/io/bitsquare/gui/main/home/HomeView.fxml", ImageUtil.HOME, ImageUtil.HOME_ACTIVE),
        BUY("/io/bitsquare/gui/main/trade/BuyView.fxml", ImageUtil.BUY, ImageUtil.BUY_ACTIVE),
        SELL("/io/bitsquare/gui/main/trade/SellView.fxml", ImageUtil.SELL, ImageUtil.SELL_ACTIVE),
        ORDERS("/io/bitsquare/gui/main/orders/OrdersView.fxml", ImageUtil.ORDERS, ImageUtil.ORDERS_ACTIVE),
        FUNDS("/io/bitsquare/gui/main/funds/FundsView.fxml", ImageUtil.FUNDS, ImageUtil.FUNDS_ACTIVE),
        MSG("/io/bitsquare/gui/main/msg/MsgView.fxml", ImageUtil.MSG, ImageUtil.MSG_ACTIVE),
        SETTINGS("/io/bitsquare/gui/main/settings/SettingsView.fxml", ImageUtil.SETTINGS, ImageUtil.SETTINGS_ACTIVE),
        ACCOUNT("/io/bitsquare/gui/main/account/AccountView.fxml", ImageUtil.ACCOUNT, ImageUtil.ACCOUNT_ACTIVE),


        ///////////////////////////////////////////////////////////////////////////////////////////
        // Sub  menus
        ///////////////////////////////////////////////////////////////////////////////////////////

        // buy/sell (trade)
        ORDER_BOOK("/io/bitsquare/gui/main/trade/orderbook/OrderBookView.fxml"),
        CREATE_OFFER("/io/bitsquare/gui/main/trade/createoffer/CreateOfferView.fxml"),
        TAKE_OFFER("/io/bitsquare/gui/main/trade/takeoffer/TakeOfferView.fxml"),

        // orders
        OFFER("/io/bitsquare/gui/main/orders/offer/OfferView.fxml"),
        PENDING_TRADE("/io/bitsquare/gui/main/orders/pending/PendingTradeView.fxml"),
        CLOSED_TRADE("/io/bitsquare/gui/main/orders/closed/ClosedTradeView.fxml"),

        // funds
        DEPOSIT("/io/bitsquare/gui/main/funds/deposit/DepositView.fxml"),
        WITHDRAWAL("/io/bitsquare/gui/main/funds/withdrawal/WithdrawalView.fxml"),
        TRANSACTIONS("/io/bitsquare/gui/main/funds/transactions/TransactionsView.fxml"),

        // account
        ACCOUNT_SETUP("/io/bitsquare/gui/main/account/setup/AccountSetupView.fxml"),
        ACCOUNT_SETTINGS("/io/bitsquare/gui/main/account/settings/AccountSettingsView.fxml"),


        ///////////////////////////////////////////////////////////////////////////////////////////
        // Content in sub  menus
        ///////////////////////////////////////////////////////////////////////////////////////////

        // account content
        SEED_WORDS("/io/bitsquare/gui/main/account/content/seedwords/SeedWordsView.fxml"),
        ADD_PASSWORD("/io/bitsquare/gui/main/account/content/password/PasswordView.fxml"),
        CHANGE_PASSWORD("/io/bitsquare/gui/main/account/content/password/PasswordView.fxml"),
        RESTRICTIONS("/io/bitsquare/gui/main/account/content/restrictions/RestrictionsView.fxml"),
        REGISTRATION("/io/bitsquare/gui/main/account/content/registration/RegistrationView.fxml"),
        FIAT_ACCOUNT("/io/bitsquare/gui/main/account/content/fiat/FiatAccountView.fxml"),


        ///////////////////////////////////////////////////////////////////////////////////////////
        // Popups
        ///////////////////////////////////////////////////////////////////////////////////////////

        // arbitrator
        ARBITRATOR_PROFILE("/io/bitsquare/gui/main/arbitrators/profile/ArbitratorProfileView.fxml"),
        ARBITRATOR_BROWSER("/io/bitsquare/gui/main/arbitrators/browser/ArbitratorBrowserView.fxml"),
        ARBITRATOR_REGISTRATION("/io/bitsquare/gui/main/arbitrators/registration/ArbitratorRegistrationView.fxml");


        private final String fxmlUrl;
        private String icon;
        private String activeIcon;

        /**
         * @param fxmlUrl
         * @param icon
         * @param activeIcon
         */
        Item(String fxmlUrl, String icon, String activeIcon) {
            this.fxmlUrl = fxmlUrl;
            this.icon = icon;
            this.activeIcon = activeIcon;
        }

        Item(String fxmlUrl) {
            this.fxmlUrl = fxmlUrl;
        }

        public String getFxmlUrl() {
            return fxmlUrl;
        }

        public String getIcon() {
            return icon;
        }

        public String getActiveIcon() {
            return activeIcon;
        }
    }
}
