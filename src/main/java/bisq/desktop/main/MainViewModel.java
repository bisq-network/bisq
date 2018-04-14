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

import bisq.desktop.common.model.ViewModel;
import bisq.desktop.components.BalanceWithConfirmationTextField;
import bisq.desktop.components.TxIdTextField;
import bisq.desktop.main.overlays.notifications.NotificationCenter;
import bisq.desktop.main.overlays.popups.Popup;
import bisq.desktop.main.overlays.windows.DisplayAlertMessageWindow;
import bisq.desktop.main.overlays.windows.TacWindow;
import bisq.desktop.main.overlays.windows.TorNetworkSettingsWindow;
import bisq.desktop.main.overlays.windows.WalletPasswordWindow;
import bisq.desktop.main.overlays.windows.downloadupdate.DisplayUpdateDownloadWindow;
import bisq.desktop.util.BSFormatter;
import bisq.desktop.util.GUIUtil;

import bisq.core.alert.Alert;
import bisq.core.alert.PrivateNotificationManager;
import bisq.core.alert.PrivateNotificationPayload;
import bisq.core.app.AppOptionKeys;
import bisq.core.app.AppSetupFullApp;
import bisq.core.app.BisqEnvironment;
import bisq.core.app.StartupHandler;
import bisq.core.btc.wallet.BtcWalletService;
import bisq.core.btc.wallet.WalletsManager;
import bisq.core.btc.wallet.WalletsSetup;
import bisq.core.filter.FilterManager;
import bisq.core.locale.CurrencyUtil;
import bisq.core.locale.Res;
import bisq.core.locale.TradeCurrency;
import bisq.core.offer.Offer;
import bisq.core.offer.OpenOffer;
import bisq.core.offer.OpenOfferManager;
import bisq.core.provider.fee.FeeService;
import bisq.core.provider.price.MarketPrice;
import bisq.core.provider.price.PriceFeedService;
import bisq.core.user.Preferences;
import bisq.core.user.User;

import bisq.common.UserThread;
import bisq.common.app.DevEnv;

import org.bitcoinj.core.Coin;
import org.bitcoinj.store.ChainFileLockedException;

import com.google.inject.Inject;

import org.fxmisc.easybind.EasyBind;
import org.fxmisc.easybind.Subscription;
import org.fxmisc.easybind.monadic.MonadicBinding;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;

import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;

import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class MainViewModel implements ViewModel, StartupHandler {
    private final AppSetupFullApp appSetupFullApp;
    private final WalletsManager walletsManager;
    private final WalletsSetup walletsSetup;
    private final OpenOfferManager openOfferManager;
    private final Preferences preferences;
    private final User user;
    private final PrivateNotificationManager privateNotificationManager;
    @SuppressWarnings({"unused", "FieldCanBeLocal"})
    private final FilterManager filterManager;
    private final WalletPasswordWindow walletPasswordWindow;
    private final NotificationCenter notificationCenter;
    private final TacWindow tacWindow;
    private final FeeService feeService;
    private final BisqEnvironment bisqEnvironment;
    final TorNetworkSettingsWindow torNetworkSettingsWindow;
    private final BSFormatter formatter;

    // BTC network
    final StringProperty btcInfo = new SimpleStringProperty(Res.get("mainView.footer.btcInfo.initializing"));
    @SuppressWarnings("ConstantConditions")
    final DoubleProperty btcSyncProgress = new SimpleDoubleProperty(DevEnv.STRESS_TEST_MODE ? 0 : -1);
    final StringProperty walletServiceErrorMsg = new SimpleStringProperty();
    final StringProperty btcSplashSyncIconId = new SimpleStringProperty();
    private final StringProperty marketPriceCurrencyCode = new SimpleStringProperty("");
    final ObjectProperty<PriceFeedComboBoxItem> selectedPriceFeedComboBoxItemProperty = new SimpleObjectProperty<>();
    final BooleanProperty isFiatCurrencyPriceFeedSelected = new SimpleBooleanProperty(true);
    final BooleanProperty isCryptoCurrencyPriceFeedSelected = new SimpleBooleanProperty(false);
    final BooleanProperty isExternallyProvidedPrice = new SimpleBooleanProperty(true);
    final BooleanProperty isPriceAvailable = new SimpleBooleanProperty(false);
    final BooleanProperty newVersionAvailableProperty = new SimpleBooleanProperty(false);
    final IntegerProperty marketPriceUpdated = new SimpleIntegerProperty(0);
    final StringProperty availableBalance = new SimpleStringProperty();
    final StringProperty reservedBalance = new SimpleStringProperty();
    final StringProperty lockedBalance = new SimpleStringProperty();

    private final StringProperty marketPrice = new SimpleStringProperty(Res.get("shared.na"));

    // P2P network
    final StringProperty p2PNetworkInfo = new SimpleStringProperty();
    final BooleanProperty splashP2PNetworkAnimationVisible = new SimpleBooleanProperty(true);
    final StringProperty p2pNetworkWarnMsg = new SimpleStringProperty();
    final StringProperty p2PNetworkIconId = new SimpleStringProperty();
    final BooleanProperty bootstrapComplete = new SimpleBooleanProperty();
    final BooleanProperty showAppScreen = new SimpleBooleanProperty();
    final StringProperty numPendingTradesAsString = new SimpleStringProperty();
    final BooleanProperty showPendingTradesNotification = new SimpleBooleanProperty();
    final StringProperty numOpenDisputesAsString = new SimpleStringProperty();
    final BooleanProperty showOpenDisputesNotification = new SimpleBooleanProperty();
    private final BooleanProperty isSplashScreenRemoved = new SimpleBooleanProperty();
    final StringProperty p2pNetworkLabelId = new SimpleStringProperty("footer-pane");

    final PriceFeedService priceFeedService;
    final ObservableList<PriceFeedComboBoxItem> priceFeedComboBoxItems = FXCollections.observableArrayList();
    private MonadicBinding<String> marketPriceBinding;
    private final BooleanProperty walletInitialized = new SimpleBooleanProperty();
    private Subscription priceFeedAllLoadedSubscription;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    @SuppressWarnings("WeakerAccess")
    @Inject
    public MainViewModel(AppSetupFullApp appSetupFullApp,
                         WalletsManager walletsManager,
                         WalletsSetup walletsSetup,
                         BtcWalletService btcWalletService,
                         PriceFeedService priceFeedService,
                         OpenOfferManager openOfferManager,
                         Preferences preferences,
                         User user,
                         PrivateNotificationManager privateNotificationManager,
                         FilterManager filterManager,
                         WalletPasswordWindow walletPasswordWindow,
                         NotificationCenter notificationCenter,
                         TacWindow tacWindow,
                         FeeService feeService,
                         BisqEnvironment bisqEnvironment,
                         TorNetworkSettingsWindow torNetworkSettingsWindow,
                         BSFormatter formatter) {
        this.appSetupFullApp = appSetupFullApp;
        this.walletsManager = walletsManager;
        this.walletsSetup = walletsSetup;
        this.priceFeedService = priceFeedService;
        this.openOfferManager = openOfferManager;
        this.preferences = preferences;
        this.user = user;
        this.privateNotificationManager = privateNotificationManager;
        this.filterManager = filterManager; // Reference so it's initialized and eventListener gets registered
        this.walletPasswordWindow = walletPasswordWindow;
        this.notificationCenter = notificationCenter;
        this.tacWindow = tacWindow;
        this.feeService = feeService;
        this.bisqEnvironment = bisqEnvironment;
        this.torNetworkSettingsWindow = torNetworkSettingsWindow;
        this.formatter = formatter;

        TxIdTextField.setPreferences(preferences);

        // TODO
        TxIdTextField.setWalletService(btcWalletService);
        BalanceWithConfirmationTextField.setWalletService(btcWalletService);
    }

    public void start() {
        appSetupFullApp.start(this);

        fillPriceFeedComboBoxItems();
        setupMarketPriceFeed();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // StartupHandler
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void onCryptoSetupError(String errorMessage) {
        UserThread.execute(() -> {
            new Popup<>().warning(errorMessage)
                    .useShutDownButton()
                    .useReportBugButton()
                    .show();
        });
    }

    @Override
    public void onShowTac() {
        UserThread.runAfter(() -> {
            tacWindow.onAction(() -> {
                preferences.setTacAccepted(true);
                appSetupFullApp.checkIfLocalHostNodeIsRunning();
            }).show();
        }, 1);
    }

    @Override
    public void onShowAppScreen() {
        showAppScreen.set(true);
    }

    @Override
    public void onShowTorNetworkSettingsWindow() {
        torNetworkSettingsWindow.show();
    }

    @Override
    public void onHideTorNetworkSettingsWindow() {
        if (torNetworkSettingsWindow != null)
            torNetworkSettingsWindow.hide();
    }

    @Override
    public void onLockedUpFundsWarning(Coin balance, String addressString, String offerId) {
        final String message = Res.get("popup.warning.lockedUpFunds",
                formatter.formatCoinWithCode(balance), addressString, offerId);
        log.warn(message);
        new Popup<>().warning(message).show();
    }

    @Override
    public void onBtcDownloadProgress(double percentage, int peers) {
        btcSyncProgress.set(percentage);
        if (percentage == 1) {
            btcInfo.set(Res.get("mainView.footer.btcInfo",
                    peers,
                    Res.get("mainView.footer.btcInfo.synchronizedWith"),
                    getBtcNetworkAsString()));
            btcSplashSyncIconId.set("image-connection-synced");
        } else if (percentage > 0.0) {
            btcInfo.set(Res.get("mainView.footer.btcInfo",
                    peers,
                    Res.get("mainView.footer.btcInfo.synchronizedWith"),
                    getBtcNetworkAsString() + ": " + formatter.formatToPercentWithSymbol(percentage)));
        } else {
            btcInfo.set(Res.get("mainView.footer.btcInfo",
                    peers,
                    Res.get("mainView.footer.btcInfo.connectingTo"),
                    getBtcNetworkAsString()));
        }
    }

    @Override
    public void onBtcDownloadError(int numBtcPeers) {
        btcInfo.set(Res.get("mainView.footer.btcInfo",
                numBtcPeers,
                Res.get("mainView.footer.btcInfo.connectionFailed"),
                getBtcNetworkAsString()));
    }

    @Override
    public void onWalletSetupException(Throwable exception) {
        if (exception.getCause().getCause() instanceof ChainFileLockedException) {
            new Popup<>().warning(Res.get("popup.warning.startupFailed.twoInstances"))
                    .useShutDownButton()
                    .show();
        } else {
            new Popup<>().warning(Res.get("error.spvFileCorrupted", exception.getMessage()))
                    .actionButtonText(Res.get("settings.net.reSyncSPVChainButton"))
                    .onAction(() -> GUIUtil.reSyncSPVChain(walletsSetup, preferences));

        }
    }

    @Override
    public void onShowFirstPopupIfResyncSPVRequested() {
        Popup firstPopup = new Popup<>();
        firstPopup.information(Res.get("settings.net.reSyncSPVAfterRestart")).show();
        if (btcSyncProgress.get() == 1) {
            showSecondPopupIfResyncSPVRequested(firstPopup);
        } else {
            btcSyncProgress.addListener((observable, oldValue, newValue) -> {
                if ((double) newValue == 1)
                    showSecondPopupIfResyncSPVRequested(firstPopup);
            });
        }
    }

    @Override
    public void onShowWalletPasswordWindow() {
        walletPasswordWindow
                .onAesKey(aesKey -> {
                    walletsManager.setAesKey(aesKey);
                    if (preferences.isResyncSpvRequested()) {
                        onShowFirstPopupIfResyncSPVRequested();
                    } else {
                        walletInitialized.set(true);
                    }
                })
                .hideCloseButton()
                .show();
    }

    @Override
    public void onShowTakeOfferRequestError(String errorMessage) {
        new Popup<>()
                .warning(Res.get("popup.error.takeOfferRequestFailed", errorMessage))
                .show();
    }

    @Override
    public void onFeeServiceInitialized() {
        GUIUtil.setFeeService(feeService);
    }

    @Override
    public void onDaoSetupError(String errorMessage) {
        new Popup<>().error(errorMessage).show();
    }

    @Override
    public void onSeedNodeBanned() {
        new Popup<>().warning(Res.get("popup.warning.nodeBanned", Res.get("popup.warning.seed"))).show();
    }

    @Override
    public void onPriceNodeBanned() {
        new Popup<>().warning(Res.get("popup.warning.nodeBanned", Res.get("popup.warning.priceRelay"))).show();
    }

    @Override
    public void onShowSecurityRecommendation(String key) {
        new Popup<>().headLine(Res.get("popup.securityRecommendation.headline"))
                .information(Res.get("popup.securityRecommendation.msg"))
                .dontShowAgainId(key)
                .show();
    }

    @Override
    public void onDisplayUpdateDownloadWindow(Alert alert, String key) {
        new DisplayUpdateDownloadWindow(alert)
                .actionButtonText(Res.get("displayUpdateDownloadWindow.button.downloadLater"))
                .onAction(() -> {
                    preferences.dontShowAgain(key, false); // update later
                })
                .closeButtonText(Res.get("shared.cancel"))
                .onClose(() -> {
                    preferences.dontShowAgain(key, true); // ignore update
                })
                .show();
    }

    @Override
    public void onDisplayAlertMessageWindow(Alert alert) {
        new DisplayAlertMessageWindow()
                .alertMessage(alert)
                .onClose(() -> {
                    user.setDisplayedAlert(alert);
                })
                .show();
    }

    @Override
    public void setTotalAvailableBalance(Coin balance) {
        String value = formatter.formatCoinWithCode(balance);
        // If we get full precision the BTC postfix breaks layout so we omit it
        if (value.length() > 11)
            value = formatter.formatCoin(balance);
        availableBalance.set(value);
    }

    @Override
    public void setReservedBalance(Coin balance) {
        reservedBalance.set(formatter.formatCoinWithCode(balance));
    }

    @Override
    public void setLockedBalance(Coin balance) {
        lockedBalance.set(formatter.formatCoinWithCode(balance));
    }

    @Override
    public void onWarnOldOffers(String offers, List<OpenOffer> outDatedOffers) {
        new Popup<>()
                .warning(Res.get("popup.warning.oldOffers.msg", offers))
                .actionButtonText(Res.get("popup.warning.oldOffers.buttonText"))
                .onAction(() -> openOfferManager.removeOpenOffers(outDatedOffers, null))
                .useShutDownButton()
                .show();
    }

    @Override
    public void onHalfTradePeriodReached(String shortId, Date maxTradePeriodDate) {
        new Popup<>().warning(Res.get("popup.warning.tradePeriod.halfReached",
                shortId,
                formatter.formatDateTime(maxTradePeriodDate)))
                .show();
    }

    @Override
    public void onTradePeriodEnded(String shortId, Date maxTradePeriodDate) {
        new Popup<>().warning(Res.get("popup.warning.tradePeriod.ended",
                shortId,
                formatter.formatDateTime(maxTradePeriodDate)))
                .show();
    }

    @Override
    public void onDisplayPrivateNotification(PrivateNotificationPayload privateNotification) {
        new Popup<>().headLine(Res.get("popup.privateNotification.headline"))
                .attention(privateNotification.getMessage())
                .setHeadlineStyle("-fx-text-fill: -bs-error-red;  -fx-font-weight: bold;  -fx-font-size: 16;")
                .onClose(privateNotificationManager::removePrivateNotification)
                .useIUnderstandButton()
                .show();
    }

    @Override
    public void onOfferWithoutAccountAgeWitness(Offer offer) {
        new Popup<>().warning(Res.get("popup.warning.offerWithoutAccountAgeWitness", offer.getId()))
                .actionButtonText(Res.get("popup.warning.offerWithoutAccountAgeWitness.confirm"))
                .onAction(() -> {
                    openOfferManager.removeOffer(offer,
                            () -> {
                                log.info("Offer with ID {} is removed", offer.getId());
                            },
                            log::error);
                })
                .hideCloseButton()
                .show();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // UI handlers
    ///////////////////////////////////////////////////////////////////////////////////////////

    // After showAppScreen is set and splash screen is faded out
    void onSplashScreenRemoved() {
        isSplashScreenRemoved.set(true);

        // Delay that as we want to know what is the current path of the navigation which is set
        // in MainView showAppScreen handler
        notificationCenter.onAllServicesAndViewsInitialized();
    }

    void setPriceFeedComboBoxItem(PriceFeedComboBoxItem item) {
        if (item != null) {
            Optional<PriceFeedComboBoxItem> itemOptional = findPriceFeedComboBoxItem(priceFeedService.currencyCodeProperty().get());
            if (itemOptional.isPresent())
                selectedPriceFeedComboBoxItemProperty.set(itemOptional.get());
            else
                findPriceFeedComboBoxItem(preferences.getPreferredTradeCurrency().getCode())
                        .ifPresent(selectedPriceFeedComboBoxItemProperty::set);

            priceFeedService.setCurrencyCode(item.currencyCode);
        } else {
            findPriceFeedComboBoxItem(preferences.getPreferredTradeCurrency().getCode())
                    .ifPresent(selectedPriceFeedComboBoxItemProperty::set);
        }
    }

    String getAppDateDir() {
        return bisqEnvironment.getProperty(AppOptionKeys.APP_DATA_DIR_KEY);
    }

    void openDownloadWindow() {
        displayAlertIfPresent(user.getDisplayedAlert(), true);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Private
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void showSecondPopupIfResyncSPVRequested(Popup firstPopup) {
        firstPopup.hide();
        preferences.setResyncSpvRequested(false);
        new Popup<>().information(Res.get("settings.net.reSyncSPVAfterRestartCompleted"))
                .hideCloseButton()
                .useShutDownButton()
                .show();
    }


    private void setupMarketPriceFeed() {
        priceFeedService.requestPriceFeed(price -> marketPrice.set(formatter.formatMarketPrice(price, priceFeedService.getCurrencyCode())),
                (errorMessage, throwable) -> marketPrice.set(Res.get("shared.na")));

        marketPriceBinding = EasyBind.combine(
                marketPriceCurrencyCode, marketPrice,
                (currencyCode, price) -> formatter.getCurrencyPair(currencyCode) + ": " + price);

        marketPriceBinding.subscribe((observable, oldValue, newValue) -> {
            if (newValue != null && !newValue.equals(oldValue)) {
                setMarketPriceInItems();

                String code = priceFeedService.currencyCodeProperty().get();
                Optional<PriceFeedComboBoxItem> itemOptional = findPriceFeedComboBoxItem(code);
                if (itemOptional.isPresent()) {
                    itemOptional.get().setDisplayString(newValue);
                    selectedPriceFeedComboBoxItemProperty.set(itemOptional.get());
                } else {
                    if (CurrencyUtil.isCryptoCurrency(code)) {
                        CurrencyUtil.getCryptoCurrency(code).ifPresent(cryptoCurrency -> {
                            preferences.addCryptoCurrency(cryptoCurrency);
                            fillPriceFeedComboBoxItems();
                        });
                    } else {
                        CurrencyUtil.getFiatCurrency(code).ifPresent(fiatCurrency -> {
                            preferences.addFiatCurrency(fiatCurrency);
                            fillPriceFeedComboBoxItems();
                        });
                    }
                }

                if (selectedPriceFeedComboBoxItemProperty.get() != null)
                    selectedPriceFeedComboBoxItemProperty.get().setDisplayString(newValue);
            }
        });

        marketPriceCurrencyCode.bind(priceFeedService.currencyCodeProperty());

        priceFeedAllLoadedSubscription = EasyBind.subscribe(priceFeedService.updateCounterProperty(), updateCounter -> setMarketPriceInItems());

        preferences.getTradeCurrenciesAsObservable().addListener((ListChangeListener<TradeCurrency>) c -> UserThread.runAfter(() -> {
            fillPriceFeedComboBoxItems();
            setMarketPriceInItems();
        }, 100, TimeUnit.MILLISECONDS));
    }

    private void setMarketPriceInItems() {
        priceFeedComboBoxItems.forEach(item -> {
            String currencyCode = item.currencyCode;
            MarketPrice marketPrice = priceFeedService.getMarketPrice(currencyCode);
            String priceString;
            if (marketPrice != null && marketPrice.isPriceAvailable()) {
                priceString = formatter.formatMarketPrice(marketPrice.getPrice(), currencyCode);
                item.setPriceAvailable(true);
                item.setExternallyProvidedPrice(marketPrice.isExternallyProvidedPrice());
            } else {
                priceString = Res.get("shared.na");
                item.setPriceAvailable(false);
            }
            item.setDisplayString(formatter.getCurrencyPair(currencyCode) + ": " + priceString);

            final String code = item.currencyCode;
            if (selectedPriceFeedComboBoxItemProperty.get() != null &&
                    selectedPriceFeedComboBoxItemProperty.get().currencyCode.equals(code)) {
                isFiatCurrencyPriceFeedSelected.set(CurrencyUtil.isFiatCurrency(code) && CurrencyUtil.getFiatCurrency(code).isPresent() && item.isPriceAvailable() && item.isExternallyProvidedPrice());
                isCryptoCurrencyPriceFeedSelected.set(CurrencyUtil.isCryptoCurrency(code) && CurrencyUtil.getCryptoCurrency(code).isPresent() && item.isPriceAvailable() && item.isExternallyProvidedPrice());
                isExternallyProvidedPrice.set(item.isExternallyProvidedPrice());
                isPriceAvailable.set(item.isPriceAvailable());
                marketPriceUpdated.set(marketPriceUpdated.get() + 1);
            }
        });
    }

    private Optional<PriceFeedComboBoxItem> findPriceFeedComboBoxItem(String currencyCode) {
        return priceFeedComboBoxItems.stream()
                .filter(item -> item.currencyCode.equals(currencyCode))
                .findAny();
    }

    private void fillPriceFeedComboBoxItems() {
        List<PriceFeedComboBoxItem> currencyItems = preferences.getTradeCurrenciesAsObservable()
                .stream()
                .map(tradeCurrency -> new PriceFeedComboBoxItem(tradeCurrency.getCode()))
                .collect(Collectors.toList());
        priceFeedComboBoxItems.setAll(currencyItems);
    }

    private void displayAlertIfPresent(Alert alert, boolean openNewVersionPopup) {
        if (alert != null) {
            if (alert.isUpdateInfo()) {
                user.setDisplayedAlert(alert);
                final boolean isNewVersion = alert.isNewVersion();
                newVersionAvailableProperty.set(isNewVersion);
                String key = "Update_" + alert.getVersion();
                if (isNewVersion && (preferences.showAgain(key) || openNewVersionPopup)) {
                    new DisplayUpdateDownloadWindow(alert)
                            .actionButtonText(Res.get("displayUpdateDownloadWindow.button.downloadLater"))
                            .onAction(() -> {
                                preferences.dontShowAgain(key, false); // update later
                            })
                            .closeButtonText(Res.get("shared.cancel"))
                            .onClose(() -> {
                                preferences.dontShowAgain(key, true); // ignore update
                            })
                            .show();
                }
            } else {
                final Alert displayedAlert = user.getDisplayedAlert();
                if (displayedAlert == null || !displayedAlert.equals(alert))
                    new DisplayAlertMessageWindow()
                            .alertMessage(alert)
                            .onClose(() -> {
                                user.setDisplayedAlert(alert);
                            })
                            .show();
            }
        }
    }

    private String getBtcNetworkAsString() {
        String postFix;
        if (bisqEnvironment.isBitcoinLocalhostNodeRunning())
            postFix = " " + Res.get("mainView.footer.localhostBitcoinNode");
        else if (preferences.getUseTorForBitcoinJ())
            postFix = " " + Res.get("mainView.footer.usingTor");
        else
            postFix = "";
        return Res.get(BisqEnvironment.getBaseCurrencyNetwork().name()) + postFix;
    }
}
