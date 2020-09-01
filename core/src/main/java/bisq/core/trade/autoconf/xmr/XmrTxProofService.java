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
import bisq.core.locale.Res;
import bisq.core.monetary.Volume;
import bisq.core.payment.payload.AssetsAccountPayload;
import bisq.core.payment.payload.PaymentAccountPayload;
import bisq.core.trade.SellerTrade;
import bisq.core.trade.Trade;
import bisq.core.trade.autoconf.AssetTxProofResult;
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

import static bisq.core.trade.autoconf.xmr.XmrTxProofRequest.Result;
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
    private final Map<String, ChangeListener<Trade.State>> listenerByTxId = new HashMap<>();
    @Getter
    private int requiredSuccessResults;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    public XmrTxProofService(FilterManager filterManager,
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

        if (!isFeatureEnabled(trade)) {
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

        if (isPayoutPublished(trade)) {
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
        String txHash = trade.getCounterCurrencyTxId();
        String txKey = trade.getCounterCurrencyExtraData();

        List<String> serviceAddresses = preferences.getAutoConfirmSettings().serviceAddresses;
        requiredSuccessResults = serviceAddresses.size();

        ChangeListener<Trade.State> listener = (observable, oldValue, newValue) -> isPayoutPublished(trade);
        trade.stateProperty().addListener(listener);
        listenerByTxId.put(trade.getId(), listener);


        for (String serviceAddress : serviceAddresses) {
            XmrTxProofModel xmrTxProofModel = new XmrTxProofModel(trade.getId(),
                    txHash,
                    txKey,
                    recipientAddress,
                    amountXmr,
                    trade.getDate(),
                    getRequiredConfirmations(),
                    serviceAddress);
            XmrTxProofRequest request = xmrTxProofRequestService.getRequest(xmrTxProofModel);
            if (request != null) {
                request.start(result -> handleResult(request, trade, result),
                        (errorMsg, throwable) -> log.warn(errorMsg));
                trade.setAssetTxProofResult(AssetTxProofResult.REQUEST_STARTED);
            }
        }
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Private
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void handleResult(XmrTxProofRequest request,
                              Trade trade,
                              Result result) {
        if (isPayoutPublished(trade)) {
            return;
        }


        // If one service fails we consider all failed as we require that all of our defined services result
        // successfully. For now we keep it that way as it reduces complexity but it might be useful to
        // support a min number of services which need to succeed from a larger set of services.
        // E.g. 2 out of 3 services need to succeed.

        int numRequestsWithSuccessResult = xmrTxProofRequestService.numRequestsWithSuccessResult();
        switch (result) {
            case PENDING:
                applyPending(trade, result);

                // Repeating the requests is handled in XmrTransferProofRequester
                return;
            case SUCCESS:
                if (numRequestsWithSuccessResult < requiredSuccessResults) {
                    log.info("Tx proof request to service {} for trade {} succeeded. We have {} remaining request(s) to other service(s).",
                            request.getServiceAddress(), trade.getShortId(), numRequestsWithSuccessResult);

                    applyPending(trade, result);
                    return; // not all APIs have confirmed yet
                }

                // All our services have returned a SUCCESS result so we have completed.
                trade.setAssetTxProofResult(AssetTxProofResult.COMPLETED);
                log.info("All {} tx proof requests for trade {} have been successful.",
                        requiredSuccessResults, trade.getShortId());
                removeListener(trade);

                if (!isPayoutPublished(trade)) {
                    log.info("We auto-confirm XMR receipt to complete trade {}.", trade.getShortId());
                    // Trade state update is handled in the trade protocol method triggered by the onFiatPaymentReceived call
                    // This triggers the completion of the trade with signing and publishing the payout tx
                    ((SellerTrade) trade).onFiatPaymentReceived(() -> {
                            },
                            errorMessage -> {
                            });
                    accountAgeWitnessService.maybeSignWitness(trade);
                } else {
                    log.info("Trade {} have been completed in the meantime.", trade.getShortId());
                }
                terminate(trade);
                return;
            case FAILED:
                log.warn("Tx proof request to service {} for trade {} failed. " +
                                "This might not mean that the XMR transfer was invalid but you have to check yourself " +
                                "if the XMR transfer was correct. Result={}",
                        request.getServiceAddress(), trade.getShortId(), result);
                trade.setAssetTxProofResult(AssetTxProofResult.FAILED);
                terminate(trade);
                return;
            case ERROR:
                log.warn("Tx proof request to service {} for trade {} resulted in an error. " +
                                "This might not mean that the XMR transfer was invalid but can be a network or " +
                                "service problem. Result={}",
                        request.getServiceAddress(), trade.getShortId(), result);

                trade.setAssetTxProofResult(AssetTxProofResult.ERROR);
                terminate(trade);
                return;
            default:
                log.warn("Unexpected result {}", result);
        }
    }

    private void applyPending(Trade trade, Result result) {
        int numRequestsWithSuccessResult = xmrTxProofRequestService.numRequestsWithSuccessResult();
        XmrTxProofRequest.Detail detail = result.getDetail();
        int numConfirmations = detail != null ? detail.getNumConfirmations() : 0;
        log.info("Tx proof service received a {} result for trade {}. numConfirmations={}",
                result, trade.getShortId(), numConfirmations);

        String details = "";
        if (XmrTxProofRequest.Detail.PENDING_CONFIRMATIONS == detail) {
            details = Res.get("portfolio.pending.autoConf.state.confirmations", numConfirmations, getRequiredConfirmations());

        } else if (XmrTxProofRequest.Detail.TX_NOT_FOUND == detail) {
            details = Res.get("portfolio.pending.autoConf.state.txNotFound");
        }
        trade.setAssetTxProofResult(AssetTxProofResult.PENDING
                .numSuccessResults(numRequestsWithSuccessResult)
                .requiredSuccessResults(requiredSuccessResults)
                .details(details));
    }

    private void terminate(Trade trade) {
        xmrTxProofRequestService.stopAllRequest();
        removeListener(trade);
    }

    private void removeListener(Trade trade) {
        ChangeListener<Trade.State> listener = listenerByTxId.get(trade.getId());
        if (listener != null) {
            trade.stateProperty().removeListener(listener);
            listenerByTxId.remove(trade.getId());
        }
    }

    private int getRequiredConfirmations() {
        return preferences.getAutoConfirmSettings().requiredConfirmations;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Validation
    ///////////////////////////////////////////////////////////////////////////////////////////

    private boolean isPayoutPublished(Trade trade) {
        if (trade.isPayoutPublished()) {
            log.warn("Trade payout already published, shutting down all open API requests for trade {}",
                    trade.getShortId());
            trade.setAssetTxProofResult(AssetTxProofResult.PAYOUT_TX_ALREADY_PUBLISHED);
            terminate(trade);
            return true;
        }
        return false;
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

    private boolean isFeatureEnabled(Trade trade) {
        boolean isEnabled = preferences.getAutoConfirmSettings().enabled && !isAutoConfDisabledByFilter();
        if (!isEnabled) {
            trade.setAssetTxProofResult(AssetTxProofResult.FEATURE_DISABLED);
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

            trade.setAssetTxProofResult(AssetTxProofResult.TRADE_LIMIT_EXCEEDED);
            return true;
        }
        return false;
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
                        log.warn("Peer used the XMR tx key already at another trade with trade ID {}. " +
                                "This might be a scam attempt.", t.getId());
                        trade.setAssetTxProofResult(AssetTxProofResult.INVALID_DATA.details(Res.get("portfolio.pending.autoConf.state.xmr.txKeyReused")));
                    }
                    return alreadyUsed;
                });
    }
}
