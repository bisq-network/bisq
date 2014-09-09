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

    // app
    MAIN("/io/bitsquare/gui/view/MainView.fxml"),

    // main menu screens
    HOME("/io/bitsquare/gui/home/HomeView.fxml", ImageUtil.HOME, ImageUtil.HOME_ACTIVE),
    ORDERS("/io/bitsquare/gui/orders/OrdersView.fxml", ImageUtil.ORDERS, ImageUtil.ORDERS_ACTIVE),
    FUNDS("/io/bitsquare/gui/funds/FundsView.fxml", ImageUtil.FUNDS, ImageUtil.FUNDS_ACTIVE),
    MSG("/io/bitsquare/gui/msg/MsgView.fxml", ImageUtil.MSG, ImageUtil.MSG_ACTIVE),
    SETTINGS("/io/bitsquare/gui/settings/SettingsView.fxml", ImageUtil.SETTINGS, ImageUtil.SETTINGS_ACTIVE),
    ACCOUNT("/io/bitsquare/gui/view/AccountView.fxml", ImageUtil.ACCOUNT, ImageUtil.ACCOUNT_ACTIVE),

    // trade
    ORDER_BOOK("/io/bitsquare/gui/trade/orderbook/OrderBookView.fxml"),
    BUY("/io/bitsquare/gui/trade/BuyView.fxml", ImageUtil.NAV_BUY, ImageUtil.NAV_BUY_ACTIVE),
    SELL("/io/bitsquare/gui/trade/SellView.fxml", ImageUtil.NAV_SELL, ImageUtil.NAV_SELL_ACTIVE),
    CREATE_OFFER("/io/bitsquare/gui/view/trade/CreateOfferView.fxml"),
    TAKE_OFFER("/io/bitsquare/gui/trade/takeoffer/TakeOfferView.fxml"),

    // orders
    OFFER("/io/bitsquare/gui/orders/offer/OfferView.fxml"),
    PENDING_TRADE("/io/bitsquare/gui/orders/pending/PendingTradeView.fxml"),
    CLOSED_TRADE("/io/bitsquare/gui/orders/closed/ClosedTradeView.fxml"),

    // funds
    DEPOSIT("/io/bitsquare/gui/funds/deposit/DepositView.fxml"),
    WITHDRAWAL("/io/bitsquare/gui/funds/withdrawal/WithdrawalView.fxml"),
    TRANSACTIONS("/io/bitsquare/gui/funds/transactions/TransactionsView.fxml"),

    // account
    ACCOUNT_SETUP("/io/bitsquare/gui/view/account/AccountSetupView.fxml"),
    ACCOUNT_SETTINGS("/io/bitsquare/gui/view/account/AccountSettingsView.fxml"),

    // account content
    SEED_WORDS("/io/bitsquare/gui/view/account/content/SeedWordsView.fxml"),
    ADD_PASSWORD("/io/bitsquare/gui/view/account/content/PasswordView.fxml"),
    CHANGE_PASSWORD("/io/bitsquare/gui/view/account/content/PasswordView.fxml"),
    RESTRICTIONS("/io/bitsquare/gui/view/account/content/RestrictionsView.fxml"),
    REGISTRATION("/io/bitsquare/gui/view/account/content/RegistrationView.fxml"),
    FIAT_ACCOUNT("/io/bitsquare/gui/view/account/content/FiatAccountView.fxml"),

    // arbitrator
    ARBITRATOR_PROFILE("/io/bitsquare/gui/arbitrators/profile/ArbitratorProfileView.fxml"),
    ARBITRATOR_BROWSER("/io/bitsquare/gui/arbitrators/browser/ArbitratorBrowserView.fxml"),
    ARBITRATOR_REGISTRATION("/io/bitsquare/gui/arbitrators/registration/ArbitratorRegistrationView.fxml");

    private final String fxmlUrl;
    private String icon;
    private String activeIcon;

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
