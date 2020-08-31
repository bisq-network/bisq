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

package bisq.core.trade.autoconf.xmr;

import bisq.core.account.witness.AccountAgeWitnessService;
import bisq.core.btc.setup.WalletsSetup;
import bisq.core.filter.FilterManager;
import bisq.core.monetary.Volume;
import bisq.core.payment.payload.AssetsAccountPayload;
import bisq.core.payment.payload.PaymentAccountPayload;
import bisq.core.trade.SellerTrade;
import bisq.core.trade.Trade;
import bisq.core.trade.closed.ClosedTradableManager;
import bisq.core.trade.failed.FailedTradesManager;
import bisq.core.user.Preferences;

import bisq.network.p2p.P2PService;

import bisq.common.app.DevEnv;

import org.bitcoinj.core.Coin;

import javax.inject.Inject;
import javax.inject.Singleton;

import javafx.beans.value.ChangeListener;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import lombok.Getter;
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
    private final Preferences preferences;
    private final XmrTxProofRequestService xmrTxProofRequestService;
    private final AccountAgeWitnessService accountAgeWitnessService;
    private final ClosedTradableManager closedTradableManager;
    private final FailedTradesManager failedTradesManager;
    private final P2PService p2PService;
    private final WalletsSetup walletsSetup;
    private final Map<String, RequestInfo> requestInfoByTxIdMap = new HashMap<>();


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    private XmrTxProofService(FilterManager filterManager,
                              Preferences preferences,
                              XmrTxProofRequestService xmrTxProofRequestService,
                              ClosedTradableManager closedTradableManager,
                              FailedTradesManager failedTradesManager,
                              P2PService p2PService,
                              WalletsSetup walletsSetup,
                              AccountAgeWitnessService accountAgeWitnessService) {
        this.filterManager = filterManager;
        this.preferences = preferences;
        this.xmrTxProofRequestService = xmrTxProofRequestService;
        this.closedTradableManager = closedTradableManager;
        this.failedTradesManager = failedTradesManager;
        this.p2PService = p2PService;
        this.walletsSetup = walletsSetup;
        this.accountAgeWitnessService = accountAgeWitnessService;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // API
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void maybeStartRequestTxProofProcess(Trade trade, List<Trade> activeTrades) {
        if (!dataValid(trade)) {
            return;
        }

        if (!isXmrBuyer(trade)) {
            return;
        }

        if (!featureEnabled(trade)) {
            return;
        }

        if (!networkAndWalletReady()) {
            return;
        }

        if (isTradeAmountAboveLimit(trade)) {
            return;
        }

        if (wasTxKeyReUsed(trade, activeTrades)) {
            return;
        }

        Coin tradeAmount = trade.getTradeAmount();
        Volume volume = checkNotNull(trade.getOffer()).getVolumeByAmount(tradeAmount);
        // XMR satoshis have 12 decimal places vs. bitcoin's 8
        long amountXmr = volume != null ? volume.getValue() * 10000L : 0L;

        PaymentAccountPayload sellersPaymentAccountPayload = checkNotNull(trade.getContract()).getSellerPaymentAccountPayload();
        String recipientAddress = ((AssetsAccountPayload) sellersPaymentAccountPayload).getAddress();
        if (DevEnv.isDevMode()) {
            // For dev testing we need to add the matching address to the dev tx key and dev view key
            recipientAddress = XmrTxProofModel.DEV_ADDRESS;
            amountXmr = XmrTxProofModel.DEV_AMOUNT;
        }
        int confirmsRequired = preferences.getAutoConfirmSettings().requiredConfirmations;
        String txHash = trade.getCounterCurrencyTxId();
        String txKey = trade.getCounterCurrencyExtraData();
        List<String> serviceAddresses = preferences.getAutoConfirmSettings().serviceAddresses;


        ChangeListener<Trade.State> listener = (observable, oldValue, newValue) -> {
            if (trade.isPayoutPublished()) {
                log.warn("Trade payout already published, shutting down all open API requests for trade {}",
                        trade.getShortId());
                cleanup(trade);
            }
        };
        trade.stateProperty().addListener(listener);
        requestInfoByTxIdMap.put(trade.getId(), new RequestInfo(serviceAddresses.size(), listener)); // need result from each service address

        trade.setAssetTxProofResult(new XmrTxProofResult(XmrTxProofResult.State.REQUEST_STARTED));
        for (String serviceAddress : serviceAddresses) {
            XmrTxProofModel xmrTxProofModel = new XmrTxProofModel(
                    txHash,
                    txKey,
                    recipientAddress,
                    amountXmr,
                    trade.getDate(),
                    confirmsRequired,
                    serviceAddress);
            xmrTxProofRequestService.requestProof(xmrTxProofModel,
                    result -> {
                        if (!handleProofResult(result, trade)) {
                            xmrTxProofRequestService.terminateRequest(xmrTxProofModel);
                        }
                    },
                    (errorMsg, throwable) -> {
                        log.warn(errorMsg);
                    }
            );
        }
    }

    private boolean handleProofResult(XmrTxProofResult result, Trade trade) {
        // here we count the Trade's API results from all
        // different serviceAddress and figure out when all have finished
        if (!requestInfoByTxIdMap.containsKey(trade.getId())) {
            // We have cleaned up our map in the meantime
            return false;
        }

        RequestInfo requestInfo = requestInfoByTxIdMap.get(trade.getId());

        if (requestInfo.isInvalid()) {
            log.info("Ignoring stale API result [{}], tradeId {} due to previous error",
                    result.getState(), trade.getShortId());
            return false;   // terminate any pending responses
        }

        if (trade.isPayoutPublished()) {
            log.warn("Trade payout already published, shutting down all open API requests for trade {}",
                    trade.getShortId());
            cleanup(trade);
        }

        if (result.isErrorState()) {
            log.warn("Tx Proof Failure {}, shutting down all open API requests for this trade {}",
                    result.getState(), trade.getShortId());
            trade.setAssetTxProofResult(result);         // this updates the GUI with the status..
            requestInfo.invalidate();
            return false;
        }

        if (result.isPendingState()) {
            log.info("Auto confirm received a {} message for tradeId {}, retry will happen automatically",
                    result.getState(), trade.getShortId());
            trade.setAssetTxProofResult(result);         // this updates the GUI with the status..
            // Repeating the requests is handled in XmrTransferProofRequester
            return true;
        }


        if (result.getState() == XmrTxProofResult.State.SINGLE_SERVICE_SUCCEEDED) {
            int resultsCountdown = requestInfo.decrementAndGet();
            log.info("Received a {} result, remaining proofs needed: {}, tradeId {}",
                    result.getState(), resultsCountdown, trade.getShortId());
            if (requestInfo.hasPendingResults()) {
                XmrTxProofResult assetTxProofResult = new XmrTxProofResult(XmrTxProofResult.State.PENDING_SERVICE_RESULTS);
                assetTxProofResult.setPendingServiceResults(requestInfo.getPendingResults());
                assetTxProofResult.setRequiredServiceResults(requestInfo.getNumServices());
                trade.setAssetTxProofResult(assetTxProofResult);
                return true; // not all APIs have confirmed yet
            }

            // All our services have returned a PROOF_OK result so we have succeeded.
            cleanup(trade);
            trade.setAssetTxProofResult(new XmrTxProofResult(XmrTxProofResult.State.ALL_SERVICES_SUCCEEDED));
            log.info("Auto confirm was successful, transitioning trade {} to next step...", trade.getShortId());
            if (!trade.isPayoutPublished()) {
                // Trade state update is handled in the trade protocol method triggered by the onFiatPaymentReceived call
                // This triggers the completion of the trade with signing and publishing the payout tx
                ((SellerTrade) trade).onFiatPaymentReceived(() -> {
                        },
                        errorMessage -> {
                        });
            }
            accountAgeWitnessService.maybeSignWitness(trade);

            return true;
        } else {
            //TODO check if that can happen
            log.error("Unexpected state {}", result.getState());
            return false;
        }
    }

    private boolean dataValid(Trade trade) {
        String txKey = trade.getCounterCurrencyExtraData();
        String txHash = trade.getCounterCurrencyTxId();

        if (txKey == null || txKey.isEmpty()) {
            return false;
        }

        if (txHash == null || txHash.isEmpty()) {
            return false;
        }

        if (!txHash.matches("[a-fA-F0-9]{64}") || !txKey.matches("[a-fA-F0-9]{64}")) {
            log.error("Validation failed: txHash {} txKey {}", txHash, txKey);
            return false;
        }

        return true;
    }

    private boolean isXmrBuyer(Trade trade) {
        if (!checkNotNull(trade.getOffer()).getCurrencyCode().equals("XMR")) {
            return false;
        }

        if (!(trade instanceof SellerTrade)) {
            return false;
        }

        return checkNotNull(trade.getContract()).getSellerPaymentAccountPayload() instanceof AssetsAccountPayload;
    }

    private boolean networkAndWalletReady() {
        return p2PService.isBootstrapped() &&
                walletsSetup.isDownloadComplete() &&
                walletsSetup.hasSufficientPeersForBroadcast();
    }

    private boolean featureEnabled(Trade trade) {
        boolean isEnabled = preferences.getAutoConfirmSettings().enabled && !isAutoConfDisabledByFilter();
        if (!isEnabled) {
            trade.setAssetTxProofResult(new XmrTxProofResult(XmrTxProofResult.State.FEATURE_DISABLED));
        }
        return isEnabled;
    }

    private boolean isAutoConfDisabledByFilter() {
        return filterManager.getFilter() != null &&
                filterManager.getFilter().isDisableAutoConf();
    }

    private boolean isTradeAmountAboveLimit(Trade trade) {
        Coin tradeAmount = trade.getTradeAmount();
        Coin tradeLimit = Coin.valueOf(preferences.getAutoConfirmSettings().tradeLimit);
        if (tradeAmount != null && tradeAmount.isGreaterThan(tradeLimit)) {
            log.warn("Trade amount {} is higher than settings limit {}, will not attempt auto-confirm",
                    tradeAmount.toFriendlyString(), tradeLimit.toFriendlyString());

            trade.setAssetTxProofResult(new XmrTxProofResult(XmrTxProofResult.State.TRADE_LIMIT_EXCEEDED));
            return true;
        }
        return false;
    }

    private void cleanup(Trade trade) {
        trade.stateProperty().removeListener(requestInfoByTxIdMap.get(trade.getId()).getListener());
        requestInfoByTxIdMap.remove(trade.getId());
    }

    private boolean wasTxKeyReUsed(Trade trade, List<Trade> activeTrades) {
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
                        String message = "Peer used the XMR tx key already at another trade with trade ID " +
                                t.getId() + ". This might be a scam attempt.";
                        trade.setAssetTxProofResult(new XmrTxProofResult(XmrTxProofResult.State.TX_KEY_REUSED, message));
                    }
                    return alreadyUsed;
                });
    }

    @Getter
    private static class RequestInfo {
        private final int numServices;
        private int pendingResults;
        private ChangeListener<Trade.State> listener;

        RequestInfo(int numServices, ChangeListener<Trade.State> listener) {
            this.numServices = numServices;
            this.pendingResults = numServices;
            this.listener = listener;
        }

        int decrementAndGet() {
            pendingResults--;
            return pendingResults;
        }

        void invalidate() {
            pendingResults = -1;
        }

        boolean isInvalid() {
            return pendingResults < 0;
        }

        boolean hasPendingResults() {
            return pendingResults > 0;
        }
    }
}
