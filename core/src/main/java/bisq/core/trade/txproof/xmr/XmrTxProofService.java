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

package bisq.core.trade.txproof.xmr;

import bisq.core.btc.setup.WalletsSetup;
import bisq.core.filter.FilterManager;
import bisq.core.locale.Res;
import bisq.core.support.dispute.mediation.MediationManager;
import bisq.core.support.dispute.refund.RefundManager;
import bisq.core.trade.ClosedTradableManager;
import bisq.core.trade.TradeManager;
import bisq.core.trade.bisq_v1.FailedTradesManager;
import bisq.core.trade.model.Tradable;
import bisq.core.trade.model.bisq_v1.SellerTrade;
import bisq.core.trade.model.bisq_v1.Trade;
import bisq.core.trade.protocol.bisq_v1.SellerProtocol;
import bisq.core.trade.txproof.AssetTxProofResult;
import bisq.core.trade.txproof.AssetTxProofService;
import bisq.core.user.AutoConfirmSettings;
import bisq.core.user.Preferences;

import bisq.network.Socks5ProxyProvider;
import bisq.network.p2p.BootstrapListener;
import bisq.network.p2p.P2PService;

import bisq.common.app.DevEnv;

import javax.inject.Inject;
import javax.inject.Singleton;

import org.fxmisc.easybind.EasyBind;
import org.fxmisc.easybind.monadic.MonadicBinding;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.value.ChangeListener;

import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import lombok.extern.slf4j.Slf4j;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Entry point for clients to request tx proof and trigger auto-confirm if all conditions
 * are met.
 */
@Slf4j
@Singleton
public class XmrTxProofService implements AssetTxProofService {
    private final FilterManager filterManager;
    private final Preferences preferences;
    private final TradeManager tradeManager;
    private final ClosedTradableManager closedTradableManager;
    private final FailedTradesManager failedTradesManager;
    private final MediationManager mediationManager;
    private final RefundManager refundManager;
    private final P2PService p2PService;
    private final WalletsSetup walletsSetup;
    private final Socks5ProxyProvider socks5ProxyProvider;
    private final Map<String, XmrTxProofRequestsPerTrade> servicesByTradeId = new HashMap<>();
    private AutoConfirmSettings autoConfirmSettings;
    private final Map<String, ChangeListener<Trade.State>> tradeStateListenerMap = new HashMap<>();
    private ChangeListener<Number> btcPeersListener, btcBlockListener;
    private BootstrapListener bootstrapListener;
    private MonadicBinding<Boolean> p2pNetworkAndWalletReady;
    private ChangeListener<Boolean> p2pNetworkAndWalletReadyListener;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    @SuppressWarnings("WeakerAccess")
    @Inject
    public XmrTxProofService(FilterManager filterManager,
                             Preferences preferences,
                             TradeManager tradeManager,
                             ClosedTradableManager closedTradableManager,
                             FailedTradesManager failedTradesManager,
                             MediationManager mediationManager,
                             RefundManager refundManager,
                             P2PService p2PService,
                             WalletsSetup walletsSetup,
                             Socks5ProxyProvider socks5ProxyProvider) {
        this.filterManager = filterManager;
        this.preferences = preferences;
        this.tradeManager = tradeManager;
        this.closedTradableManager = closedTradableManager;
        this.failedTradesManager = failedTradesManager;
        this.mediationManager = mediationManager;
        this.refundManager = refundManager;
        this.p2PService = p2PService;
        this.walletsSetup = walletsSetup;
        this.socks5ProxyProvider = socks5ProxyProvider;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // API
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void onAllServicesInitialized() {
        // As we might trigger the payout tx we want to be sure that we are well connected to the Bitcoin network.
        // onAllServicesInitialized is called once we have received the initial data but we want to have our
        // hidden service published and upDatedDataResponse received before we start.
        BooleanProperty isP2pBootstrapped = isP2pBootstrapped();
        BooleanProperty hasSufficientBtcPeers = hasSufficientBtcPeers();
        BooleanProperty isBtcBlockDownloadComplete = isBtcBlockDownloadComplete();
        if (isP2pBootstrapped.get() && hasSufficientBtcPeers.get() && isBtcBlockDownloadComplete.get()) {
            onP2pNetworkAndWalletReady();
        } else {
            p2pNetworkAndWalletReady = EasyBind.combine(isP2pBootstrapped, hasSufficientBtcPeers, isBtcBlockDownloadComplete,
                    (bootstrapped, sufficientPeers, downloadComplete) ->
                            bootstrapped && sufficientPeers && downloadComplete);

            p2pNetworkAndWalletReadyListener = (observable, oldValue, newValue) -> {
                if (newValue) {
                    onP2pNetworkAndWalletReady();
                }
            };
            p2pNetworkAndWalletReady.subscribe(p2pNetworkAndWalletReadyListener);
        }
    }

    @Override
    public void shutDown() {
        servicesByTradeId.values().forEach(XmrTxProofRequestsPerTrade::terminate);
        servicesByTradeId.clear();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Private
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void onP2pNetworkAndWalletReady() {
        if (p2pNetworkAndWalletReady != null) {
            p2pNetworkAndWalletReady.removeListener(p2pNetworkAndWalletReadyListener);
            p2pNetworkAndWalletReady = null;
            p2pNetworkAndWalletReadyListener = null;
        }

        if (!preferences.findAutoConfirmSettings("XMR").isPresent()) {
            log.error("AutoConfirmSettings is not present");
            return;
        }
        autoConfirmSettings = preferences.findAutoConfirmSettings("XMR").get();

        // We register a listener to stop running services. For new trades we check anyway in the trade validation
        filterManager.filterProperty().addListener((observable, oldValue, newValue) -> {
            if (isAutoConfDisabledByFilter()) {
                servicesByTradeId.values().stream().map(XmrTxProofRequestsPerTrade::getTrade).forEach(trade ->
                        trade.setAssetTxProofResult(AssetTxProofResult.FEATURE_DISABLED
                                .details(Res.get("portfolio.pending.autoConf.state.filterDisabledFeature"))));
                tradeManager.requestPersistence();
                shutDown();
            }
        });

        // We listen on new trades
        ObservableList<Trade> tradableList = tradeManager.getObservableList();
        tradableList.addListener((ListChangeListener<Tradable>) c -> {
            c.next();
            if (c.wasAdded()) {
                processTrades(c.getAddedSubList());
            }
        });

        // Process existing trades
        processTrades(tradableList);
    }

    private void processTrades(List<? extends Tradable> trades) {
        trades.stream()
                .filter(trade -> trade instanceof SellerTrade)
                .map(trade -> (SellerTrade) trade)
                .filter(this::isXmrTrade)
                .filter(trade -> !trade.isFiatReceived()) // Phase name is from the time when it was fiat only. Means counter currency (XMR) received.
                .forEach(this::processTradeOrAddListener);
    }

    // Basic requirements are fulfilled.
    // We process further if we are in the expected state or register a listener
    private void processTradeOrAddListener(SellerTrade trade) {
        if (isExpectedTradeState(trade.getTradeState())) {
            startRequestsIfValid(trade);
        } else {
            // We are expecting SELLER_RECEIVED_FIAT_PAYMENT_INITIATED_MSG in the future, so listen on changes
            ChangeListener<Trade.State> tradeStateListener = (observable, oldValue, newValue) -> {
                if (isExpectedTradeState(newValue)) {
                    ChangeListener<Trade.State> listener = tradeStateListenerMap.remove(trade.getId());
                    if (listener != null) {
                        trade.stateProperty().removeListener(listener);
                    }

                    startRequestsIfValid(trade);
                }
            };
            tradeStateListenerMap.put(trade.getId(), tradeStateListener);
            trade.stateProperty().addListener(tradeStateListener);
        }
    }

    private void startRequestsIfValid(SellerTrade trade) {
        String txId = trade.getCounterCurrencyTxId();
        String txHash = trade.getCounterCurrencyExtraData();
        if (is32BitHexStringInValid(txId) || is32BitHexStringInValid(txHash)) {
            trade.setAssetTxProofResult(AssetTxProofResult.INVALID_DATA.details(Res.get("portfolio.pending.autoConf.state.txKeyOrTxIdInvalid")));
            tradeManager.requestPersistence();
            return;
        }

        if (isAutoConfDisabledByFilter()) {
            trade.setAssetTxProofResult(AssetTxProofResult.FEATURE_DISABLED
                    .details(Res.get("portfolio.pending.autoConf.state.filterDisabledFeature")));
            tradeManager.requestPersistence();
            return;
        }

        if (wasTxKeyReUsed(trade, tradeManager.getObservableList())) {
            trade.setAssetTxProofResult(AssetTxProofResult.INVALID_DATA
                    .details(Res.get("portfolio.pending.autoConf.state.xmr.txKeyReused")));
            tradeManager.requestPersistence();
            return;
        }

        startRequests(trade);
    }

    private void startRequests(SellerTrade trade) {
        XmrTxProofRequestsPerTrade service = new XmrTxProofRequestsPerTrade(socks5ProxyProvider,
                trade,
                autoConfirmSettings,
                mediationManager,
                filterManager,
                refundManager);
        servicesByTradeId.put(trade.getId(), service);
        service.requestFromAllServices(
                assetTxProofResult -> {
                    trade.setAssetTxProofResult(assetTxProofResult);

                    if (assetTxProofResult == AssetTxProofResult.COMPLETED) {
                        log.info("###########################################################################################");
                        log.info("We auto-confirm trade {} as our all our services for the tx proof completed successfully", trade.getShortId());
                        log.info("###########################################################################################");

                        ((SellerProtocol) tradeManager.getTradeProtocol(trade)).onPaymentReceived(() -> {
                        }, errorMessage -> {
                        });
                    }

                    if (assetTxProofResult.isTerminal()) {
                        servicesByTradeId.remove(trade.getId());
                    }

                    tradeManager.requestPersistence();
                },
                (errorMessage, throwable) -> {
                    log.error(errorMessage);
                });
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Startup checks
    ///////////////////////////////////////////////////////////////////////////////////////////

    private BooleanProperty isBtcBlockDownloadComplete() {
        BooleanProperty result = new SimpleBooleanProperty();
        if (walletsSetup.isDownloadComplete()) {
            result.set(true);
        } else {
            btcBlockListener = (observable, oldValue, newValue) -> {
                if (walletsSetup.isDownloadComplete()) {
                    walletsSetup.downloadPercentageProperty().removeListener(btcBlockListener);
                    result.set(true);
                }
            };
            walletsSetup.downloadPercentageProperty().addListener(btcBlockListener);
        }
        return result;
    }

    private BooleanProperty hasSufficientBtcPeers() {
        BooleanProperty result = new SimpleBooleanProperty();
        if (walletsSetup.hasSufficientPeersForBroadcast()) {
            result.set(true);
        } else {
            btcPeersListener = (observable, oldValue, newValue) -> {
                if (walletsSetup.hasSufficientPeersForBroadcast()) {
                    walletsSetup.numPeersProperty().removeListener(btcPeersListener);
                    result.set(true);
                }
            };
            walletsSetup.numPeersProperty().addListener(btcPeersListener);
        }
        return result;
    }

    private BooleanProperty isP2pBootstrapped() {
        BooleanProperty result = new SimpleBooleanProperty();
        if (p2PService.isBootstrapped()) {
            result.set(true);
        } else {
            bootstrapListener = new BootstrapListener() {
                @Override
                public void onUpdatedDataReceived() {
                    p2PService.removeP2PServiceListener(bootstrapListener);
                    result.set(true);
                }
            };
            p2PService.addP2PServiceListener(bootstrapListener);
        }
        return result;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Validation
    ///////////////////////////////////////////////////////////////////////////////////////////

    private boolean isXmrTrade(Trade trade) {
        return (checkNotNull(trade.getOffer()).getCurrencyCode().equals("XMR"));
    }

    private boolean isExpectedTradeState(Trade.State newValue) {
        return newValue == Trade.State.SELLER_RECEIVED_FIAT_PAYMENT_INITIATED_MSG;
    }

    private boolean is32BitHexStringInValid(String hexString) {
        if (hexString == null || hexString.isEmpty() || !hexString.matches("[a-fA-F0-9]{64}")) {
            log.warn("Invalid hexString: {}", hexString);
            return true;
        }

        return false;
    }

    private boolean isAutoConfDisabledByFilter() {
        return filterManager.getFilter() != null &&
                filterManager.getFilter().isDisableAutoConf();
    }

    private boolean wasTxKeyReUsed(Trade trade, List<Trade> activeTrades) {
        // For dev testing we reuse test data so we ignore that check
        if (DevEnv.isDevMode()) {
            return false;
        }

        // We need to prevent that a user tries to scam by reusing a txKey and txHash of a previous XMR trade with
        // the same user (same address) and same amount. We check only for the txKey as a same txHash but different
        // txKey is not possible to get a valid result at proof.
        Stream<Trade> failedAndOpenTrades = Stream.concat(
                activeTrades.stream(),
                failedTradesManager.getObservableList().stream());
        Stream<Trade> closedTrades = closedTradableManager.getObservableList().stream()
                .filter(tradable -> tradable instanceof Trade)
                .map(tradable -> (Trade) tradable);
        Stream<Trade> allTrades = Stream.concat(failedAndOpenTrades, closedTrades);
        String txKey = trade.getCounterCurrencyExtraData();
        return allTrades
                .filter(t -> !t.getId().equals(trade.getId())) // ignore same trade
                .anyMatch(t -> {
                    String extra = t.getCounterCurrencyExtraData();
                    if (extra == null) {
                        return false;
                    }

                    boolean alreadyUsed = extra.equals(txKey);
                    if (alreadyUsed) {
                        log.warn("Peer used the XMR tx key already at another trade with trade ID {}. " +
                                "This might be a scam attempt.", t.getId());
                    }
                    return alreadyUsed;
                });
    }
}
