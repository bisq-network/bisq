/*
 * This file is part of Bisq.
 *
 * Bisq is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * Bisq is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Bisq. If not, see <http://www.gnu.org/licenses/>.
 */

package bisq.desktop.main;

import bisq.desktop.Navigation;
import bisq.desktop.common.view.CachingViewLoader;
import bisq.desktop.common.view.FxmlView;
import bisq.desktop.common.view.InitializableView;
import bisq.desktop.common.view.View;
import bisq.desktop.common.view.ViewLoader;
import bisq.desktop.components.AutoTooltipButton;
import bisq.desktop.components.AutoTooltipLabel;
import bisq.desktop.components.AutoTooltipToggleButton;
import bisq.desktop.components.BusyAnimation;
import bisq.desktop.main.account.AccountView;
import bisq.desktop.main.dao.DaoView;
import bisq.desktop.main.disputes.DisputesView;
import bisq.desktop.main.funds.FundsView;
import bisq.desktop.main.market.MarketView;
import bisq.desktop.main.offer.BuyOfferView;
import bisq.desktop.main.offer.SellOfferView;
import bisq.desktop.main.overlays.popups.Popup;
import bisq.desktop.main.portfolio.PortfolioView;
import bisq.desktop.main.settings.SettingsView;
import bisq.desktop.util.BSFormatter;
import bisq.desktop.util.GUIUtil;
import bisq.desktop.util.Transitions;

import bisq.core.app.BisqEnvironment;
import bisq.core.exceptions.BisqException;

import bisq.common.Timer;
import bisq.common.UserThread;
import bisq.common.app.DevEnv;
import bisq.common.app.Version;
import bisq.common.locale.Res;
import bisq.common.util.Tuple2;
import bisq.common.util.Utilities;

import javax.inject.Inject;

import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.Separator;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.control.Tooltip;
import javafx.scene.effect.DropShadow;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.TextAlignment;

import javafx.geometry.Insets;
import javafx.geometry.Pos;

import javafx.beans.value.ChangeListener;

import java.util.List;

import lombok.extern.slf4j.Slf4j;

import static javafx.scene.layout.AnchorPane.setBottomAnchor;
import static javafx.scene.layout.AnchorPane.setLeftAnchor;
import static javafx.scene.layout.AnchorPane.setRightAnchor;
import static javafx.scene.layout.AnchorPane.setTopAnchor;

@FxmlView
@Slf4j
public class MainView extends InitializableView<StackPane, MainViewModel> {
    // If after 30 sec we have not got connected we show "open network settings" button
    private final static int SHOW_TOR_SETTINGS_DELAY_SEC = 90;
    private Label versionLabel;

    public static StackPane getRootContainer() {
        return MainView.rootContainer;
    }

    @SuppressWarnings("PointlessBooleanExpression")
    public static void blur() {
        if (!DevEnv.STRESS_TEST_MODE)
            transitions.blur(MainView.rootContainer);
    }

    @SuppressWarnings("PointlessBooleanExpression")
    public static void blurLight() {
        if (!DevEnv.STRESS_TEST_MODE)
            transitions.blur(MainView.rootContainer, Transitions.DEFAULT_DURATION, -0.1, false, 5);
    }

    @SuppressWarnings("PointlessBooleanExpression")
    public static void blurUltraLight() {
        if (!DevEnv.STRESS_TEST_MODE)
            transitions.blur(MainView.rootContainer, Transitions.DEFAULT_DURATION, -0.1, false, 2);
    }

    @SuppressWarnings("PointlessBooleanExpression")
    public static void darken() {
        if (!DevEnv.STRESS_TEST_MODE)
            transitions.darken(MainView.rootContainer, Transitions.DEFAULT_DURATION, false);
    }

    public static void removeEffect() {
        transitions.removeEffect(MainView.rootContainer);
    }

    private static Transitions transitions;
    private static StackPane rootContainer;


    private final ViewLoader viewLoader;
    private final Navigation navigation;
    private final BSFormatter formatter;

    private final ToggleGroup navButtons = new ToggleGroup();
    private ChangeListener<String> walletServiceErrorMsgListener;
    private ChangeListener<String> btcSyncIconIdListener;
    private ChangeListener<String> splashP2PNetworkErrorMsgListener;
    private ChangeListener<String> splashP2PNetworkIconIdListener;
    private ChangeListener<Boolean> splashP2PNetworkVisibleListener;
    private BusyAnimation splashP2PNetworkBusyAnimation;
    private Label splashP2PNetworkLabel;
    private ProgressBar btcSyncIndicator;
    private Label btcSplashInfo;
    private List<String> persistedFilesCorrupted;
    private Popup<?> p2PNetworkWarnMsgPopup, btcNetworkWarnMsgPopup;

    @SuppressWarnings("WeakerAccess")
    @Inject
    public MainView(MainViewModel model,
                    CachingViewLoader viewLoader,
                    Navigation navigation,
                    Transitions transitions,
                    BSFormatter formatter) {
        super(model);
        this.viewLoader = viewLoader;
        this.navigation = navigation;
        this.formatter = formatter;
        MainView.transitions = transitions;
    }

    @Override
    protected void initialize() {
        MainView.rootContainer = root;

        ToggleButton marketButton = new NavButton(MarketView.class, Res.get("mainView.menu.market"));
        ToggleButton buyButton = new NavButton(BuyOfferView.class, Res.get("mainView.menu.buyBtc"));
        ToggleButton sellButton = new NavButton(SellOfferView.class, Res.get("mainView.menu.sellBtc"));
        ToggleButton portfolioButton = new NavButton(PortfolioView.class, Res.get("mainView.menu.portfolio"));
        ToggleButton fundsButton = new NavButton(FundsView.class, Res.get("mainView.menu.funds"));
        ToggleButton disputesButton = new NavButton(DisputesView.class, Res.get("mainView.menu.support"));
        ToggleButton settingsButton = new NavButton(SettingsView.class, Res.get("mainView.menu.settings"));
        ToggleButton accountButton = new NavButton(AccountView.class, Res.get("mainView.menu.account"));
        ToggleButton daoButton = new NavButton(DaoView.class, Res.get("mainView.menu.dao"));
        Pane portfolioButtonHolder = new Pane(portfolioButton);
        Pane disputesButtonHolder = new Pane(disputesButton);

        if (!BisqEnvironment.isDAOActivatedAndBaseCurrencySupportingBsq()) {
            daoButton.setVisible(false);
            daoButton.setManaged(false);
        }

        root.sceneProperty().addListener((observable, oldValue, newValue) -> {
            if (newValue != null) {
                newValue.addEventHandler(KeyEvent.KEY_RELEASED, keyEvent -> {
                    // TODO can be removed once DAO is released
                    if (Utilities.isAltOrCtrlPressed(KeyCode.D, keyEvent)) {
                        if (BisqEnvironment.getBaseCurrencyNetwork().isBitcoin()) {
                            daoButton.setVisible(true);
                            daoButton.setManaged(true);
                        }
                    } else if (Utilities.isAltOrCtrlPressed(KeyCode.DIGIT1, keyEvent)) {
                        marketButton.fire();
                    } else if (Utilities.isAltOrCtrlPressed(KeyCode.DIGIT2, keyEvent)) {
                        buyButton.fire();
                    } else if (Utilities.isAltOrCtrlPressed(KeyCode.DIGIT3, keyEvent)) {
                        sellButton.fire();
                    } else if (Utilities.isAltOrCtrlPressed(KeyCode.DIGIT4, keyEvent)) {
                        portfolioButton.fire();
                    } else if (Utilities.isAltOrCtrlPressed(KeyCode.DIGIT5, keyEvent)) {
                        fundsButton.fire();
                    } else if (Utilities.isAltOrCtrlPressed(KeyCode.DIGIT6, keyEvent)) {
                        disputesButton.fire();
                    } else if (Utilities.isAltOrCtrlPressed(KeyCode.DIGIT7, keyEvent)) {
                        settingsButton.fire();
                    } else if (Utilities.isAltOrCtrlPressed(KeyCode.DIGIT8, keyEvent)) {
                        accountButton.fire();
                    } else if (Utilities.isAltOrCtrlPressed(KeyCode.DIGIT9, keyEvent)) {
                        if (daoButton.isVisible())
                            daoButton.fire();
                    }
                });
            }
        });

        HBox leftNavPane = new HBox(marketButton, buyButton, sellButton, portfolioButtonHolder, fundsButton, disputesButtonHolder) {{
            setLeftAnchor(this, 10d);
            setTopAnchor(this, 0d);
        }};


        Tuple2<ComboBox<PriceFeedComboBoxItem>, VBox> marketPriceBox = getMarketPriceBox();
        ComboBox<PriceFeedComboBoxItem> priceComboBox = marketPriceBox.first;

        priceComboBox.getSelectionModel().selectedItemProperty().addListener((observable, oldValue, newValue) -> {
            model.setPriceFeedComboBoxItem(newValue);
        });
        ChangeListener<PriceFeedComboBoxItem> selectedPriceFeedItemListener = (observable, oldValue, newValue) -> {
            if (newValue != null)
                priceComboBox.getSelectionModel().select(newValue);

        };
        model.selectedPriceFeedComboBoxItemProperty.addListener(selectedPriceFeedItemListener);
        priceComboBox.setItems(model.priceFeedComboBoxItems);

        HBox.setMargin(marketPriceBox.second, new Insets(0, 0, 0, 0));


        Tuple2<TextField, VBox> availableBalanceBox = getBalanceBox(Res.get("mainView.balance.available"));
        availableBalanceBox.first.textProperty().bind(model.availableBalance);

        Tuple2<TextField, VBox> reservedBalanceBox = getBalanceBox(Res.get("mainView.balance.reserved"));
        reservedBalanceBox.first.textProperty().bind(model.reservedBalance);

        Tuple2<TextField, VBox> lockedBalanceBox = getBalanceBox(Res.get("mainView.balance.locked"));
        lockedBalanceBox.first.textProperty().bind(model.lockedBalance);

        HBox rightNavPane = new HBox(marketPriceBox.second, availableBalanceBox.second,
                reservedBalanceBox.second, lockedBalanceBox.second,
                settingsButton, accountButton, daoButton) {{
            setRightAnchor(this, 10d);
            setTopAnchor(this, 0d);
        }};

        root.widthProperty().addListener((observable, oldValue, newValue) -> {
            double w = (double) newValue;
            if (w > 0) {
                leftNavPane.setSpacing(w >= 1080 ? 12 : 6);
                rightNavPane.setSpacing(w >= 1080 ? 12 : 6);
            }
        });

        AnchorPane contentContainer = new AnchorPane() {{
            setId("content-pane");
            setLeftAnchor(this, 0d);
            setRightAnchor(this, 0d);
            setTopAnchor(this, 60d);
            setBottomAnchor(this, 10d);
        }};

        AnchorPane applicationContainer = new AnchorPane(leftNavPane, rightNavPane, contentContainer) {{
            setId("content-pane");
        }};

        BorderPane baseApplicationContainer = new BorderPane(applicationContainer) {{
            setId("base-content-container");
        }};
        baseApplicationContainer.setBottom(createFooter());

        setupNotificationIcon(portfolioButtonHolder);

        setupDisputesIcon(disputesButtonHolder);

        navigation.addListener(viewPath -> {
            if (viewPath.size() != 2 || viewPath.indexOf(MainView.class) != 0)
                return;

            Class<? extends View> viewClass = viewPath.tip();
            View view = viewLoader.load(viewClass);
            contentContainer.getChildren().setAll(view.getRoot());

            navButtons.getToggles().stream()
                    .filter(toggle -> toggle instanceof NavButton)
                    .filter(button -> viewClass == ((NavButton) button).viewClass)
                    .findFirst()
                    .orElseThrow(() -> new BisqException("No button matching %s found", viewClass))
                    .setSelected(true);
        });

        VBox splashScreen = createSplashScreen();

        root.getChildren().addAll(baseApplicationContainer, splashScreen);

        model.showAppScreen.addListener((ov, oldValue, newValue) -> {
            if (newValue) {
                navigation.navigateToPreviousVisitedView();

                if (!persistedFilesCorrupted.isEmpty()) {
                    if (persistedFilesCorrupted.size() > 1 || !persistedFilesCorrupted.get(0).equals("ViewPathAsString")) {
                        // show warning that some files has been corrupted
                        new Popup<>()
                                .warning(Res.get("popup.warning.incompatibleDB",
                                        persistedFilesCorrupted.toString(),
                                        model.getAppDateDir()))
                                .useShutDownButton()
                                .show();
                    } else {
                        log.debug("We detected incompatible data base file for Navigation. That is a minor issue happening with refactoring of UI classes " +
                                "and we don't display a warning popup to the user.");
                    }
                }

                transitions.fadeOutAndRemove(splashScreen, 1500, actionEvent -> disposeSplashScreen());
            }
        });

        // Delay a bit to give time for rendering the splash screen
        UserThread.execute(model::start);
    }

    private Tuple2<TextField, VBox> getBalanceBox(String text) {
        TextField textField = new TextField();
        textField.setEditable(false);
        textField.setPrefWidth(115); //140
        textField.setMouseTransparent(true);
        textField.setFocusTraversable(false);
        textField.getStyleClass().add("display-text-field");

        Label label = new AutoTooltipLabel(text);
        label.setId("nav-balance-label");
        label.setPadding(new Insets(0, 5, 0, 5));
        label.setPrefWidth(textField.getPrefWidth());
        VBox vBox = new VBox();
        vBox.setSpacing(3);
        vBox.setPadding(new Insets(11, 0, 0, 0));
        vBox.getChildren().addAll(textField, label);
        return new Tuple2<>(textField, vBox);
    }

    private ListCell<PriceFeedComboBoxItem> getPriceFeedComboBoxListCell() {
        return new ListCell<PriceFeedComboBoxItem>() {
            @Override
            protected void updateItem(PriceFeedComboBoxItem item, boolean empty) {
                super.updateItem(item, empty);

                if (!empty && item != null) {
                    textProperty().bind(item.displayStringProperty);
                } else {
                    textProperty().unbind();
                }
            }
        };
    }

    private Tuple2<ComboBox<PriceFeedComboBoxItem>, VBox> getMarketPriceBox() {
        ComboBox<PriceFeedComboBoxItem> priceComboBox = new ComboBox<>();
        priceComboBox.setVisibleRowCount(20);
        priceComboBox.setMaxWidth(220);
        priceComboBox.setMinWidth(220);
        priceComboBox.setFocusTraversable(false);
        priceComboBox.setId("price-feed-combo");
        priceComboBox.setCellFactory(p -> getPriceFeedComboBoxListCell());
        ListCell<PriceFeedComboBoxItem> buttonCell = getPriceFeedComboBoxListCell();
        buttonCell.setId("price-feed-combo");
        priceComboBox.setButtonCell(buttonCell);

        final ImageView btcAverageIcon = new ImageView();
        btcAverageIcon.setId("btcaverage");
        final Button btcAverageIconButton = new AutoTooltipButton("", btcAverageIcon);
        btcAverageIconButton.setPadding(new Insets(-1, 0, -1, 0));
        btcAverageIconButton.setFocusTraversable(false);
        btcAverageIconButton.getStyleClass().add("hidden-icon-button");
        HBox.setMargin(btcAverageIconButton, new Insets(0, 5, 0, 0));
        btcAverageIconButton.setOnAction(e -> GUIUtil.openWebPage("https://bitcoinaverage.com"));
        btcAverageIconButton.setVisible(model.isFiatCurrencyPriceFeedSelected.get());
        btcAverageIconButton.setManaged(btcAverageIconButton.isVisible());
        btcAverageIconButton.visibleProperty().bind(model.isFiatCurrencyPriceFeedSelected);
        btcAverageIconButton.managedProperty().bind(model.isFiatCurrencyPriceFeedSelected);
        btcAverageIconButton.setOnMouseEntered(e -> {
                    String res = Res.get("mainView.marketPrice.tooltip",
                            "https://bitcoinaverage.com",
                            "",
                            formatter.formatTime(model.priceFeedService.getLastRequestTimeStampBtcAverage()),
                            model.priceFeedService.getProviderNodeAddress());
                    btcAverageIconButton.setTooltip(
                            new Tooltip(res)
                    );
                }
        );

        final ImageView poloniexIcon = new ImageView();
        poloniexIcon.setId("poloniex");
        final Button poloniexIconButton = new AutoTooltipButton("", poloniexIcon);
        poloniexIconButton.setPadding(new Insets(-3, 0, -3, 0));
        poloniexIconButton.setFocusTraversable(false);
        poloniexIconButton.getStyleClass().add("hidden-icon-button");
        HBox.setMargin(poloniexIconButton, new Insets(2, 3, 0, 0));
        poloniexIconButton.setOnAction(e -> GUIUtil.openWebPage("https://poloniex.com"));
        poloniexIconButton.setVisible(model.isCryptoCurrencyPriceFeedSelected.get());
        poloniexIconButton.setManaged(poloniexIconButton.isVisible());
        poloniexIconButton.visibleProperty().bind(model.isCryptoCurrencyPriceFeedSelected);
        poloniexIconButton.managedProperty().bind(model.isCryptoCurrencyPriceFeedSelected);
        poloniexIconButton.setOnMouseEntered(e -> {
            String altcoinExtra = "\n" + Res.get("mainView.marketPrice.tooltip.altcoinExtra");
            String res = Res.get("mainView.marketPrice.tooltip",
                    "https://poloniex.com",
                    altcoinExtra,
                    formatter.formatTime(model.priceFeedService.getLastRequestTimeStampPoloniex()),
                    model.priceFeedService.getProviderNodeAddress());
            poloniexIconButton.setTooltip(
                    new Tooltip(res)
            );
        });

        Label label = new AutoTooltipLabel(Res.get("mainView.marketPrice.provider"));
        label.setId("nav-balance-label");
        label.setPadding(new Insets(0, 5, 0, 2));

        model.marketPriceUpdated.addListener((observable, oldValue, newValue) -> {
            updateMarketPriceLabel(label);
        });

        HBox hBox2 = new HBox();
        hBox2.getChildren().setAll(label, btcAverageIconButton, poloniexIconButton);

        VBox vBox = new VBox();
        vBox.setSpacing(3);
        vBox.setPadding(new Insets(11, 0, 0, 0));
        vBox.getChildren().addAll(priceComboBox, hBox2);
        return new Tuple2<>(priceComboBox, vBox);
    }

    private void updateMarketPriceLabel(Label label) {
        if (model.isPriceAvailable.get()) {
            if (model.isExternallyProvidedPrice.get()) {
                label.setText(Res.get("mainView.marketPrice.provider"));
                label.setTooltip(null);
            } else {
                label.setText(Res.get("mainView.marketPrice.bisqInternalPrice"));
                final Tooltip tooltip = new Tooltip(Res.get("mainView.marketPrice.tooltip.bisqInternalPrice"));
                tooltip.getStyleClass().add("market-price-tooltip");
                label.setTooltip(tooltip);
            }
        } else {
            label.setText("");
            label.setTooltip(null);
        }
    }

    public void setPersistedFilesCorrupted(List<String> persistedFilesCorrupted) {
        this.persistedFilesCorrupted = persistedFilesCorrupted;
    }

    private VBox createSplashScreen() {
        VBox vBox = new VBox();
        vBox.setAlignment(Pos.CENTER);
        vBox.setSpacing(0);
        vBox.setId("splash");

        ImageView logo = new ImageView();
        logo.setId("image-splash-logo");


        // createBitcoinInfoBox
        btcSplashInfo = new AutoTooltipLabel();
        btcSplashInfo.textProperty().bind(model.btcInfo);
        walletServiceErrorMsgListener = (ov, oldValue, newValue) -> {
            btcSplashInfo.setId("splash-error-state-msg");
            btcSplashInfo.getStyleClass().add("error-text");
        };
        model.walletServiceErrorMsg.addListener(walletServiceErrorMsgListener);

        btcSyncIndicator = new ProgressBar();
        btcSyncIndicator.setPrefWidth(120);
        btcSyncIndicator.progressProperty().bind(model.btcSyncProgress);

        ImageView btcSyncIcon = new ImageView();
        btcSyncIcon.setVisible(false);
        btcSyncIcon.setManaged(false);

        btcSyncIconIdListener = (ov, oldValue, newValue) -> {
            btcSyncIcon.setId(newValue);
            btcSyncIcon.setVisible(true);
            btcSyncIcon.setManaged(true);

            btcSyncIndicator.setVisible(false);
            btcSyncIndicator.setManaged(false);
        };
        model.btcSplashSyncIconId.addListener(btcSyncIconIdListener);


        HBox blockchainSyncBox = new HBox();
        blockchainSyncBox.setSpacing(10);
        blockchainSyncBox.setAlignment(Pos.CENTER);
        blockchainSyncBox.setPadding(new Insets(40, 0, 0, 0));
        blockchainSyncBox.setPrefHeight(50);
        blockchainSyncBox.getChildren().addAll(btcSplashInfo, btcSyncIndicator, btcSyncIcon);


        // create P2PNetworkBox
        splashP2PNetworkLabel = new AutoTooltipLabel();
        splashP2PNetworkLabel.setWrapText(true);
        splashP2PNetworkLabel.setMaxWidth(500);
        splashP2PNetworkLabel.setTextAlignment(TextAlignment.CENTER);
        splashP2PNetworkLabel.textProperty().bind(model.p2PNetworkInfo);

        splashP2PNetworkBusyAnimation = new BusyAnimation();

        splashP2PNetworkErrorMsgListener = (ov, oldValue, newValue) -> {
            if (newValue != null) {
                splashP2PNetworkLabel.setId("splash-error-state-msg");
                splashP2PNetworkLabel.getStyleClass().add("error-text");
                splashP2PNetworkBusyAnimation.stop();
            } else if (model.splashP2PNetworkAnimationVisible.get()) {
                splashP2PNetworkBusyAnimation.play();
            }
        };
        model.p2pNetworkWarnMsg.addListener(splashP2PNetworkErrorMsgListener);


        Button showTorNetworkSettingsButton = new AutoTooltipButton(Res.get("settings.net.openTorSettingsButton"));
        showTorNetworkSettingsButton.setVisible(false);
        showTorNetworkSettingsButton.setManaged(false);
        showTorNetworkSettingsButton.setOnAction(e -> {
            model.torNetworkSettingsWindow.show();
        });

        ImageView splashP2PNetworkIcon = new ImageView();
        splashP2PNetworkIcon.setId("image-connection-tor");
        splashP2PNetworkIcon.setVisible(false);
        splashP2PNetworkIcon.setManaged(false);
        HBox.setMargin(splashP2PNetworkIcon, new Insets(0, 0, 5, 0));

        Timer showTorNetworkSettingsTimer = UserThread.runAfter(() -> {
            showTorNetworkSettingsButton.setVisible(true);
            showTorNetworkSettingsButton.setManaged(true);
        }, SHOW_TOR_SETTINGS_DELAY_SEC);

        splashP2PNetworkIconIdListener = (ov, oldValue, newValue) -> {
            splashP2PNetworkIcon.setId(newValue);
            splashP2PNetworkIcon.setVisible(true);
            splashP2PNetworkIcon.setManaged(true);

            // if we can connect in 10 sec. we know that tor is working
            showTorNetworkSettingsTimer.stop();
        };
        model.p2PNetworkIconId.addListener(splashP2PNetworkIconIdListener);

        splashP2PNetworkVisibleListener = (ov, oldValue, newValue) -> splashP2PNetworkBusyAnimation.setIsRunning(newValue);
        model.splashP2PNetworkAnimationVisible.addListener(splashP2PNetworkVisibleListener);

        HBox splashP2PNetworkBox = new HBox();
        splashP2PNetworkBox.setSpacing(10);
        splashP2PNetworkBox.setAlignment(Pos.CENTER);
        splashP2PNetworkBox.setPrefHeight(50);
        splashP2PNetworkBox.getChildren().addAll(splashP2PNetworkLabel, splashP2PNetworkBusyAnimation, splashP2PNetworkIcon, showTorNetworkSettingsButton);

        vBox.getChildren().addAll(logo, blockchainSyncBox, splashP2PNetworkBox);
        return vBox;
    }

    private void disposeSplashScreen() {
        model.walletServiceErrorMsg.removeListener(walletServiceErrorMsgListener);
        model.btcSplashSyncIconId.removeListener(btcSyncIconIdListener);

        model.p2pNetworkWarnMsg.removeListener(splashP2PNetworkErrorMsgListener);
        model.p2PNetworkIconId.removeListener(splashP2PNetworkIconIdListener);
        model.splashP2PNetworkAnimationVisible.removeListener(splashP2PNetworkVisibleListener);

        btcSplashInfo.textProperty().unbind();
        btcSyncIndicator.progressProperty().unbind();

        splashP2PNetworkLabel.textProperty().unbind();

        model.onSplashScreenRemoved();
    }


    private AnchorPane createFooter() {
        // line
        Separator separator = new Separator();
        separator.setId("footer-pane-line");
        separator.setPrefHeight(1);
        setLeftAnchor(separator, 0d);
        setRightAnchor(separator, 0d);
        setTopAnchor(separator, 0d);

        // BTC
        Label btcInfoLabel = new AutoTooltipLabel();
        btcInfoLabel.setId("footer-pane");
        btcInfoLabel.textProperty().bind(model.btcInfo);

        ProgressBar blockchainSyncIndicator = new ProgressBar(-1);
        blockchainSyncIndicator.setPrefWidth(120);
        blockchainSyncIndicator.setMaxHeight(10);
        blockchainSyncIndicator.progressProperty().bind(model.btcSyncProgress);

        model.walletServiceErrorMsg.addListener((ov, oldValue, newValue) -> {
            if (newValue != null) {
                btcInfoLabel.setId("splash-error-state-msg");
                btcInfoLabel.getStyleClass().add("error-text");
                if (btcNetworkWarnMsgPopup == null) {
                    btcNetworkWarnMsgPopup = new Popup<>().warning(newValue);
                    btcNetworkWarnMsgPopup.show();
                }
            } else {
                btcInfoLabel.setId("footer-pane");
                if (btcNetworkWarnMsgPopup != null)
                    btcNetworkWarnMsgPopup.hide();
            }
        });

        model.btcSyncProgress.addListener((ov, oldValue, newValue) -> {
            if ((double) newValue >= 1) {
                blockchainSyncIndicator.setVisible(false);
                blockchainSyncIndicator.setManaged(false);
            }
        });

        HBox blockchainSyncBox = new HBox();
        blockchainSyncBox.setSpacing(10);
        blockchainSyncBox.setAlignment(Pos.CENTER);
        blockchainSyncBox.getChildren().addAll(btcInfoLabel, blockchainSyncIndicator);
        setLeftAnchor(blockchainSyncBox, 10d);
        setBottomAnchor(blockchainSyncBox, 7d);

        // version
        versionLabel = new AutoTooltipLabel();
        versionLabel.setId("footer-pane");
        versionLabel.setTextAlignment(TextAlignment.CENTER);
        versionLabel.setAlignment(Pos.BASELINE_CENTER);
        versionLabel.setText("v" + Version.VERSION);
        root.widthProperty().addListener((ov, oldValue, newValue) -> {
            versionLabel.setLayoutX(((double) newValue - versionLabel.getWidth()) / 2);
        });
        setBottomAnchor(versionLabel, 7d);
        model.newVersionAvailableProperty.addListener((observable, oldValue, newValue) -> {
            versionLabel.getStyleClass().removeAll("version-new", "version");
            if (newValue) {
                versionLabel.getStyleClass().add("version-new");
                versionLabel.setOnMouseClicked(e -> model.openDownloadWindow());
                versionLabel.setText("v" + Version.VERSION + " " + Res.get("mainView.version.update"));
            } else {
                versionLabel.getStyleClass().add("version");
                versionLabel.setOnMouseClicked(null);
                versionLabel.setText("v" + Version.VERSION);
            }
        });

        // P2P Network
        Label p2PNetworkLabel = new AutoTooltipLabel();
        p2PNetworkLabel.setId("footer-pane");
        setRightAnchor(p2PNetworkLabel, 33d);
        setBottomAnchor(p2PNetworkLabel, 7d);
        p2PNetworkLabel.textProperty().bind(model.p2PNetworkInfo);

        ImageView p2PNetworkIcon = new ImageView();
        setRightAnchor(p2PNetworkIcon, 10d);
        setBottomAnchor(p2PNetworkIcon, 7d);
        p2PNetworkIcon.setOpacity(0.4);
        p2PNetworkIcon.idProperty().bind(model.p2PNetworkIconId);
        p2PNetworkLabel.idProperty().bind(model.p2pNetworkLabelId);
        model.p2pNetworkWarnMsg.addListener((ov, oldValue, newValue) -> {
            if (newValue != null) {
                p2PNetworkWarnMsgPopup = new Popup<>().warning(newValue);
                p2PNetworkWarnMsgPopup.show();
            } else if (p2PNetworkWarnMsgPopup != null) {
                p2PNetworkWarnMsgPopup.hide();
            }
        });

        model.bootstrapComplete.addListener((observable, oldValue, newValue) -> {
            p2PNetworkIcon.setOpacity(1);
        });

        return new AnchorPane(separator, blockchainSyncBox, versionLabel, p2PNetworkLabel, p2PNetworkIcon) {{
            setId("footer-pane");
            setMinHeight(30);
            setMaxHeight(30);
        }};
    }

    private void setupNotificationIcon(Pane buttonHolder) {
        Label label = new AutoTooltipLabel();
        label.textProperty().bind(model.numPendingTradesAsString);
        label.relocate(5, 1);
        label.setId("nav-alert-label");

        ImageView icon = new ImageView();
        icon.setId("image-alert-round");

        Pane notification = new Pane();
        notification.relocate(30, 9);
        notification.setMouseTransparent(true);
        notification.setEffect(new DropShadow(4, 1, 2, Color.GREY));
        notification.getChildren().addAll(icon, label);
        notification.visibleProperty().bind(model.showPendingTradesNotification);
        buttonHolder.getChildren().add(notification);
    }

    private void setupDisputesIcon(Pane buttonHolder) {
        Label label = new AutoTooltipLabel();
        label.textProperty().bind(model.numOpenDisputesAsString);
        label.relocate(5, 1);
        label.setId("nav-alert-label");

        ImageView icon = new ImageView();
        icon.setId("image-alert-round");

        Pane notification = new Pane();
        notification.relocate(30, 9);
        notification.setMouseTransparent(true);
        notification.setEffect(new DropShadow(4, 1, 2, Color.GREY));
        notification.getChildren().addAll(icon, label);
        notification.visibleProperty().bind(model.showOpenDisputesNotification);
        buttonHolder.getChildren().add(notification);
    }

    private class NavButton extends AutoTooltipToggleButton {

        private final Class<? extends View> viewClass;

        public NavButton(Class<? extends View> viewClass, String title) {
            super(title, new ImageView() {{
                setId("image-nav-" + viewId(viewClass));
            }});

            this.viewClass = viewClass;

            this.setToggleGroup(navButtons);
            this.setId("nav-button");
            this.setPadding(new Insets(0, -10, -10, -10));
            this.setMinSize(50, 50);
            this.setMaxSize(50, 50);
            this.setContentDisplay(ContentDisplay.TOP);
            this.setGraphicTextGap(0);

            this.selectedProperty().addListener((ov, oldValue, newValue) -> {
                this.setMouseTransparent(newValue);
                this.setMinSize(50, 50);
                this.setMaxSize(50, 50);
                this.setGraphicTextGap(newValue ? -1 : 0);
                if (newValue) {
                    this.getGraphic().setId("image-nav-" + viewId(viewClass) + "-active");
                } else {
                    this.getGraphic().setId("image-nav-" + viewId(viewClass));
                }
            });

            //noinspection unchecked
            this.setOnAction(e -> navigation.navigateTo(MainView.class, viewClass));
        }

    }

    private static String viewId(Class<? extends View> viewClass) {
        String viewName = viewClass.getSimpleName();
        String suffix = "View";
        int suffixIdx = viewName.indexOf(suffix);
        if (suffixIdx != viewName.length() - suffix.length())
            throw new IllegalArgumentException("Cannot get ID for " + viewClass + ": class must end in " + suffix);
        return viewName.substring(0, suffixIdx).toLowerCase();
    }
}
