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

import bisq.network.Socks5ProxyProvider;

import bisq.common.UserThread;
import bisq.common.app.Version;
import bisq.common.handlers.FaultHandler;
import bisq.common.util.Utilities;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonParser;

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import org.jetbrains.annotations.NotNull;

import javax.annotation.Nullable;

/**
 * Requests for the XMR tx proof for a particular trade and one service.
 * Repeats requests if tx is not confirmed yet.
 */
@Slf4j
class XmrTxProofRequest {
    ///////////////////////////////////////////////////////////////////////////////////////////
    // Enums
    ///////////////////////////////////////////////////////////////////////////////////////////

    enum Result {
        PENDING,    // Tx not visible in network yet, unconfirmed or not enough confirmations
        SUCCESS,    // Proof succeeded
        FAILED,     // Proof failed
        ERROR;      // Error from service, does not mean that proof failed

        @Nullable
        @Getter
        private Detail detail;

        Result with(Detail detail) {
            this.detail = detail;
            return this;
        }

        @Override
        public String toString() {
            return "Result{" +
                    "\n     detail=" + detail +
                    "\n} " + super.toString();
        }
    }

    enum Detail {
        // Pending
        TX_NOT_FOUND, // Tx not visible in network yet. Could be also other error
        PENDING_CONFIRMATIONS,

        // Error states
        CONNECTION_FAILURE,
        API_FAILURE,
        API_INVALID,

        // Failure states
        TX_HASH_INVALID,
        TX_KEY_INVALID,
        ADDRESS_INVALID,
        NO_MATCH_FOUND,
        AMOUNT_NOT_MATCHING,
        TRADE_DATE_NOT_MATCHING;

        @Getter
        private int numConfirmations;
        @Nullable
        @Getter
        private String errorMsg;

        public Detail error(String errorMsg) {
            this.errorMsg = errorMsg;
            return this;
        }

        public Detail numConfirmations(int numConfirmations) {
            this.numConfirmations = numConfirmations;
            return this;
        }

        @Override
        public String toString() {
            return "Detail{" +
                    "\n     numConfirmations=" + numConfirmations +
                    ",\n     errorMsg='" + errorMsg + '\'' +
                    "\n} " + super.toString();
        }
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Class fields
    ///////////////////////////////////////////////////////////////////////////////////////////

    private static final long REPEAT_REQUEST_PERIOD = TimeUnit.SECONDS.toMillis(90);
    private static final long MAX_REQUEST_PERIOD = TimeUnit.HOURS.toMillis(12);

    private final ListeningExecutorService executorService = Utilities.getListeningExecutorService(
            "XmrTransferProofRequester", 3, 5, 10 * 60);
    private final XmrTxProofHttpClient httpClient;
    private final XmrTxProofModel xmrTxProofModel;
    private final long firstRequest;

    private boolean terminated;
    @Getter
    private List<Result> results = new ArrayList<>();
    @Getter
    @Nullable
    private Result result;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    XmrTxProofRequest(Socks5ProxyProvider socks5ProxyProvider,
                      XmrTxProofModel xmrTxProofModel) {
        this.httpClient = new XmrTxProofHttpClient(socks5ProxyProvider);
        this.httpClient.setBaseUrl("http://" + xmrTxProofModel.getServiceAddress());
        if (xmrTxProofModel.getServiceAddress().matches("^192.*|^localhost.*")) {
            log.info("Ignoring Socks5 proxy for local net address: {}", xmrTxProofModel.getServiceAddress());
            this.httpClient.setIgnoreSocks5Proxy(true);
        }
        this.xmrTxProofModel = xmrTxProofModel;
        this.terminated = false;
        firstRequest = System.currentTimeMillis();
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // API
    ///////////////////////////////////////////////////////////////////////////////////////////

    // used by the service to abort further automatic retries
    void stop() {
        terminated = true;
    }

    public void start(Consumer<Result> resultHandler, FaultHandler faultHandler) {
        if (terminated) {
            // the XmrTransferProofService has asked us to terminate i.e. not make any further api calls
            // this scenario may happen if a re-request is scheduled from the callback below
            log.info("Request() aborted, this object has been terminated. Service: {}", httpClient.getBaseUrl());
            return;
        }

        ListenableFuture<Result> future = executorService.submit(() -> {
            Thread.currentThread().setName("XmrTransferProofRequest-" + xmrTxProofModel.getUID());
            String param = "/api/outputs?txhash=" + xmrTxProofModel.getTxHash() +
                    "&address=" + xmrTxProofModel.getRecipientAddress() +
                    "&viewkey=" + xmrTxProofModel.getTxKey() +
                    "&txprove=1";
            log.info("Requesting from {} with param {}", httpClient.getBaseUrl(), param);
            String json = httpClient.requestWithGET(param, "User-Agent", "bisq/" + Version.VERSION);
            String prettyJson = new GsonBuilder().setPrettyPrinting().create().toJson(new JsonParser().parse(json));
            log.info("Response json\n{}", prettyJson);
            Result result = XmrTxProofParser.parse(xmrTxProofModel, json);
            log.info("xmrTxProofResult {}", result);
            return result;
        });

        Futures.addCallback(future, new FutureCallback<>() {
            public void onSuccess(Result result) {
                XmrTxProofRequest.this.result = result;

                if (terminated) {
                    log.info("We received result {} but request to {} was terminated already.", result, httpClient.getBaseUrl());
                    return;
                }
                results.add(result);
                switch (result) {
                    case PENDING:
                        if (System.currentTimeMillis() - firstRequest > MAX_REQUEST_PERIOD) {
                            log.warn("We have tried requesting from {} for too long, giving up.", httpClient.getBaseUrl());
                            return;
                        } else {
                            UserThread.runAfter(() -> start(resultHandler, faultHandler), REPEAT_REQUEST_PERIOD, TimeUnit.MILLISECONDS);
                        }
                        UserThread.execute(() -> resultHandler.accept(result));
                        break;
                    case SUCCESS:
                    case FAILED:
                    case ERROR:
                        UserThread.execute(() -> resultHandler.accept(result));
                        break;
                    default:
                        log.warn("Unexpected result {}", result);
                }
            }

            public void onFailure(@NotNull Throwable throwable) {
                String errorMessage = "Request to " + httpClient.getBaseUrl() + " failed";
                faultHandler.handleFault(errorMessage, throwable);
                UserThread.execute(() ->
                        resultHandler.accept(Result.ERROR.with(Detail.CONNECTION_FAILURE.error(errorMessage))));
            }
        });
    }

    String getUID() {
        return xmrTxProofModel.getUID();
    }

    String getServiceAddress() {
        return xmrTxProofModel.getServiceAddress();
    }

    int numSuccessResults() {
        return (int) results.stream().filter(e -> e == XmrTxProofRequest.Result.SUCCESS).count();
    }

    @Override
    public String toString() {
        return "XmrTxProofRequest{" +
                ",\n     httpClient=" + httpClient +
                ",\n     xmrTxProofModel=" + xmrTxProofModel +
                ",\n     firstRequest=" + firstRequest +
                ",\n     terminated=" + terminated +
                ",\n     result=" + result +
                "\n}";
    }
}
