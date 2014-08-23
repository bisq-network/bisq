package io.bitsquare.gui;

import io.bitsquare.gui.util.ImageUtil;

public enum NavigationItem
{
    MAIN("/io/bitsquare/gui/MainView.fxml"),
    HOME("/io/bitsquare/gui/home/HomeView.fxml", ImageUtil.HOME, ImageUtil.HOME_ACTIVE),
    BUY("/io/bitsquare/gui/trade/BuyView.fxml", ImageUtil.NAV_BUY, ImageUtil.NAV_BUY_ACTIVE),
    SELL("/io/bitsquare/gui/trade/SellView.fxml", ImageUtil.NAV_SELL, ImageUtil.NAV_SELL_ACTIVE),
    ORDERS("/io/bitsquare/gui/orders/OrdersView.fxml", ImageUtil.ORDERS, ImageUtil.ORDERS_ACTIVE),
    FUNDS("/io/bitsquare/gui/funds/FundsView.fxml", ImageUtil.FUNDS, ImageUtil.FUNDS_ACTIVE),
    MSG("/io/bitsquare/gui/msg/MsgView.fxml", ImageUtil.MSG, ImageUtil.MSG_ACTIVE),
    SETTINGS("/io/bitsquare/gui/settings/SettingsView.fxml", ImageUtil.SETTINGS, ImageUtil.SETTINGS_ACTIVE),

    ORDER_BOOK("/io/bitsquare/gui/trade/orderbook/OrderBookView.fxml"),
    CREATE_OFFER("/io/bitsquare/gui/trade/createoffer/CreateOfferView.fxml"),
    TAKE_OFFER("/io/bitsquare/gui/trade/takeoffer/TakeOfferView.fxml"),
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
