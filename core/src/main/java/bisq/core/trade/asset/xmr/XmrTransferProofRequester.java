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

package bisq.core.trade.asset.xmr;

import bisq.common.UserThread;
import bisq.common.app.Version;
import bisq.common.handlers.FaultHandler;
import bisq.common.util.Utilities;

import javax.inject.Singleton;

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;

import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import lombok.extern.slf4j.Slf4j;

import org.jetbrains.annotations.NotNull;

@Slf4j
@Singleton
class XmrTransferProofRequester {

    private final ListeningExecutorService executorService = Utilities.getListeningExecutorService(
            "XmrTransferProofService", 3, 5, 10 * 60);
    private final XmrTxProofHttpClient httpClient;
    private final String txHash;
    private final String txKey;
    private final String recipientAddress;
    private final long amount;
    private final Consumer<XmrProofResult> resultHandler;
    private final FaultHandler faultHandler;

    private long firstRequest;
    //todo dev settings
    private long REPEAT_REQUEST_SEC = TimeUnit.SECONDS.toMillis(5);
    private long MAX_REQUEST_PERIOD = TimeUnit.HOURS.toMillis(12);


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    XmrTransferProofRequester(XmrTxProofHttpClient httpClient,
                              String txHash,
                              String txKey,
                              String recipientAddress,
                              long amount,
                              Consumer<XmrProofResult> resultHandler,
                              FaultHandler faultHandler) {
        this.httpClient = httpClient;
        this.txHash = txHash;
        this.txKey = txKey;
        this.recipientAddress = recipientAddress;
        this.amount = amount;
        this.resultHandler = resultHandler;
        this.faultHandler = faultHandler;
        firstRequest = System.currentTimeMillis();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // API
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void request() {
        // todo dev test address for a real tx proof
        /*
        txID: 5e665addf6d7c6300670e8a89564ed12b5c1a21c336408e2835668f9a6a0d802
        txKey: f3ce66c9d395e5e460c8802b2c3c1fff04e508434f9738ee35558aac4678c906
        address: 85q13WDADXE26W6h7cStpPMkn8tWpvWgHbpGWWttFEafGXyjsBTXxxyQms4UErouTY5sdKpYHVjQm6SagiCqytseDkzfgub
        ammount : 8.90259736 XMR
         */

        ListenableFuture<XmrProofResult> future = executorService.submit(() -> {
            Thread.currentThread().setName("XmrTransferProofRequest-" + this.toString());
            String param = "/api/outputs?txhash=" + txHash +
                    "&address=" + recipientAddress +
                    "&viewkey=" + txKey +
                    "&txprove=1";
            String json = httpClient.requestWithGET(param, "User-Agent", "bisq/" + Version.VERSION);
            Thread.sleep(3000);

            //

            return parseResult(json);
        });

        Futures.addCallback(future, new FutureCallback<>() {
            public void onSuccess(XmrProofResult result) {
                if (result == XmrProofResult.TX_NOT_CONFIRMED && System.currentTimeMillis() - firstRequest < MAX_REQUEST_PERIOD) {
                    UserThread.runAfter(() -> request(), REPEAT_REQUEST_SEC);
                } else {
                    UserThread.execute(() -> resultHandler.accept(result));
                }
            }

            public void onFailure(@NotNull Throwable throwable) {
                String errorMessage = "Request to " + httpClient.getBaseUrl() + " failed";
                faultHandler.handleFault(errorMessage, throwable);
            }
        });
    }

    private XmrProofResult parseResult(String json) {
        //TODO parse json
        //TODO need service to check diff. error conditions
        return XmrProofResult.PROOF_OK;
        // check recipientAddress and amount
        // json example
                    /*

{
    "data": {
    "address": "42f18fc61586554095b0799b5c4b6f00cdeb26a93b20540d366932c6001617b75db35109fbba7d5f275fef4b9c49e0cc1c84b219ec6ff652fda54f89f7f63c88",
    "outputs": [
      {
        "amount": 34980000000000,
        "match": true,
        "output_idx": 0,
        "output_pubkey": "35d7200229e725c2bce0da3a2f20ef0720d242ecf88bfcb71eff2025c2501fdb"
      },
      {
        "amount": 0,
        "match": false,
        "output_idx": 1,
        "output_pubkey": "44efccab9f9b42e83c12da7988785d6c4eb3ec6e7aa2ae1234e2f0f7cb9ed6dd"
      }
    ],
    "tx_hash": "17049bc5f2d9fbca1ce8dae443bbbbed2fc02f1ee003ffdd0571996905faa831",
    "tx_prove": false,
    "viewkey": "f359631075708155cc3d92a32b75a7d02a5dcf27756707b47a2b31b21c389501"
  },
  "status": "success"
}

             */
    }
}
