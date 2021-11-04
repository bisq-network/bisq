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
import bisq.core.account.sign.SignedWitnessStorageService;
import bisq.core.account.witness.AccountAgeWitnessService;
import bisq.core.alert.Alert;
import bisq.core.alert.AlertManager;
import bisq.core.alert.PrivateNotificationPayload;
import bisq.core.btc.model.AddressEntry;
import bisq.core.btc.nodes.LocalBitcoinNode;
import bisq.core.btc.setup.WalletsSetup;
import bisq.core.btc.wallet.BtcWalletService;
import bisq.core.btc.wallet.WalletsManager;
import bisq.core.btc.wallet.http.MemPoolSpaceTxBroadcaster;
import bisq.core.dao.governance.voteresult.VoteResultException;
import bisq.core.dao.state.unconfirmed.UnconfirmedBsqChangeOutputListService;
import bisq.core.locale.Res;
import bisq.core.offer.OpenOfferManager;
import bisq.core.payment.AmazonGiftCardAccount;
import bisq.core.payment.PaymentAccount;
import bisq.core.payment.RevolutAccount;
import bisq.core.payment.payload.PaymentMethod;
import bisq.core.support.dispute.Dispute;
import bisq.core.support.dispute.arbitration.ArbitrationManager;
import bisq.core.support.dispute.mediation.MediationManager;
import bisq.core.support.dispute.refund.RefundManager;
import bisq.core.trade.TradeManager;
import bisq.core.trade.bisq_v1.TradeTxException;
import bisq.core.user.Preferences;
import bisq.core.user.User;
import bisq.core.util.FormattingUtils;
import bisq.core.util.coin.CoinFormatter;

import bisq.network.Socks5ProxyProvider;
import bisq.network.p2p.P2PService;
import bisq.network.p2p.storage.payload.PersistableNetworkPayload;
import bisq.network.utils.Utils;

import bisq.common.Timer;
import bisq.common.UserThread;
import bisq.common.app.DevEnv;
import bisq.common.app.Log;
import bisq.common.app.Version;
import bisq.common.config.BaseCurrencyNetwork;
import bisq.common.config.Config;
import bisq.common.util.Utilities;

import org.bitcoinj.core.Coin;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;

import org.fxmisc.easybind.EasyBind;
import org.fxmisc.easybind.monadic.MonadicBinding;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.StringProperty;
import javafx.beans.value.ChangeListener;

import javafx.collections.SetChangeListener;

import org.bouncycastle.crypto.params.KeyParameter;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Scanner;
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
    private static final String VERSION_FILE_NAME = "version";
    private static final String RESYNC_SPV_FILE_NAME = "resyncSpv";

    public interface BisqSetupListener {
        default void onInitP2pNetwork() {
        }

        default void onInitWallet() {
        }

        default void onRequestWalletPassword() {
        }

        void onSetupComplete();
    }

    private static final long STARTUP_TIMEOUT_MINUTES = 4;

    private final DomainInitialisation domainInitialisation;
    private final P2PNetworkSetup p2PNetworkSetup;
    private final WalletAppSetup walletAppSetup;
    private final WalletsManager walletsManager;
    private final WalletsSetup walletsSetup;
    private final BtcWalletService btcWalletService;
    private final P2PService p2PService;
    private final SignedWitnessStorageService signedWitnessStorageService;
    private final TradeManager tradeManager;
    private final OpenOfferManager openOfferManager;
    private final Preferences preferences;
    private final User user;
    private final AlertManager alertManager;
    private final UnconfirmedBsqChangeOutputListService unconfirmedBsqChangeOutputListService;
    private final Config config;
    private final AccountAgeWitnessService accountAgeWitnessService;
    private final CoinFormatter formatter;
    private final LocalBitcoinNode localBitcoinNode;
    private final AppStartupState appStartupState;
    private final MediationManager mediationManager;
    private final RefundManager refundManager;
    private final ArbitrationManager arbitrationManager;

    @Setter
    @Nullable
    private Consumer<Runnable> displayTacHandler;
    @Setter
    @Nullable
    private Consumer<String> chainFileLockedExceptionHandler,
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
    @Setter
    @Nullable
    private Runnable showPopupIfInvalidBtcConfigHandler;
    @Setter
    @Nullable
    private Consumer<List<RevolutAccount>> revolutAccountsUpdateHandler;
    @Setter
    @Nullable
    private Consumer<List<AmazonGiftCardAccount>> amazonGiftCardAccountsUpdateHandler;
    @Setter
    @Nullable
    private Runnable qubesOSInfoHandler;
    @Setter
    @Nullable
    private Runnable daoRequiresRestartHandler;
    @Setter
    @Nullable
    private Runnable torAddressUpgradeHandler;
    @Setter
    @Nullable
    private Consumer<String> downGradePreventionHandler;

    @Getter
    final BooleanProperty newVersionAvailableProperty = new SimpleBooleanProperty(false);
    private BooleanProperty p2pNetworkReady;
    private final BooleanProperty walletInitialized = new SimpleBooleanProperty();
    private boolean allBasicServicesInitialized;
    @SuppressWarnings("FieldCanBeLocal")
    private MonadicBinding<Boolean> p2pNetworkAndWalletInitialized;
    private final List<BisqSetupListener> bisqSetupListeners = new ArrayList<>();

    @Inject
    public BisqSetup(DomainInitialisation domainInitialisation,
                     P2PNetworkSetup p2PNetworkSetup,
                     WalletAppSetup walletAppSetup,
                     WalletsManager walletsManager,
                     WalletsSetup walletsSetup,
                     BtcWalletService btcWalletService,
                     P2PService p2PService,
                     SignedWitnessStorageService signedWitnessStorageService,
                     TradeManager tradeManager,
                     OpenOfferManager openOfferManager,
                     Preferences preferences,
                     User user,
                     AlertManager alertManager,
                     UnconfirmedBsqChangeOutputListService unconfirmedBsqChangeOutputListService,
                     Config config,
                     AccountAgeWitnessService accountAgeWitnessService,
                     @Named(FormattingUtils.BTC_FORMATTER_KEY) CoinFormatter formatter,
                     LocalBitcoinNode localBitcoinNode,
                     AppStartupState appStartupState,
                     Socks5ProxyProvider socks5ProxyProvider,
                     MediationManager mediationManager,
                     RefundManager refundManager,
                     ArbitrationManager arbitrationManager) {
        this.domainInitialisation = domainInitialisation;
        this.p2PNetworkSetup = p2PNetworkSetup;
        this.walletAppSetup = walletAppSetup;
        this.walletsManager = walletsManager;
        this.walletsSetup = walletsSetup;
        this.btcWalletService = btcWalletService;
        this.p2PService = p2PService;
        this.signedWitnessStorageService = signedWitnessStorageService;
        this.tradeManager = tradeManager;
        this.openOfferManager = openOfferManager;
        this.preferences = preferences;
        this.user = user;
        this.alertManager = alertManager;
        this.unconfirmedBsqChangeOutputListService = unconfirmedBsqChangeOutputListService;
        this.config = config;
        this.accountAgeWitnessService = accountAgeWitnessService;
        this.formatter = formatter;
        this.localBitcoinNode = localBitcoinNode;
        this.appStartupState = appStartupState;
        this.mediationManager = mediationManager;
        this.refundManager = refundManager;
        this.arbitrationManager = arbitrationManager;

        MemPoolSpaceTxBroadcaster.init(socks5ProxyProvider, preferences, localBitcoinNode);
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // API
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void displayAlertIfPresent(Alert alert, boolean openNewVersionPopup) {
        if (alert == null)
            return;

        if (alert.isSoftwareUpdateNotification()) {
            // only process if the alert version is "newer" than ours
            if (alert.isNewVersion(preferences)) {
                user.setDisplayedAlert(alert);          // save context to compare later
                newVersionAvailableProperty.set(true);  // shows link in footer bar
                if ((alert.canShowPopup(preferences) || openNewVersionPopup) && displayUpdateHandler != null) {
                    displayUpdateHandler.accept(alert, alert.showAgainKey());
                }
            }
        } else {
            // it is a normal message alert
            final Alert displayedAlert = user.getDisplayedAlert();
            if ((displayedAlert == null || !displayedAlert.equals(alert)) && displayAlertHandler != null)
                displayAlertHandler.accept(alert);
        }
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Main startup tasks
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void addBisqSetupListener(BisqSetupListener listener) {
        bisqSetupListeners.add(listener);
    }

    public void start() {
        // If user tried to downgrade we require a shutdown
        if (Config.baseCurrencyNetwork() == BaseCurrencyNetwork.BTC_MAINNET &&
                hasDowngraded(downGradePreventionHandler)) {
            return;
        }

        persistBisqVersion();
        maybeReSyncSPVChain();
        maybeShowTac(this::step2);
    }

    private void step2() {
        readMapsFromResources(this::step3);
        checkForCorrectOSArchitecture();
        checkIfRunningOnQubesOS();
    }

    private void step3() {
        startP2pNetworkAndWallet(this::step4);
    }

    private void step4() {
        initDomainServices();

        bisqSetupListeners.forEach(BisqSetupListener::onSetupComplete);

        // We set that after calling the setupCompleteHandler to not trigger a popup from the dev dummy accounts
        // in MainViewModel
        maybeShowSecurityRecommendation();
        maybeShowLocalhostRunningInfo();
        maybeShowAccountSigningStateInfo();
        maybeShowTorAddressUpgradeInformation();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Sub tasks
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void maybeReSyncSPVChain() {
        // We do the delete of the spv file at startup before BitcoinJ is initialized to avoid issues with locked files under Windows.
        if (getResyncSpvSemaphore()) {
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

    private void maybeShowTac(Runnable nextStep) {
        if (!preferences.isTacAcceptedV120() && !DevEnv.isDevMode()) {
            if (displayTacHandler != null)
                displayTacHandler.accept(() -> {
                    preferences.setTacAcceptedV120(true);
                    nextStep.run();
                });
        } else {
            nextStep.run();
        }
    }

    private void readMapsFromResources(Runnable completeHandler) {
        String postFix = "_" + config.baseCurrencyNetwork.name();
        p2PService.getP2PDataStorage().readFromResources(postFix, completeHandler);
    }

    private void startP2pNetworkAndWallet(Runnable nextStep) {
        ChangeListener<Boolean> walletInitializedListener = (observable, oldValue, newValue) -> {
            // TODO that seems to be called too often if Tor takes longer to start up...
            if (newValue && !p2pNetworkReady.get() && displayTorNetworkSettingsHandler != null)
                displayTorNetworkSettingsHandler.accept(true);
        };

        Timer startupTimeout = UserThread.runAfter(() -> {
            if (p2PNetworkSetup.p2pNetworkFailed.get() || walletsSetup.walletsSetupFailed.get()) {
                // Skip this timeout action if the p2p network or wallet setup failed
                // since an error prompt will be shown containing the error message
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

        log.info("Init P2P network");
        bisqSetupListeners.forEach(BisqSetupListener::onInitP2pNetwork);
        p2pNetworkReady = p2PNetworkSetup.init(this::initWallet, displayTorNetworkSettingsHandler);

        // We only init wallet service here if not using Tor for bitcoinj.
        // When using Tor, wallet init must be deferred until Tor is ready.
        // TODO encapsulate below conditional inside getUseTorForBitcoinJ
        if (!preferences.getUseTorForBitcoinJ() || localBitcoinNode.shouldBeUsed()) {
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
                nextStep.run();
            }
        });
    }

    private void initWallet() {
        log.info("Init wallet");
        bisqSetupListeners.forEach(BisqSetupListener::onInitWallet);
        Runnable walletPasswordHandler = () -> {
            log.info("Wallet password required");
            bisqSetupListeners.forEach(BisqSetupListener::onRequestWalletPassword);
            if (p2pNetworkReady.get())
                p2PNetworkSetup.setSplashP2PNetworkAnimationVisible(true);

            if (requestWalletPasswordHandler != null) {
                requestWalletPasswordHandler.accept(aesKey -> {
                    walletsManager.setAesKey(aesKey);
                    walletsManager.maybeAddSegwitKeychains(aesKey);
                    if (getResyncSpvSemaphore()) {
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
                getResyncSpvSemaphore(),
                showFirstPopupIfResyncSPVRequestedHandler,
                showPopupIfInvalidBtcConfigHandler,
                walletPasswordHandler,
                () -> {
                    if (allBasicServicesInitialized) {
                        checkForLockedUpFunds();
                        checkForInvalidMakerFeeTxs();
                    }
                },
                () -> walletInitialized.set(true));
    }

    private void initDomainServices() {
        log.info("initDomainServices");

        domainInitialisation.initDomainServices(rejectedTxErrorMessageHandler,
                displayPrivateNotificationHandler,
                daoErrorMessageHandler,
                daoWarnMessageHandler,
                filterWarningHandler,
                voteResultExceptionHandler,
                revolutAccountsUpdateHandler,
                amazonGiftCardAccountsUpdateHandler,
                daoRequiresRestartHandler);

        if (walletsSetup.downloadPercentageProperty().get() == 1) {
            checkForLockedUpFunds();
            checkForInvalidMakerFeeTxs();
        }

        alertManager.alertMessageProperty().addListener((observable, oldValue, newValue) ->
                displayAlertIfPresent(newValue, false));
        displayAlertIfPresent(alertManager.alertMessageProperty().get(), false);

        allBasicServicesInitialized = true;

        appStartupState.onDomainServicesInitialized();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Utils
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void checkForLockedUpFunds() {
        // We check if there are locked up funds in failed or closed trades
        try {
            Set<String> setOfAllTradeIds = tradeManager.getSetOfFailedOrClosedTradeIdsFromLockedInFunds();
            btcWalletService.getAddressEntriesForTrade().stream()
                    .filter(e -> setOfAllTradeIds.contains(e.getOfferId()) &&
                            e.getContext() == AddressEntry.Context.MULTI_SIG)
                    .forEach(e -> {
                        Coin balance = e.getCoinLockedInMultiSigAsCoin();
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
            if (e.getOffer().isBsqSwapOffer()) {
                return;
            }
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

    @Nullable
    public static String getLastBisqVersion() {
        File versionFile = getVersionFile();
        if (!versionFile.exists()) {
            return null;
        }
        try (Scanner scanner = new Scanner(versionFile)) {
            // We only expect 1 line
            if (scanner.hasNextLine()) {
                return scanner.nextLine();
            }
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
        return null;
    }

    @Nullable
    public static boolean getResyncSpvSemaphore() {
        File resyncSpvSemaphore = new File(Config.appDataDir(), RESYNC_SPV_FILE_NAME);
        return resyncSpvSemaphore.exists();
    }

    public static void setResyncSpvSemaphore(boolean isResyncSpvRequested) {
        File resyncSpvSemaphore = new File(Config.appDataDir(), RESYNC_SPV_FILE_NAME);
        if (isResyncSpvRequested) {
            if (!resyncSpvSemaphore.exists()) {
                try {
                    if (!resyncSpvSemaphore.createNewFile()) {
                        log.error("ResyncSpv file could not be created");
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                    log.error("ResyncSpv file could not be created. {}", e.toString());
                }
            }
        } else {
            resyncSpvSemaphore.delete();
        }
    }


    private static File getVersionFile() {
        return new File(Config.appDataDir(), VERSION_FILE_NAME);
    }

    public static boolean hasDowngraded() {
        return hasDowngraded(getLastBisqVersion());
    }

    public static boolean hasDowngraded(String lastVersion) {
        return lastVersion != null && Version.isNewVersion(lastVersion, Version.VERSION);
    }

    public static boolean hasDowngraded(@Nullable Consumer<String> downGradePreventionHandler) {
        String lastVersion = getLastBisqVersion();
        boolean hasDowngraded = hasDowngraded(lastVersion);
        if (hasDowngraded) {
            log.error("Downgrade from version {} to version {} is not supported", lastVersion, Version.VERSION);
            if (downGradePreventionHandler != null) {
                downGradePreventionHandler.accept(lastVersion);
            }
        }
        return hasDowngraded;
    }

    public static void persistBisqVersion() {
        File versionFile = getVersionFile();
        if (!versionFile.exists()) {
            try {
                if (!versionFile.createNewFile()) {
                    log.error("Version file could not be created");
                }
            } catch (IOException e) {
                e.printStackTrace();
                log.error("Version file could not be created. {}", e.toString());
            }
        }

        try (FileWriter fileWriter = new FileWriter(versionFile, false)) {
            fileWriter.write(Version.VERSION);
        } catch (IOException e) {
            e.printStackTrace();
            log.error("Writing Version failed. {}", e.toString());
        }
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

    /**
     * If Bisq is running on an OS that is virtualized under Qubes, show info popup with
     * link to the Setup Guide. The guide documents what other steps are needed, in
     * addition to installing the Linux package (qube sizing, etc)
     */
    private void checkIfRunningOnQubesOS() {
        if (Utilities.isQubesOS() && qubesOSInfoHandler != null) {
            qubesOSInfoHandler.run();
        }
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
        if (Config.baseCurrencyNetwork().isMainnet()) {
            maybeTriggerDisplayHandler("bitcoinLocalhostNode", displayLocalhostHandler,
                    localBitcoinNode.shouldBeUsed());
        }
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
        boolean signingStateFound = signedWitnessStorageService.getMap().values().stream()
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

    private void maybeShowTorAddressUpgradeInformation() {
        if (Config.baseCurrencyNetwork().isRegtest() ||
                Utils.isV3Address(Objects.requireNonNull(p2PService.getNetworkNode().getNodeAddress()).getHostName())) {
            return;
        }

        maybeRunTorNodeAddressUpgradeHandler();

        tradeManager.getNumPendingTrades().addListener((observable, oldValue, newValue) -> {
            long numPendingTrades = (long) newValue;
            if (numPendingTrades == 0) {
                maybeRunTorNodeAddressUpgradeHandler();
            }
        });
    }

    private void maybeRunTorNodeAddressUpgradeHandler() {
        if (mediationManager.getDisputesAsObservableList().stream().allMatch(Dispute::isClosed) &&
                refundManager.getDisputesAsObservableList().stream().allMatch(Dispute::isClosed) &&
                arbitrationManager.getDisputesAsObservableList().stream().allMatch(Dispute::isClosed) &&
                tradeManager.getNumPendingTrades().isEqualTo(0).get()) {
            Objects.requireNonNull(torAddressUpgradeHandler).run();
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


}
