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
import bisq.core.payment.payload.AssetsAccountPayload;
import bisq.core.trade.SellerTrade;
import bisq.core.trade.Trade;
import bisq.core.trade.closed.ClosedTradableManager;
import bisq.core.trade.failed.FailedTradesManager;
import bisq.core.trade.txproof.AssetTxProofResult;
import bisq.core.user.AutoConfirmSettings;
import bisq.core.user.Preferences;

import bisq.network.Socks5ProxyProvider;
import bisq.network.p2p.P2PService;

import bisq.common.app.DevEnv;
import bisq.common.handlers.FaultHandler;

import javax.inject.Inject;
import javax.inject.Singleton;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.stream.Stream;

import lombok.extern.slf4j.Slf4j;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Entry point for clients to request tx proof and trigger auto-confirm if all conditions
 * are met.
 */
@Slf4j
@Singleton
public class XmrTxProofService {
    private final FilterManager filterManager;
    private final Socks5ProxyProvider socks5ProxyProvider;
    private final Preferences preferences;
    private final ClosedTradableManager closedTradableManager;
    private final FailedTradesManager failedTradesManager;
    private final P2PService p2PService;
    private final WalletsSetup walletsSetup;

    private final Map<String, XmrTxProofRequestsPerTrade> servicesByTradeId = new HashMap<>();


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    @SuppressWarnings("WeakerAccess")
    @Inject
    public XmrTxProofService(FilterManager filterManager,
                             Preferences preferences,
                             ClosedTradableManager closedTradableManager,
                             FailedTradesManager failedTradesManager,
                             P2PService p2PService,
                             WalletsSetup walletsSetup,
                             Socks5ProxyProvider socks5ProxyProvider) {
        this.filterManager = filterManager;
        this.preferences = preferences;
        this.closedTradableManager = closedTradableManager;
        this.failedTradesManager = failedTradesManager;
        this.p2PService = p2PService;
        this.walletsSetup = walletsSetup;
        this.socks5ProxyProvider = socks5ProxyProvider;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // API
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void maybeStartRequests(Trade trade,
                                   List<Trade> activeTrades,
                                   Consumer<AssetTxProofResult> resultHandler,
                                   FaultHandler faultHandler) {
        if (!isXmrBuyer(trade)) {
            return;
        }

        String txId = trade.getCounterCurrencyTxId();
        String txHash = trade.getCounterCurrencyExtraData();
        if (is32BitHexStringInValid(txId) || is32BitHexStringInValid(txHash)) {
            trade.setAssetTxProofResult(AssetTxProofResult.INVALID_DATA.details(Res.get("portfolio.pending.autoConf.state.txKeyOrTxIdInvalid")));
            return;
        }

        if (!networkAndWalletReady()) {
            return;
        }

        Optional<AutoConfirmSettings> optionalAutoConfirmSettings = preferences.findAutoConfirmSettings("XMR");
        if (!optionalAutoConfirmSettings.isPresent()) {
            // Not expected
            log.error("autoConfirmSettings is not present");
            return;
        }
        AutoConfirmSettings autoConfirmSettings = optionalAutoConfirmSettings.get();

        if (!isFeatureEnabled(trade, autoConfirmSettings)) {
            return;
        }

        if (wasTxKeyReUsed(trade, activeTrades)) {
            return;
        }

        XmrTxProofRequestsPerTrade service = new XmrTxProofRequestsPerTrade(socks5ProxyProvider,
                trade,
                autoConfirmSettings);
        servicesByTradeId.put(trade.getId(), service);
        service.requestFromAllServices(
                assetTxProofResult -> {
                    trade.setAssetTxProofResult(assetTxProofResult);

                    if (assetTxProofResult.isTerminal()) {
                        cleanUp(trade);
                    }

                    resultHandler.accept(assetTxProofResult);
                },
                faultHandler);
    }

    public void shutDown() {
        servicesByTradeId.values().forEach(XmrTxProofRequestsPerTrade::terminate);
        servicesByTradeId.clear();
    }

    private void cleanUp(Trade trade) {
        servicesByTradeId.remove(trade.getId());
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Validation
    ///////////////////////////////////////////////////////////////////////////////////////////

    private boolean isXmrBuyer(Trade trade) {
        if (!checkNotNull(trade.getOffer()).getCurrencyCode().equals("XMR")) {
            return false;
        }

        if (!(trade instanceof SellerTrade)) {
            return false;
        }

        return checkNotNull(trade.getContract()).getSellerPaymentAccountPayload() instanceof AssetsAccountPayload;
    }

    private boolean is32BitHexStringInValid(String hexString) {
        if (hexString == null || hexString.isEmpty() || !hexString.matches("[a-fA-F0-9]{64}")) {
            log.warn("Invalid hexString: {}", hexString);
            return true;
        }

        return false;
    }

    private boolean networkAndWalletReady() {
        return p2PService.isBootstrapped() &&
                walletsSetup.isDownloadComplete() &&
                walletsSetup.hasSufficientPeersForBroadcast();
    }

    private boolean isFeatureEnabled(Trade trade, AutoConfirmSettings autoConfirmSettings) {
        boolean isEnabled = checkNotNull(autoConfirmSettings).isEnabled() && !isAutoConfDisabledByFilter();
        if (!isEnabled) {
            trade.setAssetTxProofResult(AssetTxProofResult.FEATURE_DISABLED);
        }
        return isEnabled;
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
        Stream<Trade> failedAndOpenTrades = Stream.concat(activeTrades.stream(), failedTradesManager.getFailedTrades().stream());
        Stream<Trade> closedTrades = closedTradableManager.getClosedTradables().stream()
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
                        trade.setAssetTxProofResult(AssetTxProofResult.INVALID_DATA.details(Res.get("portfolio.pending.autoConf.state.xmr.txKeyReused")));
                    }
                    return alreadyUsed;
                });
    }
}
