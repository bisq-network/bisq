package io.bitsquare.gui;

import io.bitsquare.gui.util.Icons;

public enum NavigationItem
{
    MAIN("/io/bitsquare/gui/MainView.fxml"),
    HOME("/io/bitsquare/gui/home/HomeView.fxml", Icons.HOME, Icons.HOME_ACTIVE),
    BUY("/io/bitsquare/gui/market/MarketView.fxml", Icons.NAV_BUY, Icons.NAV_BUY_ACTIVE),
    SELL("/io/bitsquare/gui/market/MarketView.fxml", Icons.NAV_SELL, Icons.NAV_SELL_ACTIVE),
    ORDERS("/io/bitsquare/gui/orders/OrdersView.fxml", Icons.ORDERS, Icons.ORDERS_ACTIVE),
    FUNDS("/io/bitsquare/gui/funds/FundsView.fxml", Icons.FUNDS, Icons.FUNDS_ACTIVE),
    MSG("/io/bitsquare/gui/msg/MsgView.fxml", Icons.MSG, Icons.MSG_ACTIVE),
    SETTINGS("/io/bitsquare/gui/settings/SettingsView.fxml", Icons.SETTINGS, Icons.SETTINGS_ACTIVE),

    ORDER_BOOK("/io/bitsquare/gui/market/orderbook/OrderBookView.fxml"),
    CREATE_OFFER("/io/bitsquare/gui/market/createOffer/CreateOfferView.fxml"),
    TAKER_TRADE("/io/bitsquare/gui/market/trade/TakerTradeView.fxml"),
    //OFFERER_TRADE("/io/bitsquare/gui/orders/OffererTradeView.fxml"),

    OFFER("/io/bitsquare/gui/orders/offer/OfferView.fxml"),
    PENDING_TRADE("/io/bitsquare/gui/orders/pending/PendingTradeView.fxml"),
    CLOSED_TRADE("/io/bitsquare/gui/orders/closed/ClosedTradeView.fxml"),

    DEPOSIT("/io/bitsquare/gui/funds/deposit/DepositView.fxml"),
    WITHDRAWAL("/io/bitsquare/gui/funds/withdrawal/WithdrawalView.fxml"),
    TRANSACTIONS("/io/bitsquare/gui/funds/transactions/TransactionsView.fxml"),

    ARBITRATOR_PROFILE("/io/bitsquare/gui/arbitrators/profile/ArbitratorProfileView.fxml"),
    ARBITRATOR_OVERVIEW("/io/bitsquare/gui/arbitrators/overview/ArbitratorOverviewView.fxml"),
    ARBITRATOR_REGISTRATION("/io/bitsquare/gui/arbitrators/registration/ArbitratorRegistrationView.fxml");

    private final String fxmlUrl;
    private String icon;
    private String activeIcon;

    NavigationItem(String fxmlUrl, String icon, String activeIcon)
    {
        this.fxmlUrl = fxmlUrl;
        this.icon = icon;
        this.activeIcon = activeIcon;
    }

    NavigationItem(String fxmlUrl)
    {
        this.fxmlUrl = fxmlUrl;
    }

    public String getFxmlUrl()
    {
        return fxmlUrl;
    }

    public String getIcon()
    {
        return icon;
    }

    public String getActiveIcon()
    {
        return activeIcon;
    }
}
