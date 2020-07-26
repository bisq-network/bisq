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

import bisq.common.handlers.FaultHandler;

import javax.inject.Inject;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

import lombok.extern.slf4j.Slf4j;

/**
 * Manages the XMR transfers proof requests for multiple trades.
 */
@Slf4j
public class XmrTransferProofService {
    private final XmrTxProofHttpClient httpClient;
    private Map<String, XmrTransferProofRequester> map = new HashMap<>();

    @Inject
    public XmrTransferProofService(XmrTxProofHttpClient httpClient) {
        this.httpClient = httpClient;
        //this.httpClient.setBaseUrl("http://139.59.140.37:8081");
        this.httpClient.setBaseUrl("http://127.0.0.1:8081");
        this.httpClient.setIgnoreSocks5Proxy(false);
    }

    public void requestProof(String tradeId,
                             String txHash,
                             String txKey,
                             String recipientAddress,
                             long amount,
                             Consumer<XmrProofResult> resultHandler,
                             FaultHandler faultHandler) {
        if (map.containsKey(tradeId)) {
            log.warn("We started a proof request for trade with ID {} already", tradeId);
            return;
        }

        XmrTransferProofRequester requester = new XmrTransferProofRequester(httpClient,
                txHash,
                txKey,
                recipientAddress,
                amount,
                result -> {
                    cleanup(tradeId);
                    resultHandler.accept(result);
                },
                (errorMsg, throwable) -> {
                    cleanup(tradeId);
                    faultHandler.handleFault(errorMsg, throwable);
                });
        map.put(tradeId, requester);
        requester.request();
    }

    private void cleanup(String tradeId) {
        map.remove(tradeId);
    }
}
