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
    NavigationItem(String fxmlUrl, String icon, String activeIcon) {
        this.fxmlUrl = fxmlUrl;
        this.icon = icon;
        this.activeIcon = activeIcon;
    }

    NavigationItem(String fxmlUrl) {
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
