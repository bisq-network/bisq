package io.bitsquare.gui;

import io.bitsquare.gui.util.Icons;

public enum NavigationItem
{
    MAIN("MAIN", "/io/bitsquare/gui/MainView.fxml"),
    HOME("HOME", "/io/bitsquare/gui/home/HomeView.fxml", Icons.HOME, Icons.HOME_ACTIVE),
    BUY("BUY", "/io/bitsquare/gui/market/MarketView.fxml", Icons.NAV_BUY, Icons.NAV_BUY_ACTIVE),
    SELL("SELL", "/io/bitsquare/gui/market/MarketView.fxml", Icons.NAV_SELL, Icons.NAV_SELL_ACTIVE),
    ORDERS("ORDERS", "/io/bitsquare/gui/orders/OrdersView.fxml", Icons.ORDERS, Icons.ORDERS_ACTIVE),
    FUNDS("FUNDS", "/io/bitsquare/gui/funds/FundsView.fxml", Icons.FUNDS, Icons.FUNDS_ACTIVE),
    MSG("MSG", "/io/bitsquare/gui/msg/MsgView.fxml", Icons.MSG, Icons.MSG_ACTIVE),
    SETTINGS("SETTINGS", "/io/bitsquare/gui/settings/SettingsView.fxml", Icons.SETTINGS, Icons.SETTINGS_ACTIVE),

    ORDER_BOOK("ORDER_BOOK", "/io/bitsquare/gui/market/orderbook/OrderBookView.fxml"),
    TAKER_TRADE("TAKER_TRADE", "/io/bitsquare/gui/market/trade/TakerTradeView.fxml"),
    OFFERER_TRADE("OFFERER_TRADE", "/io/bitsquare/gui/orders/OffererTradeView.fxml"),
    CREATE_OFFER("CREATE_OFFER", "/io/bitsquare/gui/market/createOffer/CreateOfferView.fxml"),

    CLOSED_TRADE("CLOSED_TRADE", "/io/bitsquare/gui/orders/closed/ClosedTradeView.fxml"),
    OFFER("OFFER", "/io/bitsquare/gui/orders/offer/OfferView.fxml"),
    PENDING_TRADE("PENDING_TRADE", "/io/bitsquare/gui/orders/pending/PendingTradeView.fxml"),

    DEPOSIT("DEPOSIT", "/io/bitsquare/gui/funds/deposit/DepositView.fxml"),
    WITHDRAWAL("WITHDRAWAL", "/io/bitsquare/gui/funds/withdrawal/WithdrawalView.fxml"),
    TRANSACTIONS("TRANSACTIONS", "/io/bitsquare/gui/funds/transactions/TransactionsView.fxml"),

    ARBITRATOR_PROFILE("ARBITRATOR_PROFILE", "/io/bitsquare/gui/arbitrators/profile/ArbitratorProfileView.fxml"),
    ARBITRATOR_OVERVIEW("ARBITRATOR_OVERVIEW", "/io/bitsquare/gui/arbitrators/overview/ArbitratorOverviewView.fxml"),
    ARBITRATOR_REGISTRATION("ARBITRATOR_REGISTRATION", "/io/bitsquare/gui/arbitrators/registration/ArbitratorRegistrationView.fxml");

    private String fxmlUrl;
    private String id;
    private String icon;
    private String activeIcon;

    NavigationItem(String id, String fxmlUrl, String icon, String activeIcon)
    {
        this.id = id;
        this.fxmlUrl = fxmlUrl;
        this.icon = icon;
        this.activeIcon = activeIcon;
    }

    NavigationItem(String id, String fxmlUrl)
    {
        this.id = id;
        this.fxmlUrl = fxmlUrl;
    }

    public String getFxmlUrl()
    {
        return fxmlUrl;
    }

    public String getId()
    {
        return id;
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
