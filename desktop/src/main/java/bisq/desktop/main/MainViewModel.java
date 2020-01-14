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

import bisq.desktop.app.BisqApp;
import bisq.desktop.common.model.ViewModel;
import bisq.desktop.components.BalanceWithConfirmationTextField;
import bisq.desktop.components.TxIdTextField;
import bisq.desktop.main.overlays.Overlay;
import bisq.desktop.main.overlays.notifications.NotificationCenter;
import bisq.desktop.main.overlays.popups.Popup;
import bisq.desktop.main.overlays.windows.DisplayAlertMessageWindow;
import bisq.desktop.main.overlays.windows.NewTradeProtocolLaunchWindow;
import bisq.desktop.main.overlays.windows.TacWindow;
import bisq.desktop.main.overlays.windows.TorNetworkSettingsWindow;
import bisq.desktop.main.overlays.windows.WalletPasswordWindow;
import bisq.desktop.main.overlays.windows.downloadupdate.DisplayUpdateDownloadWindow;
import bisq.desktop.main.presentation.AccountPresentation;
import bisq.desktop.main.presentation.DaoPresentation;
import bisq.desktop.main.presentation.MarketPricePresentation;
import bisq.desktop.main.shared.PriceFeedComboBoxItem;
import bisq.desktop.util.DisplayUtils;
import bisq.desktop.util.GUIUtil;

import bisq.core.account.sign.SignedWitnessService;
import bisq.core.account.witness.AccountAgeWitnessService;
import bisq.core.alert.PrivateNotificationManager;
import bisq.core.app.AppOptionKeys;
import bisq.core.app.BisqEnvironment;
import bisq.core.app.BisqSetup;
import bisq.core.btc.setup.WalletsSetup;
import bisq.core.btc.wallet.BtcWalletService;
import bisq.core.locale.CryptoCurrency;
import bisq.core.locale.CurrencyUtil;
import bisq.core.locale.Res;
import bisq.core.payment.AliPayAccount;
import bisq.core.payment.CryptoCurrencyAccount;
import bisq.core.presentation.BalancePresentation;
import bisq.core.presentation.SupportTicketsPresentation;
import bisq.core.presentation.TradePresentation;
import bisq.core.provider.fee.FeeService;
import bisq.core.provider.price.PriceFeedService;
import bisq.core.trade.Trade;
import bisq.core.trade.TradeManager;
import bisq.core.user.DontShowAgainLookup;
import bisq.core.user.Preferences;
import bisq.core.user.User;

import bisq.network.p2p.BootstrapListener;
import bisq.network.p2p.P2PService;

import bisq.common.Timer;
import bisq.common.UserThread;
import bisq.common.app.DevEnv;
import bisq.common.storage.CorruptedDatabaseFilesHandler;

import com.google.inject.Inject;

import org.fxmisc.easybind.EasyBind;
import org.fxmisc.easybind.monadic.MonadicBinding;

import javafx.beans.binding.Bindings;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;

import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;

import java.util.Comparator;
import java.util.Date;
import java.util.Optional;
import java.util.PriorityQueue;
import java.util.Queue;
import java.util.Random;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class MainViewModel implements ViewModel, BisqSetup.BisqSetupListener {
    private final BisqSetup bisqSetup;
    private final WalletsSetup walletsSetup;
    private final User user;
    private final BalancePresentation balancePresentation;
    private final TradePresentation tradePresentation;
    private final SupportTicketsPresentation supportTicketsPresentation;
    private final MarketPricePresentation marketPricePresentation;
    private final DaoPresentation daoPresentation;
    private final AccountPresentation accountPresentation;
    private final P2PService p2PService;
    private final TradeManager tradeManager;
    @Getter
    private final Preferences preferences;
    private final PrivateNotificationManager privateNotificationManager;
    private final WalletPasswordWindow walletPasswordWindow;
    private final NotificationCenter notificationCenter;
    private final TacWindow tacWindow;
    @Getter
    private final PriceFeedService priceFeedService;
    private final BisqEnvironment bisqEnvironment;
    private final AccountAgeWitnessService accountAgeWitnessService;
    @Getter
    private final TorNetworkSettingsWindow torNetworkSettingsWindow;
    private final CorruptedDatabaseFilesHandler corruptedDatabaseFilesHandler;

    @Getter
    private BooleanProperty showAppScreen = new SimpleBooleanProperty();
    private DoubleProperty combinedSyncProgress = new SimpleDoubleProperty(-1);
    private final BooleanProperty isSplashScreenRemoved = new SimpleBooleanProperty();
    private Timer checkNumberOfBtcPeersTimer;
    private Timer checkNumberOfP2pNetworkPeersTimer;
    @SuppressWarnings("FieldCanBeLocal")
    private MonadicBinding<Boolean> tradesAndUIReady;
    private Queue<Overlay> popupQueue = new PriorityQueue<>(Comparator.comparing(Overlay::getDisplayOrderPriority));


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    public MainViewModel(BisqSetup bisqSetup,
                         WalletsSetup walletsSetup,
                         BtcWalletService btcWalletService,
                         User user,
                         BalancePresentation balancePresentation,
                         TradePresentation tradePresentation,
                         SupportTicketsPresentation supportTicketsPresentation,
                         MarketPricePresentation marketPricePresentation,
                         DaoPresentation daoPresentation,
                         AccountPresentation accountPresentation, P2PService p2PService,
                         TradeManager tradeManager,
                         Preferences preferences,
                         PrivateNotificationManager privateNotificationManager,
                         WalletPasswordWindow walletPasswordWindow,
                         NotificationCenter notificationCenter,
                         TacWindow tacWindow,
                         FeeService feeService,
                         PriceFeedService priceFeedService,
                         BisqEnvironment bisqEnvironment,
                         AccountAgeWitnessService accountAgeWitnessService,
                         TorNetworkSettingsWindow torNetworkSettingsWindow,
                         CorruptedDatabaseFilesHandler corruptedDatabaseFilesHandler) {
        this.bisqSetup = bisqSetup;
        this.walletsSetup = walletsSetup;
        this.user = user;
        this.balancePresentation = balancePresentation;
        this.tradePresentation = tradePresentation;
        this.supportTicketsPresentation = supportTicketsPresentation;
        this.marketPricePresentation = marketPricePresentation;
        this.daoPresentation = daoPresentation;
        this.accountPresentation = accountPresentation;
        this.p2PService = p2PService;
        this.tradeManager = tradeManager;
        this.preferences = preferences;
        this.privateNotificationManager = privateNotificationManager;
        this.walletPasswordWindow = walletPasswordWindow;
        this.notificationCenter = notificationCenter;
        this.tacWindow = tacWindow;
        this.priceFeedService = priceFeedService;
        this.bisqEnvironment = bisqEnvironment;
        this.accountAgeWitnessService = accountAgeWitnessService;
        this.torNetworkSettingsWindow = torNetworkSettingsWindow;
        this.corruptedDatabaseFilesHandler = corruptedDatabaseFilesHandler;

        TxIdTextField.setPreferences(preferences);

        TxIdTextField.setWalletService(btcWalletService);
        BalanceWithConfirmationTextField.setWalletService(btcWalletService);

        GUIUtil.setFeeService(feeService);
        GUIUtil.setPreferences(preferences);

        setupHandlers();
        bisqSetup.addBisqSetupListener(this);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // BisqSetupListener
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void onSetupComplete() {
        // We handle the trade period here as we display a global popup if we reached dispute time
        tradesAndUIReady = EasyBind.combine(isSplashScreenRemoved, tradeManager.pendingTradesInitializedProperty(), (a, b) -> a && b);
        tradesAndUIReady.subscribe((observable, oldValue, newValue) -> {
            if (newValue) {
                tradeManager.applyTradePeriodState();

                tradeManager.getTradableList().forEach(trade -> {
                    Date maxTradePeriodDate = trade.getMaxTradePeriodDate();
                    String key;
                    switch (trade.getTradePeriodState()) {
                        case FIRST_HALF:
                            break;
                        case SECOND_HALF:
                            key = "displayHalfTradePeriodOver" + trade.getId();
                            if (DontShowAgainLookup.showAgain(key)) {
                                DontShowAgainLookup.dontShowAgain(key, true);
                                new Popup().warning(Res.get("popup.warning.tradePeriod.halfReached",
                                        trade.getShortId(),
                                        DisplayUtils.formatDateTime(maxTradePeriodDate)))
                                        .show();
                            }
                            break;
                        case TRADE_PERIOD_OVER:
                            key = "displayTradePeriodOver" + trade.getId();
                            if (DontShowAgainLookup.showAgain(key)) {
                                DontShowAgainLookup.dontShowAgain(key, true);
                                new Popup().warning(Res.get("popup.warning.tradePeriod.ended",
                                        trade.getShortId(),
                                        DisplayUtils.formatDateTime(maxTradePeriodDate)))
                                        .show();
                            }
                            break;
                    }
                });
            }
        });

        setupP2PNumPeersWatcher();
        setupBtcNumPeersWatcher();

        marketPricePresentation.setup();
        daoPresentation.setup();
        accountPresentation.setup();

        if (DevEnv.isDevMode()) {
            preferences.setShowOwnOffersInOfferBook(true);
            setupDevDummyPaymentAccounts();
        }

        getShowAppScreen().set(true);
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

        maybeAddNewTradeProtocolLaunchWindowToQueue();
        maybeShowPopupsFromQueue();
    }

    void onOpenDownloadWindow() {
        bisqSetup.displayAlertIfPresent(user.getDisplayedAlert(), true);
    }

    void setPriceFeedComboBoxItem(PriceFeedComboBoxItem item) {
        marketPricePresentation.setPriceFeedComboBoxItem(item);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Private
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void setupHandlers() {
        bisqSetup.setDisplayTacHandler(acceptedHandler -> UserThread.runAfter(() -> {
            //noinspection FunctionalExpressionCanBeFolded
            tacWindow.onAction(acceptedHandler::run).show();
        }, 1));

        bisqSetup.setCryptoSetupFailedHandler(msg -> UserThread.execute(() ->
                new Popup().warning(msg)
                        .useShutDownButton()
                        .useReportBugButton()
                        .show()));
        bisqSetup.setDisplayTorNetworkSettingsHandler(show -> {
            if (show)
                torNetworkSettingsWindow.show();
            else
                torNetworkSettingsWindow.hide();
        });
        bisqSetup.setSpvFileCorruptedHandler(msg -> new Popup().warning(msg)
                .actionButtonText(Res.get("settings.net.reSyncSPVChainButton"))
                .onAction(() -> GUIUtil.reSyncSPVChain(preferences))
                .show());
        bisqSetup.setVoteResultExceptionHandler(voteResultException -> log.warn(voteResultException.toString()));

        bisqSetup.setChainFileLockedExceptionHandler(msg -> new Popup().warning(msg)
                .useShutDownButton()
                .show());
        bisqSetup.setLockedUpFundsHandler(msg -> new Popup().width(850).warning(msg).show());
        bisqSetup.setShowFirstPopupIfResyncSPVRequestedHandler(this::showFirstPopupIfResyncSPVRequested);
        bisqSetup.setRequestWalletPasswordHandler(aesKeyHandler -> walletPasswordWindow
                .onAesKey(aesKeyHandler::accept)
                .onClose(() -> BisqApp.getShutDownHandler().run())
                .show());

        bisqSetup.setDisplayUpdateHandler((alert, key) -> new DisplayUpdateDownloadWindow(alert)
                .actionButtonText(Res.get("displayUpdateDownloadWindow.button.downloadLater"))
                .onAction(() -> {
                    preferences.dontShowAgain(key, false); // update later
                })
                .closeButtonText(Res.get("shared.cancel"))
                .onClose(() -> {
                    preferences.dontShowAgain(key, true); // ignore update
                })
                .show());
        bisqSetup.setDisplayAlertHandler(alert -> new DisplayAlertMessageWindow()
                .alertMessage(alert)
                .closeButtonText(Res.get("shared.close"))
                .onClose(() -> user.setDisplayedAlert(alert))
                .show());
        bisqSetup.setDisplayPrivateNotificationHandler(privateNotification ->
                new Popup().headLine(Res.get("popup.privateNotification.headline"))
                        .attention(privateNotification.getMessage())
                        .setHeadlineStyle("-fx-text-fill: -bs-error-red;  -fx-font-weight: bold;  -fx-font-size: 16;")
                        .onClose(privateNotificationManager::removePrivateNotification)
                        .useIUnderstandButton()
                        .show());
        bisqSetup.setDaoErrorMessageHandler(errorMessage -> new Popup().error(errorMessage).show());
        bisqSetup.setDaoWarnMessageHandler(warnMessage -> new Popup().warning(warnMessage).show());
        bisqSetup.setDisplaySecurityRecommendationHandler(key ->
                new Popup().headLine(Res.get("popup.securityRecommendation.headline"))
                        .information(Res.get("popup.securityRecommendation.msg"))
                        .dontShowAgainId(key)
                        .show());
        bisqSetup.setDisplayLocalhostHandler(key -> {
            if (!DevEnv.isDevMode()) {
                Overlay popup = new Popup().backgroundInfo(Res.get("popup.bitcoinLocalhostNode.msg"))
                        .dontShowAgainId(key);
                popup.setDisplayOrderPriority(5);
                popupQueue.add(popup);
            }
        });
        bisqSetup.setDisplaySignedByArbitratorHandler(key -> accountPresentation.showOneTimeAccountSigningPopup(
                key, "popup.accountSigning.signedByArbitrator"));
        bisqSetup.setDisplaySignedByPeerHandler(key -> accountPresentation.showOneTimeAccountSigningPopup(
                key, "popup.accountSigning.signedByPeer", String.valueOf(SignedWitnessService.SIGNER_AGE_DAYS)));
        bisqSetup.setDisplayPeerLimitLiftedHandler(key -> accountPresentation.showOneTimeAccountSigningPopup(
                key, "popup.accountSigning.peerLimitLifted"));
        bisqSetup.setDisplayPeerSignerHandler(key -> accountPresentation.showOneTimeAccountSigningPopup(
                key, "popup.accountSigning.peerSigner"));

        bisqSetup.setWrongOSArchitectureHandler(msg -> new Popup().warning(msg).show());

        bisqSetup.setRejectedTxErrorMessageHandler(msg -> new Popup().width(850).warning(msg).show());

        corruptedDatabaseFilesHandler.getCorruptedDatabaseFiles().ifPresent(files -> new Popup()
                .warning(Res.get("popup.warning.incompatibleDB", files.toString(),
                        bisqEnvironment.getProperty(AppOptionKeys.APP_DATA_DIR_KEY)))
                .useShutDownButton()
                .show());

        tradeManager.setTakeOfferRequestErrorMessageHandler(errorMessage -> new Popup()
                .warning(Res.get("popup.error.takeOfferRequestFailed", errorMessage))
                .show());

        tradeManager.getTradesWithoutDepositTx().addListener((ListChangeListener<Trade>) c -> {
            c.next();
            if (c.wasAdded()) {
                c.getAddedSubList().forEach(trade -> {
                    new Popup().warning(Res.get("popup.warning.trade.depositTxNull", trade.getShortId()))
                            .actionButtonText(Res.get("popup.warning.trade.depositTxNull.shutDown"))
                            .onAction(() -> BisqApp.getShutDownHandler().run())
                            .secondaryActionButtonText(Res.get("popup.warning.trade.depositTxNull.moveToFailedTrades"))
                            .onSecondaryAction(() -> tradeManager.addTradeToFailedTrades(trade))
                            .show();
                });
            }
        });

        bisqSetup.getBtcSyncProgress().addListener((observable, oldValue, newValue) -> updateBtcSyncProgress());
        daoPresentation.getBsqSyncProgress().addListener((observable, oldValue, newValue) -> updateBtcSyncProgress());

        bisqSetup.setFilterWarningHandler(warning -> new Popup().warning(warning).show());
    }

    private void setupP2PNumPeersWatcher() {
        p2PService.getNumConnectedPeers().addListener((observable, oldValue, newValue) -> {
            int numPeers = (int) newValue;
            if ((int) oldValue > 0 && numPeers == 0) {
                // give a bit of tolerance
                if (checkNumberOfP2pNetworkPeersTimer != null)
                    checkNumberOfP2pNetworkPeersTimer.stop();

                checkNumberOfP2pNetworkPeersTimer = UserThread.runAfter(() -> {
                    // check again numPeers
                    if (p2PService.getNumConnectedPeers().get() == 0) {
                        getP2pNetworkWarnMsg().set(Res.get("mainView.networkWarning.allConnectionsLost", Res.get("shared.P2P")));
                        getP2pNetworkLabelId().set("splash-error-state-msg");
                    } else {
                        getP2pNetworkWarnMsg().set(null);
                        getP2pNetworkLabelId().set("footer-pane");
                    }
                }, 5);
            } else if ((int) oldValue == 0 && numPeers > 0) {
                if (checkNumberOfP2pNetworkPeersTimer != null)
                    checkNumberOfP2pNetworkPeersTimer.stop();

                getP2pNetworkWarnMsg().set(null);
                getP2pNetworkLabelId().set("footer-pane");
            }
        });
    }

    private void setupBtcNumPeersWatcher() {
        walletsSetup.numPeersProperty().addListener((observable, oldValue, newValue) -> {
            int numPeers = (int) newValue;
            if ((int) oldValue > 0 && numPeers == 0) {
                if (checkNumberOfBtcPeersTimer != null)
                    checkNumberOfBtcPeersTimer.stop();

                checkNumberOfBtcPeersTimer = UserThread.runAfter(() -> {
                    // check again numPeers
                    if (walletsSetup.numPeersProperty().get() == 0) {
                        if (bisqEnvironment.isBitcoinLocalhostNodeRunning())
                            getWalletServiceErrorMsg().set(Res.get("mainView.networkWarning.localhostBitcoinLost", Res.getBaseCurrencyName().toLowerCase()));
                        else
                            getWalletServiceErrorMsg().set(Res.get("mainView.networkWarning.allConnectionsLost", Res.getBaseCurrencyName().toLowerCase()));
                    } else {
                        getWalletServiceErrorMsg().set(null);
                    }
                }, 5);
            } else if ((int) oldValue == 0 && numPeers > 0) {
                if (checkNumberOfBtcPeersTimer != null)
                    checkNumberOfBtcPeersTimer.stop();
                getWalletServiceErrorMsg().set(null);
            }
        });
    }

    private void showFirstPopupIfResyncSPVRequested() {
        Popup firstPopup = new Popup();
        firstPopup.information(Res.get("settings.net.reSyncSPVAfterRestart")).show();
        if (bisqSetup.getBtcSyncProgress().get() == 1) {
            showSecondPopupIfResyncSPVRequested(firstPopup);
        } else {
            bisqSetup.getBtcSyncProgress().addListener((observable, oldValue, newValue) -> {
                if ((double) newValue == 1)
                    showSecondPopupIfResyncSPVRequested(firstPopup);
            });
        }
    }

    private void showSecondPopupIfResyncSPVRequested(Popup firstPopup) {
        firstPopup.hide();
        preferences.setResyncSpvRequested(false);
        new Popup().information(Res.get("settings.net.reSyncSPVAfterRestartCompleted"))
                .hideCloseButton()
                .useShutDownButton()
                .show();
    }

    private void setupDevDummyPaymentAccounts() {
        if (user.getPaymentAccounts() != null && user.getPaymentAccounts().isEmpty()) {
            AliPayAccount aliPayAccount = new AliPayAccount();
            aliPayAccount.init();
            aliPayAccount.setAccountNr("dummy_" + new Random().nextInt(100));
            aliPayAccount.setAccountName("AliPayAccount dummy");// Don't translate only for dev
            user.addPaymentAccount(aliPayAccount);

            if (p2PService.isBootstrapped()) {
                accountAgeWitnessService.publishMyAccountAgeWitness(aliPayAccount.getPaymentAccountPayload());
            } else {
                p2PService.addP2PServiceListener(new BootstrapListener() {
                    @Override
                    public void onUpdatedDataReceived() {
                        accountAgeWitnessService.publishMyAccountAgeWitness(aliPayAccount.getPaymentAccountPayload());
                    }
                });
            }

            CryptoCurrencyAccount cryptoCurrencyAccount = new CryptoCurrencyAccount();
            cryptoCurrencyAccount.init();
            cryptoCurrencyAccount.setAccountName("ETH dummy");// Don't translate only for dev
            cryptoCurrencyAccount.setAddress("0x" + new Random().nextInt(1000000));
            Optional<CryptoCurrency> eth = CurrencyUtil.getCryptoCurrency("ETH");
            eth.ifPresent(cryptoCurrencyAccount::setSingleTradeCurrency);

            user.addPaymentAccount(cryptoCurrencyAccount);
        }
    }

    private void updateBtcSyncProgress() {
        final DoubleProperty btcSyncProgress = bisqSetup.getBtcSyncProgress();

        if (btcSyncProgress.doubleValue() < 1) {
            combinedSyncProgress.set(btcSyncProgress.doubleValue());
        } else {
            combinedSyncProgress.set(daoPresentation.getBsqSyncProgress().doubleValue());
        }
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // MainView delegate getters
    ///////////////////////////////////////////////////////////////////////////////////////////

    BooleanProperty getNewVersionAvailableProperty() {
        return bisqSetup.getNewVersionAvailableProperty();
    }

    StringProperty getNumOpenSupportTickets() {
        return supportTicketsPresentation.getNumOpenSupportTickets();
    }

    BooleanProperty getShowOpenSupportTicketsNotification() {
        return supportTicketsPresentation.getShowOpenSupportTicketsNotification();
    }

    BooleanProperty getShowPendingTradesNotification() {
        return tradePresentation.getShowPendingTradesNotification();
    }

    StringProperty getNumPendingTrades() {
        return tradePresentation.getNumPendingTrades();
    }

    StringProperty getAvailableBalance() {
        return balancePresentation.getAvailableBalance();
    }

    StringProperty getReservedBalance() {
        return balancePresentation.getReservedBalance();
    }

    StringProperty getLockedBalance() {
        return balancePresentation.getLockedBalance();
    }


    // Wallet
    StringProperty getBtcInfo() {
        final StringProperty combinedInfo = new SimpleStringProperty();
        combinedInfo.bind(Bindings.concat(bisqSetup.getBtcInfo(), " ", daoPresentation.getBsqInfo()));
        return combinedInfo;
    }

    DoubleProperty getCombinedSyncProgress() {
        return combinedSyncProgress;
    }

    StringProperty getWalletServiceErrorMsg() {
        return bisqSetup.getWalletServiceErrorMsg();
    }

    StringProperty getBtcSplashSyncIconId() {
        return bisqSetup.getBtcSplashSyncIconId();
    }

    BooleanProperty getUseTorForBTC() {
        return bisqSetup.getUseTorForBTC();
    }

    // P2P
    StringProperty getP2PNetworkInfo() {
        return bisqSetup.getP2PNetworkInfo();
    }

    BooleanProperty getSplashP2PNetworkAnimationVisible() {
        return bisqSetup.getSplashP2PNetworkAnimationVisible();
    }

    StringProperty getP2pNetworkWarnMsg() {
        return bisqSetup.getP2pNetworkWarnMsg();
    }

    StringProperty getP2PNetworkIconId() {
        return bisqSetup.getP2PNetworkIconId();
    }

    BooleanProperty getUpdatedDataReceived() {
        return bisqSetup.getUpdatedDataReceived();
    }

    StringProperty getP2pNetworkLabelId() {
        return bisqSetup.getP2pNetworkLabelId();
    }

    // marketPricePresentation
    ObjectProperty<PriceFeedComboBoxItem> getSelectedPriceFeedComboBoxItemProperty() {
        return marketPricePresentation.getSelectedPriceFeedComboBoxItemProperty();
    }

    BooleanProperty getIsFiatCurrencyPriceFeedSelected() {
        return marketPricePresentation.getIsFiatCurrencyPriceFeedSelected();
    }

    BooleanProperty getIsExternallyProvidedPrice() {
        return marketPricePresentation.getIsExternallyProvidedPrice();
    }

    BooleanProperty getIsPriceAvailable() {
        return marketPricePresentation.getIsPriceAvailable();
    }

    IntegerProperty getMarketPriceUpdated() {
        return marketPricePresentation.getMarketPriceUpdated();
    }

    StringProperty getMarketPrice() {
        return marketPricePresentation.getMarketPrice();
    }

    StringProperty getMarketPrice(String currencyCode) {
        return marketPricePresentation.getMarketPrice(currencyCode);
    }

    public ObservableList<PriceFeedComboBoxItem> getPriceFeedComboBoxItems() {
        return marketPricePresentation.getPriceFeedComboBoxItems();
    }

    public BooleanProperty getShowDaoUpdatesNotification() {
        return daoPresentation.getShowDaoUpdatesNotification();
    }

    public BooleanProperty getShowAccountUpdatesNotification() {
        return accountPresentation.getShowAccountUpdatesNotification();
    }

    private void maybeAddNewTradeProtocolLaunchWindowToQueue() {
        String newTradeProtocolWithAccountSigningLaunchPopupKey = "newTradeProtocolWithAccountSigningLaunchPopup";
        if (DontShowAgainLookup.showAgain(newTradeProtocolWithAccountSigningLaunchPopupKey)) {
            NewTradeProtocolLaunchWindow newTradeProtocolLaunchWindow = new NewTradeProtocolLaunchWindow()
                    .headLine(Res.get("popup.news.launch.headline"));
            newTradeProtocolLaunchWindow.setDisplayOrderPriority(1);
            popupQueue.add(newTradeProtocolLaunchWindow);

            DontShowAgainLookup.dontShowAgain(newTradeProtocolWithAccountSigningLaunchPopupKey, true);
        }
    }

    private void maybeShowPopupsFromQueue() {
        if (!popupQueue.isEmpty()) {
            Overlay overlay = popupQueue.poll();
            overlay.getIsHiddenProperty().addListener((observable, oldValue, newValue) -> {
                if (newValue) {
                    UserThread.runAfter(this::maybeShowPopupsFromQueue, 2);
                }
            });
            overlay.show();
        }
    }
}
