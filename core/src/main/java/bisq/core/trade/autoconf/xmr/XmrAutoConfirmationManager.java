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
import bisq.core.offer.Offer;
import bisq.core.payment.payload.AssetsAccountPayload;
import bisq.core.payment.payload.PaymentAccountPayload;
import bisq.core.trade.Contract;
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

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import lombok.extern.slf4j.Slf4j;

import static com.google.common.base.Preconditions.checkNotNull;

@Slf4j
@Singleton
public class XmrAutoConfirmationManager {

    private final FilterManager filterManager;
    private final Preferences preferences;
    private final XmrTransferProofService xmrTransferProofService;
    private final AccountAgeWitnessService accountAgeWitnessService;
    private final ClosedTradableManager closedTradableManager;
    private final FailedTradesManager failedTradesManager;
    private final P2PService p2PService;
    private final WalletsSetup walletsSetup;
    private final Map<String, Integer> txProofResultsPending = new HashMap<>();

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    private XmrAutoConfirmationManager(FilterManager filterManager,
                                       Preferences preferences,
                                       XmrTransferProofService xmrTransferProofService,
                                       ClosedTradableManager closedTradableManager,
                                       FailedTradesManager failedTradesManager,
                                       P2PService p2PService,
                                       WalletsSetup walletsSetup,
                                       AccountAgeWitnessService accountAgeWitnessService
    ) {
        this.filterManager = filterManager;
        this.preferences = preferences;
        this.xmrTransferProofService = xmrTransferProofService;
        this.closedTradableManager = closedTradableManager;
        this.failedTradesManager = failedTradesManager;
        this.p2PService = p2PService;
        this.walletsSetup = walletsSetup;
        this.accountAgeWitnessService = accountAgeWitnessService;
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // API
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void processCounterCurrencyExtraData(Trade trade, Stream<Trade> activeTrades) {
        String counterCurrencyExtraData = trade.getCounterCurrencyExtraData();
        if (counterCurrencyExtraData == null || counterCurrencyExtraData.isEmpty()) {
            return;
        }

        String txHash = trade.getCounterCurrencyTxId();
        if (txHash == null || txHash.isEmpty()) {
            return;
        }

        Contract contract = checkNotNull(trade.getContract(), "Contract must not be null");
        PaymentAccountPayload sellersPaymentAccountPayload = contract.getSellerPaymentAccountPayload();
        if (!(sellersPaymentAccountPayload instanceof AssetsAccountPayload)) {
            return;
        }
        AssetsAccountPayload sellersAssetsAccountPayload = (AssetsAccountPayload) sellersPaymentAccountPayload;

        if (!(trade instanceof SellerTrade)) {
            return;
        }

        // Take the safe option and don't begin auto confirmation if the app has not reached a high enough level
        // of operation.  In that case it will be left for the user to confirm the trade manually which is fine.
        if (!p2PService.isBootstrapped()) {
            return;
        }
        if (!walletsSetup.hasSufficientPeersForBroadcast()) {
            return;
        }
        if (!walletsSetup.isDownloadComplete()) {
            return;
        }

        Offer offer = checkNotNull(trade.getOffer(), "Offer must not be null");
        if (offer.getCurrencyCode().equals("XMR")) {
            //noinspection UnnecessaryLocalVariable
            String txKey = counterCurrencyExtraData;

            if (!txHash.matches("[a-fA-F0-9]{64}") || !txKey.matches("[a-fA-F0-9]{64}")) {
                log.error("Validation failed: txHash {} txKey {}", txHash, txKey);
                return;
            }

            // We need to prevent that a user tries to scam by reusing a txKey and txHash of a previous XMR trade with
            // the same user (same address) and same amount. We check only for the txKey as a same txHash but different
            // txKey is not possible to get a valid result at proof.
            Stream<Trade> failedAndOpenTrades = Stream.concat(activeTrades, failedTradesManager.getFailedTrades().stream());
            Stream<Trade> closedTrades = closedTradableManager.getClosedTradables().stream()
                    .filter(tradable -> tradable instanceof Trade)
                    .map(tradable -> (Trade) tradable);
            Stream<Trade> allTrades = Stream.concat(failedAndOpenTrades, closedTrades);

            boolean txKeyUsedAtAnyOpenTrade = allTrades
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
                            trade.setAutoConfirmResult(new XmrAutoConfirmResult(XmrAutoConfirmResult.State.TX_KEY_REUSED, message));
                        }
                        return alreadyUsed;
                    });

            if (txKeyUsedAtAnyOpenTrade && !DevEnv.isDevMode()) {
                return;
            }

            if (!preferences.getAutoConfirmSettings().enabled || this.isAutoConfDisabledByFilter()) {
                trade.setAutoConfirmResult(new XmrAutoConfirmResult(XmrAutoConfirmResult.State.FEATURE_DISABLED, null));
                return;
            }
            Coin tradeAmount = trade.getTradeAmount();
            Coin tradeLimit = Coin.valueOf(preferences.getAutoConfirmSettings().tradeLimit);
            if (tradeAmount != null && tradeAmount.isGreaterThan(tradeLimit)) {
                log.warn("Trade amount {} is higher than settings limit {}, will not attempt auto-confirm",
                        tradeAmount.toFriendlyString(), tradeLimit.toFriendlyString());
                trade.setAutoConfirmResult(new XmrAutoConfirmResult(XmrAutoConfirmResult.State.TRADE_LIMIT_EXCEEDED, null));
                return;
            }

            String address = sellersAssetsAccountPayload.getAddress();
            // XMR satoshis have 12 decimal places vs. bitcoin's 8
            Volume volume = offer.getVolumeByAmount(tradeAmount);
            long amountXmr = volume != null ? volume.getValue() * 10000L : 0L;
            int confirmsRequired = preferences.getAutoConfirmSettings().requiredConfirmations;
            trade.setAutoConfirmResult(new XmrAutoConfirmResult(XmrAutoConfirmResult.State.TX_NOT_FOUND));
            List<String> serviceAddresses = preferences.getAutoConfirmSettings().serviceAddresses;
            txProofResultsPending.put(trade.getId(), serviceAddresses.size()); // need result from each service address
            for (String serviceAddress : serviceAddresses) {
                XmrProofInfo xmrProofInfo = new XmrProofInfo(
                        txHash,
                        txKey,
                        address,
                        amountXmr,
                        trade.getDate(),
                        confirmsRequired,
                        serviceAddress);
                xmrTransferProofService.requestProof(xmrProofInfo,
                        result -> {
                            if (!handleProofResult(result, trade))
                                xmrTransferProofService.terminateRequest(xmrProofInfo);
                        },
                        (errorMsg, throwable) -> {
                            log.warn(errorMsg);
                        }
                );
            }
        }
    }

    private boolean handleProofResult(XmrAutoConfirmResult result, Trade trade) {
        // here we count the Trade's API results from all
        // different serviceAddress and figure out when all have finished
        int resultsCountdown = txProofResultsPending.getOrDefault(trade.getId(), 0);
        if (resultsCountdown < 0) {   // see failure scenario below
            log.info("Ignoring stale API result [{}], tradeId {} due to previous error",
                    result.getState(), trade.getShortId());
            return false;   // terminate any pending responses
        }

        if (trade.isPayoutPublished()) {
            log.warn("Trade payout already published, shutting down all open API requests for this trade {}",
                    trade.getShortId());
            txProofResultsPending.remove(trade.getId());
            return false;
        }

        if (result.isPendingState()) {
            log.info("Auto confirm received a {} message for tradeId {}, retry will happen automatically",
                    result.getState(), trade.getShortId());
            trade.setAutoConfirmResult(result);         // this updates the GUI with the status..
            // Repeating the requests is handled in XmrTransferProofRequester
            return true;
        }

        if (result.isSuccessState()) {
            resultsCountdown -= 1;
            log.info("Received a {} message, remaining proofs needed: {}, tradeId {}",
                    result.getState(), resultsCountdown, trade.getShortId());
            if (resultsCountdown > 0) {
                txProofResultsPending.put(trade.getId(), resultsCountdown);   // track proof result count
                return true; // not all APIs have confirmed yet
            }
            // we've received the final PROOF_OK, all good here.
            txProofResultsPending.remove(trade.getId());
            trade.setAutoConfirmResult(result);            // this updates the GUI with the status..
            log.info("Auto confirm was successful, transitioning trade {} to next step...", trade.getShortId());
            if (!trade.isPayoutPublished()) {
                // note that this state can also be triggered by auto confirmation feature
                trade.setState(Trade.State.SELLER_CONFIRMED_IN_UI_FIAT_PAYMENT_RECEIPT);
            }
            accountAgeWitnessService.maybeSignWitness(trade);
            // transition the trade to step 4:
            ((SellerTrade) trade).onFiatPaymentReceived(() -> {
                    },
                    errorMessage -> {
                    });
            return true;
        }

        // error case.  any validation error from XmrProofRequester or XmrProofInfo.check
        // the following error codes will end up here:
        //   CONNECTION_FAIL, API_FAILURE, API_INVALID, TX_KEY_REUSED, TX_HASH_INVALID,
        //   TX_KEY_INVALID, ADDRESS_INVALID, NO_MATCH_FOUND, AMOUNT_NOT_MATCHING, TRADE_DATE_NOT_MATCHING
        log.warn("Tx Proof Failure {}, shutting down all open API requests for this trade {}",
                result.getState(), trade.getShortId());
        trade.setAutoConfirmResult(result);         // this updates the GUI with the status..
        resultsCountdown = -1;  // signal all API requesters to cease
        txProofResultsPending.put(trade.getId(), resultsCountdown);   // track proof result count
        return false;
    }

    private boolean isAutoConfDisabledByFilter() {
        return filterManager.getFilter() != null &&
                filterManager.getFilter().isDisableAutoConf();
    }
}
