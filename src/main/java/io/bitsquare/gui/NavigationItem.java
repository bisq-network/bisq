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

public enum NavigationItem {

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Application
    ///////////////////////////////////////////////////////////////////////////////////////////

    MAIN(0, "/io/bitsquare/gui/main/MainView.fxml"),


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Main menu screens
    ///////////////////////////////////////////////////////////////////////////////////////////

    HOME(1, "/io/bitsquare/gui/main/home/HomeView.fxml", ImageUtil.HOME, ImageUtil.HOME_ACTIVE),
    BUY(1, "/io/bitsquare/gui/main/trade/BuyView.fxml", ImageUtil.NAV_BUY, ImageUtil.NAV_BUY_ACTIVE),
    SELL(1, "/io/bitsquare/gui/main/trade/SellView.fxml", ImageUtil.NAV_SELL, ImageUtil.NAV_SELL_ACTIVE),
    ORDERS(1, "/io/bitsquare/gui/main/orders/OrdersView.fxml", ImageUtil.ORDERS, ImageUtil.ORDERS_ACTIVE),
    FUNDS(1, "/io/bitsquare/gui/main/funds/FundsView.fxml", ImageUtil.FUNDS, ImageUtil.FUNDS_ACTIVE),
    MSG(1, "/io/bitsquare/gui/main/msg/MsgView.fxml", ImageUtil.MSG, ImageUtil.MSG_ACTIVE),
    SETTINGS(1, "/io/bitsquare/gui/main/settings/SettingsView.fxml", ImageUtil.SETTINGS, ImageUtil.SETTINGS_ACTIVE),
    ACCOUNT(1, "/io/bitsquare/gui/main/account/AccountView.fxml", ImageUtil.ACCOUNT, ImageUtil.ACCOUNT_ACTIVE),


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Sub  menus
    ///////////////////////////////////////////////////////////////////////////////////////////

    // buy/sell (trade)
    ORDER_BOOK(2, "/io/bitsquare/gui/main/trade/orderbook/OrderBookView.fxml"),
    CREATE_OFFER(2, "/io/bitsquare/gui/main/trade/createoffer/CreateOfferView.fxml"),
    TAKE_OFFER(2, "/io/bitsquare/gui/main/trade/takeoffer/TakeOfferView.fxml"),

    // orders
    OFFER(2, "/io/bitsquare/gui/main/orders/offer/OfferView.fxml"),
    PENDING_TRADE(2, "/io/bitsquare/gui/main/orders/pending/PendingTradeView.fxml"),
    CLOSED_TRADE(2, "/io/bitsquare/gui/main/orders/closed/ClosedTradeView.fxml"),

    // funds
    DEPOSIT(2, "/io/bitsquare/gui/main/funds/deposit/DepositView.fxml"),
    WITHDRAWAL(2, "/io/bitsquare/gui/main/funds/withdrawal/WithdrawalView.fxml"),
    TRANSACTIONS(2, "/io/bitsquare/gui/main/funds/transactions/TransactionsView.fxml"),

    // account
    ACCOUNT_SETUP(2, "/io/bitsquare/gui/main/account/AccountSetupView.fxml"),
    ACCOUNT_SETTINGS(2, "/io/bitsquare/gui/main/account/AccountSettingsView.fxml"),


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Content in sub  menus
    ///////////////////////////////////////////////////////////////////////////////////////////

    // account content
    SEED_WORDS(3, "/io/bitsquare/gui/main/account/content/SeedWordsView.fxml"),
    ADD_PASSWORD(3, "/io/bitsquare/gui/main/account/content/PasswordView.fxml"),
    CHANGE_PASSWORD(3, "/io/bitsquare/gui/main/account/content/PasswordView.fxml"),
    RESTRICTIONS(3, "/io/bitsquare/gui/main/account/content/RestrictionsView.fxml"),
    REGISTRATION(3, "/io/bitsquare/gui/main/account/content/RegistrationView.fxml"),
    FIAT_ACCOUNT(3, "/io/bitsquare/gui/main/account/content/FiatAccountView.fxml"),


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Popups
    ///////////////////////////////////////////////////////////////////////////////////////////

    // arbitrator
    ARBITRATOR_PROFILE(2, "/io/bitsquare/gui/main/arbitrators/profile/ArbitratorProfileView.fxml"),
    ARBITRATOR_BROWSER(-1, "/io/bitsquare/gui/main/arbitrators/browser/ArbitratorBrowserView.fxml"),
    ARBITRATOR_REGISTRATION(-1, "/io/bitsquare/gui/main/arbitrators/registration/ArbitratorRegistrationView.fxml");


    private int level;
    private final String fxmlUrl;
    private String icon;
    private String activeIcon;

    /**
     * @param level      The navigation hierarchy depth. 0 is main app level, 1 is main menu items, 2 is sub-menus,
     *                   3 content in sub-menus, -1 is popup window
     * @param fxmlUrl
     * @param icon
     * @param activeIcon
     */
    NavigationItem(int level, String fxmlUrl, String icon, String activeIcon) {
        this.level = level;
        this.fxmlUrl = fxmlUrl;
        this.icon = icon;
        this.activeIcon = activeIcon;
    }

    NavigationItem(int level, String fxmlUrl) {
        this.level = level;
        this.fxmlUrl = fxmlUrl;
    }

    public int getLevel() {
        return level;
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
