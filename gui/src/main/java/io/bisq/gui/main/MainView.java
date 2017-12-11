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

package io.bisq.gui.main;

import io.bisq.common.Timer;
import io.bisq.common.UserThread;
import io.bisq.common.app.DevEnv;
import io.bisq.common.app.Version;
import io.bisq.common.locale.Res;
import io.bisq.common.util.Tuple2;
import io.bisq.common.util.Utilities;
import io.bisq.core.app.BisqEnvironment;
import io.bisq.core.exceptions.BisqException;
import io.bisq.gui.Navigation;
import io.bisq.gui.common.view.*;
import io.bisq.gui.components.BusyAnimation;
import io.bisq.gui.main.account.AccountView;
import io.bisq.gui.main.dao.DaoView;
import io.bisq.gui.main.disputes.DisputesView;
import io.bisq.gui.main.funds.FundsView;
import io.bisq.gui.main.market.MarketView;
import io.bisq.gui.main.offer.BuyOfferView;
import io.bisq.gui.main.offer.SellOfferView;
import io.bisq.gui.main.overlays.popups.Popup;
import io.bisq.gui.main.overlays.windows.TorNetworkSettingsWindow;
import io.bisq.gui.main.portfolio.PortfolioView;
import io.bisq.gui.main.settings.SettingsView;
import io.bisq.gui.util.BSFormatter;
import io.bisq.gui.util.GUIUtil;
import io.bisq.gui.util.Transitions;
import javafx.beans.value.ChangeListener;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.effect.DropShadow;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.text.TextAlignment;
import lombok.extern.slf4j.Slf4j;

import javax.inject.Inject;
import java.util.List;

import static javafx.scene.layout.AnchorPane.*;

@FxmlView
@Slf4j
public class MainView extends InitializableView<StackPane, MainViewModel> {
    // If after 30 sec we have not got connected we show "open network settings" button
    private final static int SHOW_TOR_SETTINGS_DELAY_SEC = 30;
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

    private final ToggleGroup navButtons = new ToggleGroup();

    private final ViewLoader viewLoader;
    private final Navigation navigation;
    private static Transitions transitions;
    private final BSFormatter formatter;
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
    private static StackPane rootContainer;

    @SuppressWarnings("WeakerAccess")
    @Inject
    public MainView(MainViewModel model, CachingViewLoader viewLoader, Navigation navigation, Transitions transitions,
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

        // TODO can be removed once DAO is released
        if (BisqEnvironment.getBaseCurrencyNetwork().isBitcoin()) {
            UserThread.runAfter(() -> {
                root.getScene().addEventHandler(KeyEvent.KEY_RELEASED, keyEvent -> {
                    if (Utilities.isAltOrCtrlPressed(KeyCode.D, keyEvent)) {
                        daoButton.setVisible(true);
                        daoButton.setManaged(true);
                    }
                });
            }, 1);
        }

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
                leftNavPane.setSpacing(w >= 1080 ? 10 : 5);
                rightNavPane.setSpacing(w >= 1080 ? 10 : 5);
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
        textField.setStyle("-fx-alignment: center;  -fx-background-color: white;");

        Label label = new Label(text);
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
        final Button btcAverageIconButton = new Button("", btcAverageIcon);
        btcAverageIconButton.setPadding(new Insets(-1, 0, -1, 0));
        btcAverageIconButton.setFocusTraversable(false);
        btcAverageIconButton.setStyle("-fx-background-color: transparent;");
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
        final Button poloniexIconButton = new Button("", poloniexIcon);
        poloniexIconButton.setPadding(new Insets(-3, 0, -3, 0));
        poloniexIconButton.setFocusTraversable(false);
        poloniexIconButton.setStyle("-fx-background-color: transparent;");
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

        Label label = new Label(Res.get("mainView.marketPrice.provider"));
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
                tooltip.setStyle("-fx-font-size: 12");
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
        btcSplashInfo = new Label();
        btcSplashInfo.textProperty().bind(model.btcInfo);
        walletServiceErrorMsgListener = (ov, oldValue, newValue) -> btcSplashInfo.setId("splash-error-state-msg");
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
        splashP2PNetworkLabel = new Label();
        splashP2PNetworkLabel.setWrapText(true);
        splashP2PNetworkLabel.setMaxWidth(500);
        splashP2PNetworkLabel.setTextAlignment(TextAlignment.CENTER);
        splashP2PNetworkLabel.textProperty().bind(model.p2PNetworkInfo);

        splashP2PNetworkBusyAnimation = new BusyAnimation();

        splashP2PNetworkErrorMsgListener = (ov, oldValue, newValue) -> {
            if (newValue != null) {
                splashP2PNetworkLabel.setId("splash-error-state-msg");
                splashP2PNetworkBusyAnimation.stop();
            } else if (model.splashP2PNetworkAnimationVisible.get()) {
                splashP2PNetworkBusyAnimation.play();
            }
        };
        model.p2pNetworkWarnMsg.addListener(splashP2PNetworkErrorMsgListener);


        Button showTorNetworkSettingsButton = new Button(Res.get("settings.net.openTorSettingsButton"));
        showTorNetworkSettingsButton.setVisible(false);
        showTorNetworkSettingsButton.setManaged(false);
        showTorNetworkSettingsButton.setOnAction(e -> {
            new TorNetworkSettingsWindow(model.preferences).show();
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
        Label btcInfoLabel = new Label();
        btcInfoLabel.setId("footer-pane");
        btcInfoLabel.textProperty().bind(model.btcInfo);

        ProgressBar blockchainSyncIndicator = new ProgressBar(-1);
        blockchainSyncIndicator.setPrefWidth(120);
        blockchainSyncIndicator.setMaxHeight(10);
        blockchainSyncIndicator.progressProperty().bind(model.btcSyncProgress);

        model.walletServiceErrorMsg.addListener((ov, oldValue, newValue) -> {
            if (newValue != null) {
                btcInfoLabel.setId("splash-error-state-msg");
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
        versionLabel = new Label();
        versionLabel.setId("footer-pane");
        versionLabel.setTextAlignment(TextAlignment.CENTER);
        versionLabel.setAlignment(Pos.BASELINE_CENTER);
        versionLabel.setText("v" + Version.VERSION);
        root.widthProperty().addListener((ov, oldValue, newValue) -> {
            versionLabel.setLayoutX(((double) newValue - versionLabel.getWidth()) / 2);
        });
        setBottomAnchor(versionLabel, 7d);
        model.newVersionAvailableProperty.addListener((observable, oldValue, newValue) -> {
            if (newValue) {
                versionLabel.setStyle("-fx-text-fill: -bs-error-red; -fx-underline: true; -fx-cursor: hand;");
                versionLabel.setOnMouseClicked(e -> model.openDownloadWindow());
                versionLabel.setText("v" + Version.VERSION + " " + Res.get("mainView.version.update"));
            } else {
                versionLabel.setStyle("-fx-text-fill: black; -fx-underline: false; -fx-cursor: null;");
                versionLabel.setOnMouseClicked(null);
                versionLabel.setText("v" + Version.VERSION);
            }
        });

        // P2P Network
        Label p2PNetworkLabel = new Label();
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
        Label label = new Label();
        label.textProperty().bind(model.numPendingTradesAsString);
        label.relocate(5, 1);
        label.setId("nav-alert-label");

        ImageView icon = new ImageView();
        icon.setLayoutX(0.5);
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
        Label label = new Label();
        label.textProperty().bind(model.numOpenDisputesAsString);
        label.relocate(5, 1);
        label.setId("nav-alert-label");

        ImageView icon = new ImageView();
        icon.setLayoutX(0.5);
        icon.setId("image-alert-round");

        Pane notification = new Pane();
        notification.relocate(30, 9);
        notification.setMouseTransparent(true);
        notification.setEffect(new DropShadow(4, 1, 2, Color.GREY));
        notification.getChildren().addAll(icon, label);
        notification.visibleProperty().bind(model.showOpenDisputesNotification);
        buttonHolder.getChildren().add(notification);
    }

    private class NavButton extends ToggleButton {

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
