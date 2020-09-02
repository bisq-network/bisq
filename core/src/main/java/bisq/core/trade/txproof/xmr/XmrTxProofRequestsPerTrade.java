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

import bisq.core.locale.Res;
import bisq.core.trade.Trade;
import bisq.core.trade.txproof.AssetTxProofHttpClient;
import bisq.core.trade.txproof.AssetTxProofRequestsPerTrade;
import bisq.core.trade.txproof.AssetTxProofResult;
import bisq.core.user.AutoConfirmSettings;

import bisq.common.handlers.FaultHandler;

import org.bitcoinj.core.Coin;

import javafx.beans.value.ChangeListener;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

import lombok.extern.slf4j.Slf4j;

/**
 * Handles the XMR tx proof requests for multiple services per trade.
 */
@Slf4j
class XmrTxProofRequestsPerTrade implements AssetTxProofRequestsPerTrade {
    private final Trade trade;
    private final AutoConfirmSettings autoConfirmSettings;
    private final AssetTxProofHttpClient httpClient;

    private int numRequiredSuccessResults;
    private final Set<XmrTxProofRequest> requests = new HashSet<>();

    private int numSuccessResults;
    private ChangeListener<Trade.State> tradeStateListener;
    private AutoConfirmSettings.Listener autoConfirmSettingsListener;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    XmrTxProofRequestsPerTrade(AssetTxProofHttpClient httpClient,
                               Trade trade,
                               AutoConfirmSettings autoConfirmSettings) {
        this.httpClient = httpClient;
        this.trade = trade;
        this.autoConfirmSettings = autoConfirmSettings;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // API
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void requestFromAllServices(Consumer<AssetTxProofResult> resultHandler, FaultHandler faultHandler) {
        // We set serviceAddresses at request time. If user changes AutoConfirmSettings after request has started
        // it will have no impact on serviceAddresses and numRequiredSuccessResults.
        // Thought numRequiredConfirmations can be changed during request process and will be read from
        // autoConfirmSettings at result parsing.
        List<String> serviceAddresses = autoConfirmSettings.getServiceAddresses();
        numRequiredSuccessResults = serviceAddresses.size();

        if (isTradeAmountAboveLimit(trade)) {
            callResultHandlerAndMaybeTerminate(resultHandler, AssetTxProofResult.TRADE_LIMIT_EXCEEDED);
            return;
        }

        if (trade.isPayoutPublished()) {
            callResultHandlerAndMaybeTerminate(resultHandler, AssetTxProofResult.PAYOUT_TX_ALREADY_PUBLISHED);
            return;
        }

        // We will stop all our services if the user changes the enable state in the AutoConfirmSettings
        autoConfirmSettingsListener = () -> {
            if (!autoConfirmSettings.isEnabled()) {
                callResultHandlerAndMaybeTerminate(resultHandler, AssetTxProofResult.FEATURE_DISABLED);
            }
        };
        autoConfirmSettings.addListener(autoConfirmSettingsListener);
        if (!autoConfirmSettings.isEnabled()) {
            callResultHandlerAndMaybeTerminate(resultHandler, AssetTxProofResult.FEATURE_DISABLED);
            return;
        }

        tradeStateListener = (observable, oldValue, newValue) -> {
            if (trade.isPayoutPublished()) {
                callResultHandlerAndMaybeTerminate(resultHandler, AssetTxProofResult.PAYOUT_TX_ALREADY_PUBLISHED);
            }
        };
        trade.stateProperty().addListener(tradeStateListener);

        callResultHandlerAndMaybeTerminate(resultHandler, AssetTxProofResult.REQUESTS_STARTED);

        for (String serviceAddress : serviceAddresses) {
            XmrTxProofModel model = new XmrTxProofModel(trade, serviceAddress, autoConfirmSettings);
            XmrTxProofRequest request = new XmrTxProofRequest(httpClient, model);

            log.info("{} created", request);
            requests.add(request);

            request.requestFromService(result -> {
                        AssetTxProofResult assetTxProofResult;
                        if (trade.isPayoutPublished()) {
                            assetTxProofResult = AssetTxProofResult.PAYOUT_TX_ALREADY_PUBLISHED;
                            callResultHandlerAndMaybeTerminate(resultHandler, assetTxProofResult);
                            return;
                        }

                        switch (result) {
                            case PENDING:
                                // We expect repeated PENDING results with different details
                                assetTxProofResult = getAssetTxProofResultForPending(result);
                                break;
                            case SUCCESS:
                                numSuccessResults++;
                                if (numSuccessResults < numRequiredSuccessResults) {
                                    // Request is success but not all have completed yet.
                                    int remaining = numRequiredSuccessResults - numSuccessResults;
                                    log.info("{} succeeded. We have {} remaining request(s) open.",
                                            request, remaining);
                                    assetTxProofResult = getAssetTxProofResultForPending(result);
                                } else {
                                    // All our services have returned a SUCCESS result so we
                                    // have completed on the service level.
                                    log.info("All {} tx proof requests for trade {} have been successful.",
                                            numRequiredSuccessResults, trade.getShortId());
                                    assetTxProofResult = AssetTxProofResult.COMPLETED;
                                }
                                break;
                            case FAILED:
                                log.warn("{} failed. " +
                                                "This might not mean that the XMR transfer was invalid but you have to check yourself " +
                                                "if the XMR transfer was correct. {}",
                                        request, result);

                                assetTxProofResult = AssetTxProofResult.FAILED;
                                break;
                            case ERROR:
                            default:
                                log.warn("{} resulted in an error. " +
                                                "This might not mean that the XMR transfer was invalid but can be a network or " +
                                                "service problem. {}",
                                        request, result);

                                assetTxProofResult = AssetTxProofResult.ERROR;
                                break;
                        }

                        callResultHandlerAndMaybeTerminate(resultHandler, assetTxProofResult);
                    },
                    faultHandler);
        }
    }

    @Override
    public void terminate() {
        requests.forEach(XmrTxProofRequest::terminate);
        requests.clear();
        if (tradeStateListener != null) {
            trade.stateProperty().removeListener(tradeStateListener);
        }
        if (autoConfirmSettingsListener != null) {
            autoConfirmSettings.removeListener(autoConfirmSettingsListener);
        }
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Private
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void callResultHandlerAndMaybeTerminate(Consumer<AssetTxProofResult> resultHandler,
                                                    AssetTxProofResult assetTxProofResult) {
        resultHandler.accept(assetTxProofResult);
        if (assetTxProofResult.isTerminal()) {
            terminate();
        }
    }

    private AssetTxProofResult getAssetTxProofResultForPending(XmrTxProofRequest.Result result) {
        XmrTxProofRequest.Detail detail = result.getDetail();
        int numConfirmations = detail != null ? detail.getNumConfirmations() : 0;
        log.info("{} returned with numConfirmations {}",
                result, numConfirmations);

        String detailString = "";
        if (XmrTxProofRequest.Detail.PENDING_CONFIRMATIONS == detail) {
            detailString = Res.get("portfolio.pending.autoConf.state.confirmations",
                    numConfirmations, autoConfirmSettings.getRequiredConfirmations());

        } else if (XmrTxProofRequest.Detail.TX_NOT_FOUND == detail) {
            detailString = Res.get("portfolio.pending.autoConf.state.txNotFound");
        }

        return AssetTxProofResult.PENDING
                .numSuccessResults(numSuccessResults)
                .numRequiredSuccessResults(numRequiredSuccessResults)
                .details(detailString);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Validation
    ///////////////////////////////////////////////////////////////////////////////////////////

    private boolean isTradeAmountAboveLimit(Trade trade) {
        Coin tradeAmount = trade.getTradeAmount();
        Coin tradeLimit = Coin.valueOf(autoConfirmSettings.getTradeLimit());
        if (tradeAmount != null && tradeAmount.isGreaterThan(tradeLimit)) {
            log.warn("Trade amount {} is higher than limit from auto-conf setting {}.",
                    tradeAmount.toFriendlyString(), tradeLimit.toFriendlyString());
            return true;
        }
        return false;
    }
}
