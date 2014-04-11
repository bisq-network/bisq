package io.bitsquare.gui;

public interface INavigationController
{

    public static final String HOME = "/io/bitsquare/gui/home/HomeView.fxml";
    public static final String TRADE = "/io/bitsquare/gui/trade/TradeView.fxml";
    public static final String ORDERS = "/io/bitsquare/gui/orders/OrdersView.fxml";
    public static final String FUNDS = "/io/bitsquare/gui/funds/FundsView.fxml";
    public static final String MSG = "/io/bitsquare/gui/msg/MsgView.fxml";
    public static final String HISTORY = "/io/bitsquare/gui/history/HistoryView.fxml";
    public static final String SETTINGS = "/io/bitsquare/gui/settings/SettingsView.fxml";

    public static final String TRADE__ORDER_BOOK = "/io/bitsquare/gui/trade/orderbook/OrderBookView.fxml";
    public static final String TRADE__PROCESS = "/io/bitsquare/gui/trade/tradeprocess/TradeProcessView.fxml";
    public static final String TRADE__CREATE_OFFER = "/io/bitsquare/gui/trade/offer/CreateOfferView.fxml";

    IChildController navigateToView(String fxmlView, String title);
}
