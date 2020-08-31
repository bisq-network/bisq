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

import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import lombok.extern.slf4j.Slf4j;

import org.jetbrains.annotations.NotNull;

/**
 * Requests for the XMR tx proof for a particular trade and one service.
 * Repeats requests if tx is not confirmed yet.
 */
@Slf4j
class XmrTxProofRequest {
    // these settings are not likely to change and therefore not put into Config
    private static final long REPEAT_REQUEST_PERIOD = TimeUnit.SECONDS.toMillis(90);
    private static final long MAX_REQUEST_PERIOD = TimeUnit.HOURS.toMillis(12);

    private final ListeningExecutorService executorService = Utilities.getListeningExecutorService(
            "XmrTransferProofRequester", 3, 5, 10 * 60);
    private final XmrTxProofHttpClient httpClient;
    private final XmrTxProofModel xmrTxProofModel;
    private final Consumer<XmrTxProofResult> resultHandler;
    private final FaultHandler faultHandler;

    private boolean terminated;
    private final long firstRequest;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    XmrTxProofRequest(Socks5ProxyProvider socks5ProxyProvider,
                      XmrTxProofModel xmrTxProofModel,
                      Consumer<XmrTxProofResult> resultHandler,
                      FaultHandler faultHandler) {
        this.httpClient = new XmrTxProofHttpClient(socks5ProxyProvider);
        this.httpClient.setBaseUrl("http://" + xmrTxProofModel.getServiceAddress());
        if (xmrTxProofModel.getServiceAddress().matches("^192.*|^localhost.*")) {
            log.info("Ignoring Socks5 proxy for local net address: {}", xmrTxProofModel.getServiceAddress());
            this.httpClient.setIgnoreSocks5Proxy(true);
        }
        this.xmrTxProofModel = xmrTxProofModel;
        this.resultHandler = resultHandler;
        this.faultHandler = faultHandler;
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

    public void request() {
        if (terminated) {
            // the XmrTransferProofService has asked us to terminate i.e. not make any further api calls
            // this scenario may happen if a re-request is scheduled from the callback below
            log.info("Request() aborted, this object has been terminated. Service: {}", httpClient.getBaseUrl());
            return;
        }
        ListenableFuture<XmrTxProofResult> future = executorService.submit(() -> {
            Thread.currentThread().setName("XmrTransferProofRequest-" + xmrTxProofModel.getUID());
            String param = "/api/outputs?txhash=" + xmrTxProofModel.getTxHash() +
                    "&address=" + xmrTxProofModel.getRecipientAddress() +
                    "&viewkey=" + xmrTxProofModel.getTxKey() +
                    "&txprove=1";
            log.info("Requesting from {} with param {}", httpClient.getBaseUrl(), param);
            String json = httpClient.requestWithGET(param, "User-Agent", "bisq/" + Version.VERSION);
            String prettyJson = new GsonBuilder().setPrettyPrinting().create().toJson(new JsonParser().parse(json));
            log.info("Response json\n{}", prettyJson);
            XmrTxProofResult xmrTxProofResult = XmrTxProofParser.parse(xmrTxProofModel, json);
            log.info("xmrTxProofResult {}", xmrTxProofResult);
            return xmrTxProofResult;
        });

        Futures.addCallback(future, new FutureCallback<>() {
            public void onSuccess(XmrTxProofResult result) {
                if (terminated) {
                    log.info("API terminated from higher level: {}", httpClient.getBaseUrl());
                    return;
                }
                if (System.currentTimeMillis() - firstRequest > MAX_REQUEST_PERIOD) {
                    log.warn("We have tried requesting from {} for too long, giving up.", httpClient.getBaseUrl());
                    return;
                }
                if (result.isPendingState()) {
                    UserThread.runAfter(() -> request(), REPEAT_REQUEST_PERIOD, TimeUnit.MILLISECONDS);
                }
                UserThread.execute(() -> resultHandler.accept(result));
            }

            public void onFailure(@NotNull Throwable throwable) {
                String errorMessage = "Request to " + httpClient.getBaseUrl() + " failed";
                faultHandler.handleFault(errorMessage, throwable);
                UserThread.execute(() -> resultHandler.accept(
                        new XmrTxProofResult(XmrTxProofResult.State.CONNECTION_FAIL, errorMessage)));
            }
        });
    }
}
