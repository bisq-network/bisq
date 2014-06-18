package io.bitsquare.gui;

public interface NavigationController
{
    public static final String SETUP = "/io/bitsquare/gui/setup/SetupView.fxml";

    public static final String HOME = "/io/bitsquare/gui/home/HomeView.fxml";
    public static final String MARKET = "/io/bitsquare/gui/market/MarketView.fxml";
    public static final String ORDERS = "/io/bitsquare/gui/orders/OrdersView.fxml";
    public static final String FUNDS = "/io/bitsquare/gui/funds/FundsView.fxml";
    public static final String MSG = "/io/bitsquare/gui/msg/MsgView.fxml";
    public static final String HISTORY = "/io/bitsquare/gui/history/HistoryView.fxml";
    public static final String SETTINGS = "/io/bitsquare/gui/settings/SettingsView.fxml";

    public static final String ORDER_BOOK = "/io/bitsquare/gui/market/orderbook/OrderBookView.fxml";
    public static final String TAKER_TRADE = "/io/bitsquare/gui/market/trade/TakerTradeView.fxml";
    public static final String OFFERER_TRADE = "/io/bitsquare/gui/orders/OffererTradeView.fxml";
    public static final String CREATE_OFFER = "/io/bitsquare/gui/market/createOffer/CreateOfferView.fxml";

    public static final String ARBITRATOR_PROFILE = "/io/bitsquare/gui/arbitrators/profile/ArbitratorProfileView.fxml";
    public static final String ARBITRATOR_OVERVIEW = "/io/bitsquare/gui/arbitrators/overview/ArbitratorOverviewView.fxml";
    public static final String ARBITRATOR_REGISTRATION = "/io/bitsquare/gui/arbitrators/registration/ArbitratorRegistrationView.fxml";

    ChildController navigateToView(String fxmlView);

    ChildController navigateToView(String fxmlView, String title);

}
