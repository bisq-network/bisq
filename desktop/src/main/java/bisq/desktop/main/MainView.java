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
import bisq.desktop.util.Transitions;

import bisq.core.exceptions.BisqException;
import bisq.core.locale.GlobalSettings;
import bisq.core.locale.Res;
import bisq.core.util.BSFormatter;

import bisq.common.Timer;
import bisq.common.UserThread;
import bisq.common.app.Version;
import bisq.common.util.Tuple2;
import bisq.common.util.Utilities;

import javax.inject.Inject;

import com.jfoenix.controls.JFXBadge;
import com.jfoenix.controls.JFXComboBox;
import com.jfoenix.controls.JFXProgressBar;

import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.Separator;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.control.Tooltip;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.text.TextAlignment;

import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;

import javafx.beans.binding.ObjectBinding;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.beans.value.ChangeListener;

import java.text.DecimalFormat;
import java.text.NumberFormat;

import java.util.Locale;

import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import org.jetbrains.annotations.NotNull;

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
    @Setter
    private Runnable onUiReadyHandler;

    public static StackPane getRootContainer() {
        return MainView.rootContainer;
    }

    @SuppressWarnings("PointlessBooleanExpression")
    public static void blur() {
        transitions.blur(MainView.rootContainer);
    }

    @SuppressWarnings("PointlessBooleanExpression")
    public static void blurLight() {
        transitions.blur(MainView.rootContainer, Transitions.DEFAULT_DURATION, -0.1, false, 5);
    }

    @SuppressWarnings("PointlessBooleanExpression")
    public static void blurUltraLight() {
        transitions.blur(MainView.rootContainer, Transitions.DEFAULT_DURATION, -0.1, false, 2);
    }

    @SuppressWarnings("PointlessBooleanExpression")
    public static void darken() {
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
    private ProgressBar btcSyncIndicator, p2pNetworkProgressBar;
    private Label btcSplashInfo;
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

        ToggleButton marketButton = new NavButton(MarketView.class, Res.get("mainView.menu.market").toUpperCase());
        ToggleButton buyButton = new NavButton(BuyOfferView.class, Res.get("mainView.menu.buyBtc").toUpperCase());
        ToggleButton sellButton = new NavButton(SellOfferView.class, Res.get("mainView.menu.sellBtc").toUpperCase());
        ToggleButton portfolioButton = new NavButton(PortfolioView.class, Res.get("mainView.menu.portfolio").toUpperCase());
        ToggleButton fundsButton = new NavButton(FundsView.class, Res.get("mainView.menu.funds").toUpperCase());

        ToggleButton disputesButton = new NavButton(DisputesView.class, Res.get("mainView.menu.support"));
        ToggleButton settingsButton = new NavButton(SettingsView.class, Res.get("mainView.menu.settings"));
        ToggleButton accountButton = new NavButton(AccountView.class, Res.get("mainView.menu.account"));
        ToggleButton daoButton = new NavButton(DaoView.class, Res.get("mainView.menu.dao"));

        JFXBadge portfolioButtonWithBadge = new JFXBadge(portfolioButton);
        JFXBadge disputesButtonWithBadge = new JFXBadge(disputesButton);
        JFXBadge daoButtonWithBadge = new JFXBadge(daoButton);
        daoButtonWithBadge.getStyleClass().add("new");

        Locale locale = GlobalSettings.getLocale();
        DecimalFormat currencyFormat = (DecimalFormat) NumberFormat.getNumberInstance(locale);
        currencyFormat.setMinimumFractionDigits(2);
        currencyFormat.setMaximumFractionDigits(2);

        root.sceneProperty().addListener((observable, oldValue, newValue) -> {
            if (newValue != null) {
                newValue.addEventHandler(KeyEvent.KEY_RELEASED, keyEvent -> {
                    if (Utilities.isAltOrCtrlPressed(KeyCode.DIGIT1, keyEvent)) {
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


        Tuple2<ComboBox<PriceFeedComboBoxItem>, VBox> marketPriceBox = getMarketPriceBox();
        ComboBox<PriceFeedComboBoxItem> priceComboBox = marketPriceBox.first;

        priceComboBox.getSelectionModel().selectedItemProperty().addListener((observable, oldValue, newValue) -> {
            model.setPriceFeedComboBoxItem(newValue);
        });
        ChangeListener<PriceFeedComboBoxItem> selectedPriceFeedItemListener = (observable, oldValue, newValue) -> {
            if (newValue != null)
                priceComboBox.getSelectionModel().select(newValue);

        };
        model.getSelectedPriceFeedComboBoxItemProperty().addListener(selectedPriceFeedItemListener);
        priceComboBox.setItems(model.getPriceFeedComboBoxItems());

        Tuple2<Label, VBox> availableBalanceBox = getBalanceBox(Res.get("mainView.balance.available"));
        availableBalanceBox.first.textProperty().bind(model.getAvailableBalance());
        availableBalanceBox.first.setPrefWidth(100);
        availableBalanceBox.first.tooltipProperty().bind(new ObjectBinding<>() {
            {
                bind(model.getAvailableBalance());
                bind(model.getMarketPrice());
            }

            @Override
            protected Tooltip computeValue() {
                String tooltipText = Res.get("mainView.balance.available");
                try {
                    double availableBalance = Double.parseDouble(
                            model.getAvailableBalance().getValue().replace("BTC", ""));
                    double marketPrice = Double.parseDouble(model.getMarketPrice().getValue());
                    tooltipText += "\n" + currencyFormat.format(availableBalance * marketPrice) +
                            " " + model.getPreferences().getPreferredTradeCurrency().getCode();
                } catch (NullPointerException | NumberFormatException e) {
                    // Either the balance or market price is not available yet
                }
                return new Tooltip(tooltipText);
            }
        });

        Tuple2<Label, VBox> reservedBalanceBox = getBalanceBox(Res.get("mainView.balance.reserved.short"));
        reservedBalanceBox.first.textProperty().bind(model.getReservedBalance());
        reservedBalanceBox.first.tooltipProperty().bind(new ObjectBinding<>() {
            {
                bind(model.getReservedBalance());
                bind(model.getMarketPrice());
            }

            @Override
            protected Tooltip computeValue() {
                String tooltipText = Res.get("mainView.balance.reserved");
                try {
                    double reservedBalance = Double.parseDouble(
                            model.getReservedBalance().getValue().replace("BTC", ""));
                    double marketPrice = Double.parseDouble(model.getMarketPrice().getValue());
                    tooltipText += "\n" + currencyFormat.format(reservedBalance * marketPrice) +
                            " " + model.getPreferences().getPreferredTradeCurrency().getCode();
                } catch (NullPointerException | NumberFormatException e) {
                    // Either the balance or market price is not available yet
                }
                return new Tooltip(tooltipText);
            }
        });

        Tuple2<Label, VBox> lockedBalanceBox = getBalanceBox(Res.get("mainView.balance.locked.short"));
        lockedBalanceBox.first.textProperty().bind(model.getLockedBalance());
        lockedBalanceBox.first.tooltipProperty().bind(new ObjectBinding<>() {
            {
                bind(model.getLockedBalance());
                bind(model.getMarketPrice());
            }

            @Override
            protected Tooltip computeValue() {
                String tooltipText = Res.get("mainView.balance.locked");
                try {
                    double lockedBalance = Double.parseDouble(
                            model.getLockedBalance().getValue().replace("BTC", ""));
                    double marketPrice = Double.parseDouble(model.getMarketPrice().getValue());
                    tooltipText += "\n" + currencyFormat.format(lockedBalance * marketPrice) +
                            " " + model.getPreferences().getPreferredTradeCurrency().getCode();
                } catch (NullPointerException | NumberFormatException e) {
                    // Either the balance or market price is not available yet
                }
                return new Tooltip(tooltipText);
            }
        });

        HBox primaryNav = new HBox(marketButton, getNavigationSeparator(), buyButton, getNavigationSeparator(),
                sellButton, getNavigationSeparator(), portfolioButtonWithBadge, getNavigationSeparator(), fundsButton);

        primaryNav.setAlignment(Pos.CENTER_LEFT);
        primaryNav.getStyleClass().add("nav-primary");
        HBox.setHgrow(primaryNav, Priority.SOMETIMES);

        HBox secondaryNav = new HBox(disputesButtonWithBadge, getNavigationSpacer(), settingsButton,
                getNavigationSpacer(), accountButton, getNavigationSpacer(), daoButtonWithBadge);
        secondaryNav.getStyleClass().add("nav-secondary");
        HBox.setHgrow(secondaryNav, Priority.SOMETIMES);

        secondaryNav.setAlignment(Pos.CENTER);

        HBox priceAndBalance = new HBox(marketPriceBox.second, getNavigationSeparator(), availableBalanceBox.second,
                getNavigationSeparator(), reservedBalanceBox.second, getNavigationSeparator(), lockedBalanceBox.second);
        priceAndBalance.setMaxHeight(41);

        priceAndBalance.setAlignment(Pos.CENTER);
        priceAndBalance.setSpacing(11);
        priceAndBalance.getStyleClass().add("nav-price-balance");

        HBox navPane = new HBox(primaryNav, secondaryNav,
                priceAndBalance) {{
            setLeftAnchor(this, 0d);
            setRightAnchor(this, 0d);
            setTopAnchor(this, 0d);
            setPadding(new Insets(0, 0, 0, 0));
            getStyleClass().add("top-navigation");
        }};
        navPane.setAlignment(Pos.CENTER);

        AnchorPane contentContainer = new AnchorPane() {{
            getStyleClass().add("content-pane");
            setLeftAnchor(this, 0d);
            setRightAnchor(this, 0d);
            setTopAnchor(this, 57d);
            setBottomAnchor(this, 0d);
        }};

        AnchorPane applicationContainer = new AnchorPane(navPane, contentContainer) {{
            setId("application-container");
        }};

        BorderPane baseApplicationContainer = new BorderPane(applicationContainer) {{
            setId("base-content-container");
        }};
        baseApplicationContainer.setBottom(createFooter());

        setupBadge(portfolioButtonWithBadge, model.getNumPendingTrades(), model.getShowPendingTradesNotification());
        setupBadge(disputesButtonWithBadge, model.getNumOpenDisputes(), model.getShowOpenDisputesNotification());
        setupBadge(daoButtonWithBadge, new SimpleStringProperty(Res.get("shared.new")), model.getShowDaoUpdatesNotification());

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

        model.getShowAppScreen().addListener((ov, oldValue, newValue) -> {
            if (newValue) {
                navigation.navigateToPreviousVisitedView();

                transitions.fadeOutAndRemove(splashScreen, 1500, actionEvent -> disposeSplashScreen());
            }
        });

        // Delay a bit to give time for rendering the splash screen
        UserThread.execute(() -> onUiReadyHandler.run());
    }

    @NotNull
    private Separator getNavigationSeparator() {
        final Separator separator = new Separator(Orientation.VERTICAL);
        HBox.setHgrow(separator, Priority.ALWAYS);
        separator.setMaxHeight(22);
        separator.setMaxWidth(Double.MAX_VALUE);
        return separator;
    }

    @NotNull
    private Region getNavigationSpacer() {
        final Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        return spacer;
    }

    private Tuple2<Label, VBox> getBalanceBox(String text) {
        Label balanceDisplay = new Label();
        balanceDisplay.getStyleClass().add("nav-balance-display");

        Label label = new Label(text);
        label.getStyleClass().add("nav-balance-label");
        label.maxWidthProperty().bind(balanceDisplay.widthProperty());
        label.setPadding(new Insets(0, 0, 0, 0));
        VBox vBox = new VBox();
        vBox.setAlignment(Pos.CENTER_LEFT);
        vBox.getChildren().addAll(balanceDisplay, label);
        return new Tuple2<>(balanceDisplay, vBox);
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

        VBox marketPriceBox = new VBox();
        marketPriceBox.setAlignment(Pos.CENTER_LEFT);

        ComboBox<PriceFeedComboBoxItem> priceComboBox = new JFXComboBox<>();
        priceComboBox.setVisibleRowCount(12);
        priceComboBox.setFocusTraversable(false);
        priceComboBox.setId("price-feed-combo");
        priceComboBox.setPadding(new Insets(0, 0, -4, 0));
        priceComboBox.setCellFactory(p -> getPriceFeedComboBoxListCell());
        ListCell<PriceFeedComboBoxItem> buttonCell = getPriceFeedComboBoxListCell();
        buttonCell.setId("price-feed-combo");
        priceComboBox.setButtonCell(buttonCell);

        Label marketPriceLabel = new Label();

        updateMarketPriceLabel(marketPriceLabel);

        marketPriceLabel.getStyleClass().add("nav-balance-label");
        marketPriceLabel.setPadding(new Insets(-2, 0, 4, 9));

        marketPriceBox.getChildren().addAll(priceComboBox, marketPriceLabel);

        model.getMarketPriceUpdated().addListener((observable, oldValue, newValue) -> {
            updateMarketPriceLabel(marketPriceLabel);
        });

        return new Tuple2<>(priceComboBox, marketPriceBox);
    }

    @NotNull
    private String getPriceProvider() {
        return model.getIsFiatCurrencyPriceFeedSelected().get() ? "BitcoinAverage" : "Poloniex";
    }

    private void updateMarketPriceLabel(Label label) {
        if (model.getIsPriceAvailable().get()) {
            if (model.getIsExternallyProvidedPrice().get()) {
                label.setText(Res.get("mainView.marketPriceWithProvider.label", getPriceProvider()));
                label.setTooltip(new Tooltip(getPriceProviderTooltipString()));
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

    @NotNull
    private String getPriceProviderTooltipString() {

        String res;
        if (model.getIsFiatCurrencyPriceFeedSelected().get()) {
            res = Res.get("mainView.marketPrice.tooltip",
                    "https://bitcoinaverage.com",
                    "",
                    formatter.formatTime(model.getPriceFeedService().getLastRequestTimeStampBtcAverage()),
                    model.getPriceFeedService().getProviderNodeAddress());
        } else {
            String altcoinExtra = "\n" + Res.get("mainView.marketPrice.tooltip.altcoinExtra");
            res = Res.get("mainView.marketPrice.tooltip",
                    "https://poloniex.com",
                    altcoinExtra,
                    formatter.formatTime(model.getPriceFeedService().getLastRequestTimeStampPoloniex()),
                    model.getPriceFeedService().getProviderNodeAddress());
        }
        return res;
    }

    private VBox createSplashScreen() {
        VBox vBox = new VBox();
        vBox.setAlignment(Pos.CENTER);
        vBox.setSpacing(10);
        vBox.setId("splash");

        ImageView logo = new ImageView();
        logo.setId("image-splash-logo");


        // createBitcoinInfoBox
        btcSplashInfo = new AutoTooltipLabel();
        btcSplashInfo.textProperty().bind(model.getBtcInfo());
        walletServiceErrorMsgListener = (ov, oldValue, newValue) -> {
            btcSplashInfo.setId("splash-error-state-msg");
            btcSplashInfo.getStyleClass().add("error-text");
        };
        model.getWalletServiceErrorMsg().addListener(walletServiceErrorMsgListener);

        btcSyncIndicator = new JFXProgressBar();
        btcSyncIndicator.setPrefWidth(305);
        btcSyncIndicator.progressProperty().bind(model.getCombinedSyncProgress());

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
        model.getBtcSplashSyncIconId().addListener(btcSyncIconIdListener);


        HBox blockchainSyncBox = new HBox();
        blockchainSyncBox.setSpacing(10);
        blockchainSyncBox.setAlignment(Pos.CENTER);
        blockchainSyncBox.setPadding(new Insets(40, 0, 0, 0));
        blockchainSyncBox.setPrefHeight(50);
        blockchainSyncBox.getChildren().addAll(btcSplashInfo, btcSyncIcon);


        // create P2PNetworkBox
        splashP2PNetworkLabel = new AutoTooltipLabel();
        splashP2PNetworkLabel.setWrapText(true);
        splashP2PNetworkLabel.setMaxWidth(500);
        splashP2PNetworkLabel.setTextAlignment(TextAlignment.CENTER);
        splashP2PNetworkLabel.getStyleClass().add("sub-info");
        splashP2PNetworkLabel.textProperty().bind(model.getP2PNetworkInfo());

        Button showTorNetworkSettingsButton = new AutoTooltipButton(Res.get("settings.net.openTorSettingsButton"));
        showTorNetworkSettingsButton.setVisible(false);
        showTorNetworkSettingsButton.setManaged(false);
        showTorNetworkSettingsButton.setOnAction(e -> {
            model.getTorNetworkSettingsWindow().show();
        });

        splashP2PNetworkBusyAnimation = new BusyAnimation(false);

        splashP2PNetworkErrorMsgListener = (ov, oldValue, newValue) -> {
            if (newValue != null) {
                splashP2PNetworkLabel.setId("splash-error-state-msg");
                splashP2PNetworkLabel.getStyleClass().remove("sub-info");
                splashP2PNetworkLabel.getStyleClass().add("error-text");
                splashP2PNetworkBusyAnimation.setDisable(true);
                splashP2PNetworkBusyAnimation.stop();
                showTorNetworkSettingsButton.setVisible(true);
                showTorNetworkSettingsButton.setManaged(true);
                if (model.getUseTorForBTC().get()) {
                    // If using tor for BTC, hide the BTC status since tor is not working
                    btcSyncIndicator.setVisible(false);
                    btcSplashInfo.setVisible(false);
                }
            } else if (model.getSplashP2PNetworkAnimationVisible().get()) {
                splashP2PNetworkBusyAnimation.setDisable(false);
                splashP2PNetworkBusyAnimation.play();
            }
        };
        model.getP2pNetworkWarnMsg().addListener(splashP2PNetworkErrorMsgListener);

        ImageView splashP2PNetworkIcon = new ImageView();
        splashP2PNetworkIcon.setId("image-connection-tor");
        splashP2PNetworkIcon.setVisible(false);
        splashP2PNetworkIcon.setManaged(false);
        HBox.setMargin(splashP2PNetworkIcon, new Insets(0, 0, 5, 0));

        Timer showTorNetworkSettingsTimer = UserThread.runAfter(() -> {
            showTorNetworkSettingsButton.setVisible(true);
            showTorNetworkSettingsButton.setManaged(true);
            if (btcSyncIndicator.progressProperty().getValue() <= 0) {
                // If no progress has been made, hide the BTC status since tor is not working
                btcSyncIndicator.setVisible(false);
                btcSplashInfo.setVisible(false);
            }
        }, SHOW_TOR_SETTINGS_DELAY_SEC);

        splashP2PNetworkIconIdListener = (ov, oldValue, newValue) -> {
            splashP2PNetworkIcon.setId(newValue);
            splashP2PNetworkIcon.setVisible(true);
            splashP2PNetworkIcon.setManaged(true);

            // if we can connect in 10 sec. we know that tor is working
            showTorNetworkSettingsTimer.stop();
        };
        model.getP2PNetworkIconId().addListener(splashP2PNetworkIconIdListener);

        splashP2PNetworkVisibleListener = (ov, oldValue, newValue) -> {
            splashP2PNetworkBusyAnimation.setDisable(!newValue);
            if (newValue) splashP2PNetworkBusyAnimation.play();
        };

        model.getSplashP2PNetworkAnimationVisible().addListener(splashP2PNetworkVisibleListener);

        HBox splashP2PNetworkBox = new HBox();
        splashP2PNetworkBox.setSpacing(10);
        splashP2PNetworkBox.setAlignment(Pos.CENTER);
        splashP2PNetworkBox.setPrefHeight(40);
        splashP2PNetworkBox.getChildren().addAll(splashP2PNetworkLabel, splashP2PNetworkBusyAnimation, splashP2PNetworkIcon, showTorNetworkSettingsButton);

        vBox.getChildren().addAll(logo, blockchainSyncBox, btcSyncIndicator, splashP2PNetworkBox);
        return vBox;
    }

    private void disposeSplashScreen() {
        model.getWalletServiceErrorMsg().removeListener(walletServiceErrorMsgListener);
        model.getBtcSplashSyncIconId().removeListener(btcSyncIconIdListener);

        model.getP2pNetworkWarnMsg().removeListener(splashP2PNetworkErrorMsgListener);
        model.getP2PNetworkIconId().removeListener(splashP2PNetworkIconIdListener);
        model.getSplashP2PNetworkAnimationVisible().removeListener(splashP2PNetworkVisibleListener);

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
        btcInfoLabel.textProperty().bind(model.getBtcInfo());

        ProgressBar blockchainSyncIndicator = new JFXProgressBar(-1);
        blockchainSyncIndicator.setPrefWidth(80);
        blockchainSyncIndicator.setMaxHeight(10);
        blockchainSyncIndicator.progressProperty().bind(model.getCombinedSyncProgress());

        model.getWalletServiceErrorMsg().addListener((ov, oldValue, newValue) -> {
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

        model.getCombinedSyncProgress().addListener((ov, oldValue, newValue) -> {
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
        model.getNewVersionAvailableProperty().addListener((observable, oldValue, newValue) -> {
            versionLabel.getStyleClass().removeAll("version-new", "version");
            if (newValue) {
                versionLabel.getStyleClass().add("version-new");
                versionLabel.setOnMouseClicked(e -> model.onOpenDownloadWindow());
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
        p2PNetworkLabel.textProperty().bind(model.getP2PNetworkInfo());

        ImageView p2PNetworkIcon = new ImageView();
        setRightAnchor(p2PNetworkIcon, 10d);
        setBottomAnchor(p2PNetworkIcon, 5d);
        p2PNetworkIcon.setOpacity(0.4);
        p2PNetworkIcon.idProperty().bind(model.getP2PNetworkIconId());
        p2PNetworkLabel.idProperty().bind(model.getP2pNetworkLabelId());
        model.getP2pNetworkWarnMsg().addListener((ov, oldValue, newValue) -> {
            if (newValue != null) {
                p2PNetworkWarnMsgPopup = new Popup<>().warning(newValue);
                p2PNetworkWarnMsgPopup.show();
            } else if (p2PNetworkWarnMsgPopup != null) {
                p2PNetworkWarnMsgPopup.hide();
            }
        });

        model.getUpdatedDataReceived().addListener((observable, oldValue, newValue) -> {
            p2PNetworkIcon.setOpacity(1);
            p2pNetworkProgressBar.setProgress(0);
        });

        p2pNetworkProgressBar = new JFXProgressBar(-1);
        p2pNetworkProgressBar.setMaxHeight(2);
        p2pNetworkProgressBar.prefWidthProperty().bind(p2PNetworkLabel.widthProperty());

        VBox vBox = new VBox();
        vBox.setAlignment(Pos.CENTER_RIGHT);
        vBox.getChildren().addAll(p2PNetworkLabel, p2pNetworkProgressBar);
        setRightAnchor(vBox, 33d);
        setBottomAnchor(vBox, 5d);

        return new AnchorPane(separator, blockchainSyncBox, versionLabel, vBox, p2PNetworkIcon) {{
            setId("footer-pane");
            setMinHeight(30);
            setMaxHeight(30);
        }};
    }

    private void setupBadge(JFXBadge buttonWithBadge, StringProperty badgeNumber, BooleanProperty badgeEnabled) {
        buttonWithBadge.textProperty().bind(badgeNumber);
        buttonWithBadge.setEnabled(badgeEnabled.get());
        badgeEnabled.addListener((observable, oldValue, newValue) -> {
            buttonWithBadge.setEnabled(newValue);
            buttonWithBadge.refreshBadge();
        });

        buttonWithBadge.setPosition(Pos.TOP_RIGHT);
        buttonWithBadge.setMinHeight(34);
        buttonWithBadge.setMaxHeight(34);
    }

    private class NavButton extends AutoTooltipToggleButton {

        private final Class<? extends View> viewClass;

        NavButton(Class<? extends View> viewClass, String title) {
            super(title);

            this.viewClass = viewClass;

            this.setToggleGroup(navButtons);
            this.getStyleClass().add("nav-button");

            this.selectedProperty().addListener((ov, oldValue, newValue) -> {
                this.setMouseTransparent(newValue);
            });

            this.setOnAction(e -> navigation.navigateTo(MainView.class, viewClass));
        }

    }
}
