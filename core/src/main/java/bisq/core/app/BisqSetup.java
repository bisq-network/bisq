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

package bisq.core.app;

import bisq.core.account.sign.SignedWitness;
import bisq.core.account.sign.SignedWitnessService;
import bisq.core.account.witness.AccountAgeWitnessService;
import bisq.core.alert.Alert;
import bisq.core.alert.AlertManager;
import bisq.core.alert.PrivateNotificationManager;
import bisq.core.alert.PrivateNotificationPayload;
import bisq.core.btc.Balances;
import bisq.core.btc.model.AddressEntry;
import bisq.core.btc.setup.WalletsSetup;
import bisq.core.btc.wallet.BtcWalletService;
import bisq.core.btc.wallet.WalletsManager;
import bisq.core.dao.DaoSetup;
import bisq.core.dao.governance.asset.AssetService;
import bisq.core.dao.governance.voteresult.VoteResultException;
import bisq.core.dao.governance.voteresult.VoteResultService;
import bisq.core.dao.state.unconfirmed.UnconfirmedBsqChangeOutputListService;
import bisq.core.filter.FilterManager;
import bisq.core.locale.Res;
import bisq.core.notifications.MobileNotificationService;
import bisq.core.notifications.alerts.DisputeMsgEvents;
import bisq.core.notifications.alerts.MyOfferTakenEvents;
import bisq.core.notifications.alerts.TradeEvents;
import bisq.core.notifications.alerts.market.MarketAlerts;
import bisq.core.notifications.alerts.price.PriceAlert;
import bisq.core.offer.OpenOfferManager;
import bisq.core.payment.PaymentAccount;
import bisq.core.payment.TradeLimits;
import bisq.core.payment.payload.PaymentMethod;
import bisq.core.provider.fee.FeeService;
import bisq.core.provider.price.PriceFeedService;
import bisq.core.support.dispute.arbitration.ArbitrationManager;
import bisq.core.support.dispute.arbitration.arbitrator.ArbitratorManager;
import bisq.core.support.dispute.mediation.MediationManager;
import bisq.core.support.dispute.mediation.mediator.MediatorManager;
import bisq.core.support.dispute.refund.RefundManager;
import bisq.core.support.dispute.refund.refundagent.RefundAgentManager;
import bisq.core.support.traderchat.TraderChatManager;
import bisq.core.trade.TradeManager;
import bisq.core.trade.TradeTxException;
import bisq.core.trade.statistics.AssetTradeActivityCheck;
import bisq.core.trade.statistics.TradeStatisticsManager;
import bisq.core.user.Preferences;
import bisq.core.user.User;
import bisq.core.util.FormattingUtils;
import bisq.core.util.coin.CoinFormatter;

import bisq.network.crypto.DecryptedDataTuple;
import bisq.network.crypto.EncryptionService;
import bisq.network.p2p.P2PService;
import bisq.network.p2p.peers.keepalive.messages.Ping;
import bisq.network.p2p.storage.payload.PersistableNetworkPayload;

import bisq.common.ClockWatcher;
import bisq.common.Timer;
import bisq.common.UserThread;
import bisq.common.app.DevEnv;
import bisq.common.app.Log;
import bisq.common.crypto.CryptoException;
import bisq.common.crypto.KeyRing;
import bisq.common.crypto.SealedAndSigned;
import bisq.common.proto.ProtobufferException;
import bisq.common.util.Utilities;

import org.bitcoinj.core.Coin;
import org.bitcoinj.core.RejectMessage;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;

import com.google.common.net.InetAddresses;

import org.fxmisc.easybind.EasyBind;
import org.fxmisc.easybind.monadic.MonadicBinding;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.StringProperty;
import javafx.beans.value.ChangeListener;

import javafx.collections.ListChangeListener;
import javafx.collections.SetChangeListener;

import org.spongycastle.crypto.params.KeyParameter;

import java.net.InetSocketAddress;
import java.net.Socket;

import java.io.IOException;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import ch.qos.logback.classic.Level;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import javax.annotation.Nullable;

@Slf4j
@Singleton
public class BisqSetup {
    public interface BisqSetupListener {
        default void onInitP2pNetwork() {
            log.info("onInitP2pNetwork");
        }

        default void onInitWallet() {
            log.info("onInitWallet");
        }

        default void onRequestWalletPassword() {
            log.info("onRequestWalletPassword");
        }

        void onSetupComplete();
    }

    private static final long STARTUP_TIMEOUT_MINUTES = 4;

    private final P2PNetworkSetup p2PNetworkSetup;
    private final WalletAppSetup walletAppSetup;
    private final WalletsManager walletsManager;
    private final WalletsSetup walletsSetup;
    private final BtcWalletService btcWalletService;
    private final Balances balances;
    private final PriceFeedService priceFeedService;
    private final ArbitratorManager arbitratorManager;
    private final MediatorManager mediatorManager;
    private final RefundAgentManager refundAgentManager;
    private final P2PService p2PService;
    private final TradeManager tradeManager;
    private final OpenOfferManager openOfferManager;
    private final ArbitrationManager arbitrationManager;
    private final MediationManager mediationManager;
    private final RefundManager refundManager;
    private final TraderChatManager traderChatManager;
    private final Preferences preferences;
    private final User user;
    private final AlertManager alertManager;
    private final PrivateNotificationManager privateNotificationManager;
    private final FilterManager filterManager;
    private final TradeStatisticsManager tradeStatisticsManager;
    private final ClockWatcher clockWatcher;
    private final FeeService feeService;
    private final DaoSetup daoSetup;
    private final UnconfirmedBsqChangeOutputListService unconfirmedBsqChangeOutputListService;
    private final EncryptionService encryptionService;
    private final KeyRing keyRing;
    private final BisqEnvironment bisqEnvironment;
    private final AccountAgeWitnessService accountAgeWitnessService;
    private final SignedWitnessService signedWitnessService;
    private final MobileNotificationService mobileNotificationService;
    private final MyOfferTakenEvents myOfferTakenEvents;
    private final TradeEvents tradeEvents;
    private final DisputeMsgEvents disputeMsgEvents;
    private final PriceAlert priceAlert;
    private final MarketAlerts marketAlerts;
    private final VoteResultService voteResultService;
    private final AssetTradeActivityCheck tradeActivityCheck;
    private final AssetService assetService;
    private final TorSetup torSetup;
    private final TradeLimits tradeLimits;
    private final CoinFormatter formatter;
    @Setter
    @Nullable
    private Consumer<Runnable> displayTacHandler;
    @Setter
    @Nullable
    private Consumer<String> cryptoSetupFailedHandler, chainFileLockedExceptionHandler,
            spvFileCorruptedHandler, lockedUpFundsHandler, daoErrorMessageHandler, daoWarnMessageHandler,
            filterWarningHandler, displaySecurityRecommendationHandler, displayLocalhostHandler,
            wrongOSArchitectureHandler, displaySignedByArbitratorHandler,
            displaySignedByPeerHandler, displayPeerLimitLiftedHandler, displayPeerSignerHandler,
            rejectedTxErrorMessageHandler;
    @Setter
    @Nullable
    private Consumer<Boolean> displayTorNetworkSettingsHandler;
    @Setter
    @Nullable
    private Runnable showFirstPopupIfResyncSPVRequestedHandler;
    @Setter
    @Nullable
    private Consumer<Consumer<KeyParameter>> requestWalletPasswordHandler;
    @Setter
    @Nullable
    private Consumer<Alert> displayAlertHandler;
    @Setter
    @Nullable
    private BiConsumer<Alert, String> displayUpdateHandler;
    @Setter
    @Nullable
    private Consumer<VoteResultException> voteResultExceptionHandler;
    @Setter
    @Nullable
    private Consumer<PrivateNotificationPayload> displayPrivateNotificationHandler;

    @Getter
    final BooleanProperty newVersionAvailableProperty = new SimpleBooleanProperty(false);
    private BooleanProperty p2pNetworkReady;
    private final BooleanProperty walletInitialized = new SimpleBooleanProperty();
    private boolean allBasicServicesInitialized;
    @SuppressWarnings("FieldCanBeLocal")
    private MonadicBinding<Boolean> p2pNetworkAndWalletInitialized;
    private List<BisqSetupListener> bisqSetupListeners = new ArrayList<>();

    @Inject
    public BisqSetup(P2PNetworkSetup p2PNetworkSetup,
                     WalletAppSetup walletAppSetup,
                     WalletsManager walletsManager,
                     WalletsSetup walletsSetup,
                     BtcWalletService btcWalletService,
                     Balances balances,
                     PriceFeedService priceFeedService,
                     ArbitratorManager arbitratorManager,
                     MediatorManager mediatorManager,
                     RefundAgentManager refundAgentManager,
                     P2PService p2PService,
                     TradeManager tradeManager,
                     OpenOfferManager openOfferManager,
                     ArbitrationManager arbitrationManager,
                     MediationManager mediationManager,
                     RefundManager refundManager,
                     TraderChatManager traderChatManager,
                     Preferences preferences,
                     User user,
                     AlertManager alertManager,
                     PrivateNotificationManager privateNotificationManager,
                     FilterManager filterManager,
                     TradeStatisticsManager tradeStatisticsManager,
                     ClockWatcher clockWatcher,
                     FeeService feeService,
                     DaoSetup daoSetup,
                     UnconfirmedBsqChangeOutputListService unconfirmedBsqChangeOutputListService,
                     EncryptionService encryptionService,
                     KeyRing keyRing,
                     BisqEnvironment bisqEnvironment,
                     AccountAgeWitnessService accountAgeWitnessService,
                     SignedWitnessService signedWitnessService,
                     MobileNotificationService mobileNotificationService,
                     MyOfferTakenEvents myOfferTakenEvents,
                     TradeEvents tradeEvents,
                     DisputeMsgEvents disputeMsgEvents,
                     PriceAlert priceAlert,
                     MarketAlerts marketAlerts,
                     VoteResultService voteResultService,
                     AssetTradeActivityCheck tradeActivityCheck,
                     AssetService assetService,
                     TorSetup torSetup,
                     TradeLimits tradeLimits,
                     @Named(FormattingUtils.BTC_FORMATTER_KEY) CoinFormatter formatter) {


        this.p2PNetworkSetup = p2PNetworkSetup;
        this.walletAppSetup = walletAppSetup;

        this.walletsManager = walletsManager;
        this.walletsSetup = walletsSetup;
        this.btcWalletService = btcWalletService;
        this.balances = balances;
        this.priceFeedService = priceFeedService;
        this.arbitratorManager = arbitratorManager;
        this.mediatorManager = mediatorManager;
        this.refundAgentManager = refundAgentManager;
        this.p2PService = p2PService;
        this.tradeManager = tradeManager;
        this.openOfferManager = openOfferManager;
        this.arbitrationManager = arbitrationManager;
        this.mediationManager = mediationManager;
        this.refundManager = refundManager;
        this.traderChatManager = traderChatManager;
        this.preferences = preferences;
        this.user = user;
        this.alertManager = alertManager;
        this.privateNotificationManager = privateNotificationManager;
        this.filterManager = filterManager;
        this.tradeStatisticsManager = tradeStatisticsManager;
        this.clockWatcher = clockWatcher;
        this.feeService = feeService;
        this.daoSetup = daoSetup;
        this.unconfirmedBsqChangeOutputListService = unconfirmedBsqChangeOutputListService;
        this.encryptionService = encryptionService;
        this.keyRing = keyRing;
        this.bisqEnvironment = bisqEnvironment;
        this.accountAgeWitnessService = accountAgeWitnessService;
        this.signedWitnessService = signedWitnessService;
        this.mobileNotificationService = mobileNotificationService;
        this.myOfferTakenEvents = myOfferTakenEvents;
        this.tradeEvents = tradeEvents;
        this.disputeMsgEvents = disputeMsgEvents;
        this.priceAlert = priceAlert;
        this.marketAlerts = marketAlerts;
        this.voteResultService = voteResultService;
        this.tradeActivityCheck = tradeActivityCheck;
        this.assetService = assetService;
        this.torSetup = torSetup;
        this.tradeLimits = tradeLimits;
        this.formatter = formatter;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Setup
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void addBisqSetupListener(BisqSetupListener listener) {
        bisqSetupListeners.add(listener);
    }

    public void start() {
        UserThread.runPeriodically(() -> {
        }, 1);
        maybeReSyncSPVChain();
        maybeShowTac();
    }

    private void step2() {
        checkIfLocalHostNodeIsRunning();
    }

    private void step3() {
        torSetup.cleanupTorFiles();
        readMapsFromResources();
        checkCryptoSetup();
        checkForCorrectOSArchitecture();
    }

    private void step4() {
        startP2pNetworkAndWallet();
    }

    private void step5() {
        initDomainServices();

        bisqSetupListeners.forEach(BisqSetupListener::onSetupComplete);

        // We set that after calling the setupCompleteHandler to not trigger a popup from the dev dummy accounts
        // in MainViewModel
        maybeShowSecurityRecommendation();
        maybeShowLocalhostRunningInfo();
        maybeShowAccountSigningStateInfo();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // API
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void displayAlertIfPresent(Alert alert, boolean openNewVersionPopup) {
        if (alert != null) {
            if (alert.isUpdateInfo()) {
                user.setDisplayedAlert(alert);
                final boolean isNewVersion = alert.isNewVersion();
                newVersionAvailableProperty.set(isNewVersion);
                String key = "Update_" + alert.getVersion();
                if (isNewVersion && (preferences.showAgain(key) || openNewVersionPopup) && displayUpdateHandler != null) {
                    displayUpdateHandler.accept(alert, key);
                }
            } else {
                final Alert displayedAlert = user.getDisplayedAlert();
                if ((displayedAlert == null || !displayedAlert.equals(alert)) && displayAlertHandler != null)
                    displayAlertHandler.accept(alert);
            }
        }
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Getters
    ///////////////////////////////////////////////////////////////////////////////////////////

    // Wallet
    public StringProperty getBtcInfo() {
        return walletAppSetup.getBtcInfo();
    }

    public DoubleProperty getBtcSyncProgress() {
        return walletAppSetup.getBtcSyncProgress();
    }

    public StringProperty getWalletServiceErrorMsg() {
        return walletAppSetup.getWalletServiceErrorMsg();
    }

    public StringProperty getBtcSplashSyncIconId() {
        return walletAppSetup.getBtcSplashSyncIconId();
    }

    public BooleanProperty getUseTorForBTC() {
        return walletAppSetup.getUseTorForBTC();
    }

    // P2P
    public StringProperty getP2PNetworkInfo() {
        return p2PNetworkSetup.getP2PNetworkInfo();
    }

    public BooleanProperty getSplashP2PNetworkAnimationVisible() {
        return p2PNetworkSetup.getSplashP2PNetworkAnimationVisible();
    }

    public StringProperty getP2pNetworkWarnMsg() {
        return p2PNetworkSetup.getP2pNetworkWarnMsg();
    }

    public StringProperty getP2PNetworkIconId() {
        return p2PNetworkSetup.getP2PNetworkIconId();
    }

    public BooleanProperty getUpdatedDataReceived() {
        return p2PNetworkSetup.getUpdatedDataReceived();
    }

    public StringProperty getP2pNetworkLabelId() {
        return p2PNetworkSetup.getP2pNetworkLabelId();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Private
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void maybeReSyncSPVChain() {
        // We do the delete of the spv file at startup before BitcoinJ is initialized to avoid issues with locked files under Windows.
        if (preferences.isResyncSpvRequested()) {
            try {
                walletsSetup.reSyncSPVChain();

                // In case we had an unconfirmed change output we reset the unconfirmedBsqChangeOutputList so that
                // after a SPV resync we do not have any dangling BSQ utxos in that list which would cause an incorrect
                // BSQ balance state after the SPV resync.
                unconfirmedBsqChangeOutputListService.onSpvResync();
            } catch (IOException e) {
                log.error(e.toString());
                e.printStackTrace();
            }
        }
    }

    private void maybeShowTac() {
        if (!preferences.isTacAcceptedV120() && !DevEnv.isDevMode()) {
            if (displayTacHandler != null)
                displayTacHandler.accept(() -> {
                    preferences.setTacAcceptedV120(true);
                    step2();
                });
        } else {
            step2();
        }
    }

    private void checkIfLocalHostNodeIsRunning() {
        // For DAO testnet we ignore local btc node
        if (BisqEnvironment.getBaseCurrencyNetwork().isDaoRegTest() ||
                BisqEnvironment.getBaseCurrencyNetwork().isDaoTestNet() ||
                bisqEnvironment.isIgnoreLocalBtcNode()) {
            step3();
        } else {
            new Thread(() -> {
                try (Socket socket = new Socket()) {
                    socket.connect(new InetSocketAddress(InetAddresses.forString("127.0.0.1"),
                            BisqEnvironment.getBaseCurrencyNetwork().getParameters().getPort()), 5000);
                    log.info("Localhost Bitcoin node detected.");
                    UserThread.execute(() -> {
                        bisqEnvironment.setBitcoinLocalhostNodeRunning(true);
                        step3();
                    });
                } catch (Throwable e) {
                    UserThread.execute(BisqSetup.this::step3);
                }
            }, "checkIfLocalHostNodeIsRunningThread").start();
        }
    }

    private void readMapsFromResources() {
        SetupUtils.readFromResources(p2PService.getP2PDataStorage()).addListener((observable, oldValue, newValue) -> {
            if (newValue)
                step4();
        });
    }

    private void checkCryptoSetup() {
        // We want to test if the client is compiled with the correct crypto provider (BountyCastle)
        // and if the unlimited Strength for cryptographic keys is set.
        // If users compile themselves they might miss that step and then would get an exception in the trade.
        // To avoid that we add a sample encryption and signing here at startup to see if it doesn't cause an exception.
        // See: https://github.com/bisq-network/exchange/blob/master/doc/build.md#7-enable-unlimited-strength-for-cryptographic-keys
        new Thread(() -> {
            try {
                // just use any simple dummy msg
                Ping payload = new Ping(1, 1);
                SealedAndSigned sealedAndSigned = EncryptionService.encryptHybridWithSignature(payload,
                        keyRing.getSignatureKeyPair(), keyRing.getPubKeyRing().getEncryptionPubKey());
                DecryptedDataTuple tuple = encryptionService.decryptHybridWithSignature(sealedAndSigned, keyRing.getEncryptionKeyPair().getPrivate());
                if (tuple.getNetworkEnvelope() instanceof Ping &&
                        ((Ping) tuple.getNetworkEnvelope()).getNonce() == payload.getNonce() &&
                        ((Ping) tuple.getNetworkEnvelope()).getLastRoundTripTime() == payload.getLastRoundTripTime()) {
                    log.debug("Crypto test succeeded");
                } else {
                    throw new CryptoException("Payload not correct after decryption");
                }
            } catch (CryptoException | ProtobufferException e) {
                e.printStackTrace();
                String msg = Res.get("popup.warning.cryptoTestFailed", e.getMessage());
                log.error(msg);
                if (cryptoSetupFailedHandler != null)
                    cryptoSetupFailedHandler.accept(msg);
            }
        }, "checkCryptoThread").start();
    }

    private void startP2pNetworkAndWallet() {
        ChangeListener<Boolean> walletInitializedListener = (observable, oldValue, newValue) -> {
            // TODO that seems to be called too often if Tor takes longer to start up...
            if (newValue && !p2pNetworkReady.get() && displayTorNetworkSettingsHandler != null)
                displayTorNetworkSettingsHandler.accept(true);
        };

        Timer startupTimeout = UserThread.runAfter(() -> {
            if (p2PNetworkSetup.p2pNetworkFailed.get()) {
                // Skip this timeout action if the p2p network setup failed
                // since a p2p network error prompt will be shown containing the error message
                return;
            }
            log.warn("startupTimeout called");
            if (walletsManager.areWalletsEncrypted())
                walletInitialized.addListener(walletInitializedListener);
            else if (displayTorNetworkSettingsHandler != null)
                displayTorNetworkSettingsHandler.accept(true);

            log.info("Set log level for org.berndpruenster.netlayer classes to DEBUG to show more details for " +
                    "Tor network connection issues");
            Log.setCustomLogLevel("org.berndpruenster.netlayer", Level.DEBUG);

        }, STARTUP_TIMEOUT_MINUTES, TimeUnit.MINUTES);

        bisqSetupListeners.forEach(BisqSetupListener::onInitP2pNetwork);
        p2pNetworkReady = p2PNetworkSetup.init(this::initWallet, displayTorNetworkSettingsHandler);

        // We only init wallet service here if not using Tor for bitcoinj.
        // When using Tor, wallet init must be deferred until Tor is ready.
        if (!preferences.getUseTorForBitcoinJ() || bisqEnvironment.isBitcoinLocalhostNodeRunning()) {
            initWallet();
        }

        // need to store it to not get garbage collected
        p2pNetworkAndWalletInitialized = EasyBind.combine(walletInitialized, p2pNetworkReady,
                (a, b) -> {
                    log.info("walletInitialized={}, p2pNetWorkReady={}", a, b);
                    return a && b;
                });
        p2pNetworkAndWalletInitialized.subscribe((observable, oldValue, newValue) -> {
            if (newValue) {
                startupTimeout.stop();
                walletInitialized.removeListener(walletInitializedListener);
                if (displayTorNetworkSettingsHandler != null)
                    displayTorNetworkSettingsHandler.accept(false);
                step5();
            }
        });
    }

    private void initWallet() {
        bisqSetupListeners.forEach(BisqSetupListener::onInitWallet);
        Runnable walletPasswordHandler = () -> {
            log.info("Wallet password required");
            bisqSetupListeners.forEach(BisqSetupListener::onRequestWalletPassword);
            if (p2pNetworkReady.get())
                p2PNetworkSetup.setSplashP2PNetworkAnimationVisible(true);

            if (requestWalletPasswordHandler != null) {
                requestWalletPasswordHandler.accept(aesKey -> {
                    walletsManager.setAesKey(aesKey);
                    if (preferences.isResyncSpvRequested()) {
                        if (showFirstPopupIfResyncSPVRequestedHandler != null)
                            showFirstPopupIfResyncSPVRequestedHandler.run();
                    } else {
                        // TODO no guarantee here that the wallet is really fully initialized
                        // We would need a new walletInitializedButNotEncrypted state to track
                        // Usually init is fast and we have our wallet initialized at that state though.
                        walletInitialized.set(true);
                    }
                });
            }
        };
        walletAppSetup.init(chainFileLockedExceptionHandler,
                spvFileCorruptedHandler,
                showFirstPopupIfResyncSPVRequestedHandler,
                walletPasswordHandler,
                () -> {
                    if (allBasicServicesInitialized) {
                        checkForLockedUpFunds();
                        checkForInvalidMakerFeeTxs();
                    }
                },
                () -> walletInitialized.set(true));
    }


    private void checkForLockedUpFunds() {
        // We check if there are locked up funds in failed or closed trades
        try {
            Set<String> setOfAllTradeIds = tradeManager.getSetOfFailedOrClosedTradeIdsFromLockedInFunds();
            btcWalletService.getAddressEntriesForTrade().stream()
                    .filter(e -> setOfAllTradeIds.contains(e.getOfferId()) &&
                            e.getContext() == AddressEntry.Context.MULTI_SIG)
                    .forEach(e -> {
                        Coin balance = e.getCoinLockedInMultiSig();
                        if (balance.isPositive()) {
                            String message = Res.get("popup.warning.lockedUpFunds",
                                    formatter.formatCoinWithCode(balance), e.getAddressString(), e.getOfferId());
                            log.warn(message);
                            if (lockedUpFundsHandler != null) {
                                lockedUpFundsHandler.accept(message);
                            }
                        }
                    });
        } catch (TradeTxException e) {
            log.warn(e.getMessage());
            if (lockedUpFundsHandler != null) {
                lockedUpFundsHandler.accept(e.getMessage());
            }
        }
    }

    private void checkForInvalidMakerFeeTxs() {
        // We check if we have open offers with no confidence object at the maker fee tx. That can happen if the
        // miner fee was too low and the transaction got removed from mempool and got out from our wallet after a
        // resync.
        openOfferManager.getObservableList().forEach(e -> {
            String offerFeePaymentTxId = e.getOffer().getOfferFeePaymentTxId();
            if (btcWalletService.getConfidenceForTxId(offerFeePaymentTxId) == null) {
                String message = Res.get("popup.warning.openOfferWithInvalidMakerFeeTx",
                        e.getOffer().getShortId(), offerFeePaymentTxId);
                log.warn(message);
                if (lockedUpFundsHandler != null) {
                    lockedUpFundsHandler.accept(message);
                }
            }
        });
    }

    private void checkForCorrectOSArchitecture() {
        if (!Utilities.isCorrectOSArchitecture() && wrongOSArchitectureHandler != null) {
            String osArchitecture = Utilities.getOSArchitecture();
            // We don't force a shutdown as the osArchitecture might in strange cases return a wrong value.
            // Needs at least more testing on different machines...
            wrongOSArchitectureHandler.accept(Res.get("popup.warning.wrongVersion",
                    osArchitecture,
                    Utilities.getJVMArchitecture(),
                    osArchitecture));
        }
    }

    private void initDomainServices() {
        log.info("initDomainServices");

        clockWatcher.start();

        tradeLimits.onAllServicesInitialized();

        arbitrationManager.onAllServicesInitialized();
        mediationManager.onAllServicesInitialized();
        refundManager.onAllServicesInitialized();
        traderChatManager.onAllServicesInitialized();

        tradeManager.onAllServicesInitialized();

        if (walletsSetup.downloadPercentageProperty().get() == 1) {
            checkForLockedUpFunds();
            checkForInvalidMakerFeeTxs();
        }

        openOfferManager.onAllServicesInitialized();

        balances.onAllServicesInitialized();

        walletAppSetup.getRejectedTxException().addListener((observable, oldValue, newValue) -> {
            if (newValue == null || newValue.getTxId() == null) {
                return;
            }

            RejectMessage rejectMessage = newValue.getRejectMessage();
            log.warn("We received reject message: {}", rejectMessage);

            // TODO: Find out which reject messages are critical and which not.
            // We got a report where a "tx already known" message caused a failed trade but the deposit tx was valid.
            // To avoid such false positives we only handle reject messages which we consider clearly critical.

            switch (rejectMessage.getReasonCode()) {
                case OBSOLETE:
                case DUPLICATE:
                case NONSTANDARD:
                case CHECKPOINT:
                case OTHER:
                    // We ignore those cases to avoid that not critical reject messages trigger a failed trade.
                    log.warn("We ignore that reject message as it is likely not critical.");
                    break;
                case MALFORMED:
                case INVALID:
                case DUST:
                case INSUFFICIENTFEE:
                    // We delay as we might get the rejected tx error before we have completed the create offer protocol
                    log.warn("We handle that reject message as it is likely critical.");
                    UserThread.runAfter(() -> {
                        String txId = newValue.getTxId();
                        openOfferManager.getObservableList().stream()
                                .filter(openOffer -> txId.equals(openOffer.getOffer().getOfferFeePaymentTxId()))
                                .forEach(openOffer -> {
                                    // We delay to avoid concurrent modification exceptions
                                    UserThread.runAfter(() -> {
                                        openOffer.getOffer().setErrorMessage(newValue.getMessage());
                                        if (rejectedTxErrorMessageHandler != null) {
                                            rejectedTxErrorMessageHandler.accept(Res.get("popup.warning.openOffer.makerFeeTxRejected", openOffer.getId(), txId));
                                        }
                                        openOfferManager.removeOpenOffer(openOffer, () -> {
                                            log.warn("We removed an open offer because the maker fee was rejected by the Bitcoin " +
                                                    "network. OfferId={}, txId={}", openOffer.getShortId(), txId);
                                        }, log::warn);
                                    }, 1);
                                });

                        tradeManager.getTradableList().stream()
                                .filter(trade -> trade.getOffer() != null)
                                .forEach(trade -> {
                                    String details = null;
                                    if (txId.equals(trade.getDepositTxId())) {
                                        details = Res.get("popup.warning.trade.txRejected.deposit");
                                    }
                                    if (txId.equals(trade.getOffer().getOfferFeePaymentTxId()) || txId.equals(trade.getTakerFeeTxId())) {
                                        details = Res.get("popup.warning.trade.txRejected.tradeFee");
                                    }

                                    if (details != null) {
                                        // We delay to avoid concurrent modification exceptions
                                        String finalDetails = details;
                                        UserThread.runAfter(() -> {
                                            trade.setErrorMessage(newValue.getMessage());
                                            if (rejectedTxErrorMessageHandler != null) {
                                                rejectedTxErrorMessageHandler.accept(Res.get("popup.warning.trade.txRejected",
                                                        finalDetails, trade.getShortId(), txId));
                                            }
                                            tradeManager.addTradeToFailedTrades(trade);
                                        }, 1);
                                    }
                                });
                    }, 3);
            }
        });


        arbitratorManager.onAllServicesInitialized();
        mediatorManager.onAllServicesInitialized();
        refundAgentManager.onAllServicesInitialized();

        alertManager.alertMessageProperty().addListener((observable, oldValue, newValue) ->
                displayAlertIfPresent(newValue, false));
        displayAlertIfPresent(alertManager.alertMessageProperty().get(), false);

        privateNotificationManager.privateNotificationProperty().addListener((observable, oldValue, newValue) -> {
            if (displayPrivateNotificationHandler != null)
                displayPrivateNotificationHandler.accept(newValue);
        });

        p2PService.onAllServicesInitialized();

        feeService.onAllServicesInitialized();

        if (DevEnv.isDaoActivated()) {
            daoSetup.onAllServicesInitialized(errorMessage -> {
                if (daoErrorMessageHandler != null)
                    daoErrorMessageHandler.accept(errorMessage);
            }, warningMessage -> {
                if (daoWarnMessageHandler != null)
                    daoWarnMessageHandler.accept(warningMessage);
            });
        }

        tradeStatisticsManager.onAllServicesInitialized();
        tradeActivityCheck.onAllServicesInitialized();

        assetService.onAllServicesInitialized();

        accountAgeWitnessService.onAllServicesInitialized();
        signedWitnessService.onAllServicesInitialized();

        priceFeedService.setCurrencyCodeOnInit();

        filterManager.onAllServicesInitialized();
        filterManager.addListener(filter -> {
            if (filter != null && filterWarningHandler != null) {
                if (filter.getSeedNodes() != null && !filter.getSeedNodes().isEmpty()) {
                    log.warn(Res.get("popup.warning.nodeBanned", Res.get("popup.warning.seed")));
                    // Let's keep that more silent. Might be used in case a node is unstable and we don't want to confuse users.
                    // filterWarningHandler.accept(Res.get("popup.warning.nodeBanned", Res.get("popup.warning.seed")));
                }

                if (filter.getPriceRelayNodes() != null && !filter.getPriceRelayNodes().isEmpty()) {
                    log.warn(Res.get("popup.warning.nodeBanned", Res.get("popup.warning.priceRelay")));
                    // Let's keep that more silent. Might be used in case a node is unstable and we don't want to confuse users.
                    // filterWarningHandler.accept(Res.get("popup.warning.nodeBanned", Res.get("popup.warning.priceRelay")));
                }

                if (filterManager.requireUpdateToNewVersionForTrading()) {
                    filterWarningHandler.accept(Res.get("popup.warning.mandatoryUpdate.trading"));
                }

                if (filterManager.requireUpdateToNewVersionForDAO()) {
                    filterWarningHandler.accept(Res.get("popup.warning.mandatoryUpdate.dao"));
                }
                if (filter.isDisableDao()) {
                    filterWarningHandler.accept(Res.get("popup.warning.disable.dao"));
                }
            }
        });

        voteResultService.getVoteResultExceptions().addListener((ListChangeListener<VoteResultException>) c -> {
            c.next();
            if (c.wasAdded() && voteResultExceptionHandler != null) {
                c.getAddedSubList().forEach(e -> voteResultExceptionHandler.accept(e));
            }
        });

        mobileNotificationService.onAllServicesInitialized();
        myOfferTakenEvents.onAllServicesInitialized();
        tradeEvents.onAllServicesInitialized();
        disputeMsgEvents.onAllServicesInitialized();
        priceAlert.onAllServicesInitialized();
        marketAlerts.onAllServicesInitialized();

        allBasicServicesInitialized = true;
    }

    private void maybeShowSecurityRecommendation() {
        String key = "remindPasswordAndBackup";
        user.getPaymentAccountsAsObservable().addListener((SetChangeListener<PaymentAccount>) change -> {
            if (!walletsManager.areWalletsEncrypted() && !user.isPaymentAccountImport() && preferences.showAgain(key) && change.wasAdded() &&
                    displaySecurityRecommendationHandler != null)
                displaySecurityRecommendationHandler.accept(key);
        });
    }

    private void maybeShowLocalhostRunningInfo() {
        maybeTriggerDisplayHandler("bitcoinLocalhostNode", displayLocalhostHandler, bisqEnvironment.isBitcoinLocalhostNodeRunning());
    }

    private void maybeShowAccountSigningStateInfo() {
        String keySignedByArbitrator = "accountSignedByArbitrator";
        String keySignedByPeer = "accountSignedByPeer";
        String keyPeerLimitedLifted = "accountLimitLifted";
        String keyPeerSigner = "accountPeerSigner";

        // check signed witness on startup
        checkSigningState(AccountAgeWitnessService.SignState.ARBITRATOR, keySignedByArbitrator, displaySignedByArbitratorHandler);
        checkSigningState(AccountAgeWitnessService.SignState.PEER_INITIAL, keySignedByPeer, displaySignedByPeerHandler);
        checkSigningState(AccountAgeWitnessService.SignState.PEER_LIMIT_LIFTED, keyPeerLimitedLifted, displayPeerLimitLiftedHandler);
        checkSigningState(AccountAgeWitnessService.SignState.PEER_SIGNER, keyPeerSigner, displayPeerSignerHandler);

        // check signed witness during runtime
        p2PService.getP2PDataStorage().addAppendOnlyDataStoreListener(
                payload -> {
                    maybeTriggerDisplayHandler(keySignedByArbitrator, displaySignedByArbitratorHandler,
                            isSignedWitnessOfMineWithState(payload, AccountAgeWitnessService.SignState.ARBITRATOR));
                    maybeTriggerDisplayHandler(keySignedByPeer, displaySignedByPeerHandler,
                            isSignedWitnessOfMineWithState(payload, AccountAgeWitnessService.SignState.PEER_INITIAL));
                    maybeTriggerDisplayHandler(keyPeerLimitedLifted, displayPeerLimitLiftedHandler,
                            isSignedWitnessOfMineWithState(payload, AccountAgeWitnessService.SignState.PEER_LIMIT_LIFTED));
                    maybeTriggerDisplayHandler(keyPeerSigner, displayPeerSignerHandler,
                            isSignedWitnessOfMineWithState(payload, AccountAgeWitnessService.SignState.PEER_SIGNER));
                });
    }

    private void checkSigningState(AccountAgeWitnessService.SignState state,
                                   String key, Consumer<String> displayHandler) {
        boolean signingStateFound = p2PService.getP2PDataStorage().getAppendOnlyDataStoreMap().values().stream()
                .anyMatch(payload -> isSignedWitnessOfMineWithState(payload, state));

        maybeTriggerDisplayHandler(key, displayHandler, signingStateFound);
    }

    private boolean isSignedWitnessOfMineWithState(PersistableNetworkPayload payload,
                                                   AccountAgeWitnessService.SignState state) {
        if (payload instanceof SignedWitness && user.getPaymentAccounts() != null) {
            // We know at this point that it is already added to the signed witness list
            // Check if new signed witness is for one of my own accounts
            return user.getPaymentAccounts().stream()
                    .filter(a -> PaymentMethod.hasChargebackRisk(a.getPaymentMethod(), a.getTradeCurrencies()))
                    .filter(a -> Arrays.equals(((SignedWitness) payload).getAccountAgeWitnessHash(),
                            accountAgeWitnessService.getMyWitness(a.getPaymentAccountPayload()).getHash()))
                    .anyMatch(a -> accountAgeWitnessService.getSignState(accountAgeWitnessService.getMyWitness(
                            a.getPaymentAccountPayload())).equals(state));
        }
        return false;
    }

    private void maybeTriggerDisplayHandler(String key, Consumer<String> displayHandler, boolean signingStateFound) {
        if (signingStateFound && preferences.showAgain(key) &&
                displayHandler != null) {
            displayHandler.accept(key);
        }
    }
}
