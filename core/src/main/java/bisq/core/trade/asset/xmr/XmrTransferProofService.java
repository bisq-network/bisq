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

import bisq.network.Socks5ProxyProvider;

import bisq.common.handlers.FaultHandler;

import javax.inject.Inject;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

import lombok.extern.slf4j.Slf4j;

import javax.annotation.Nullable;

/**
 * Manages the XMR transfers proof requests for multiple trades.
 */
@Slf4j
public class XmrTransferProofService {
    private Map<String, XmrTransferProofRequester> map = new HashMap<>();
    private Socks5ProxyProvider socks5ProxyProvider;

    @Inject
    public XmrTransferProofService(@Nullable Socks5ProxyProvider provider) {
        socks5ProxyProvider = provider;
    }

    public void requestProof(XmrProofInfo xmrProofInfo,
                             Consumer<XmrProofResult> resultHandler,
                             FaultHandler faultHandler) {
        String key = xmrProofInfo.getKey();
        if (map.containsKey(key)) {
            log.warn("We started a proof request for trade with ID {} already", key);
            return;
        }
        log.info("requesting tx proof for " + key);

        XmrTransferProofRequester requester = new XmrTransferProofRequester(
                socks5ProxyProvider,
                xmrProofInfo,
                result -> {
                    if (result.isSuccessState())
                        cleanup(key);
                    resultHandler.accept(result);
                },
                (errorMsg, throwable) -> {
                    cleanup(key);
                    faultHandler.handleFault(errorMsg, throwable);
                });
        map.put(key, requester);
        requester.request();
    }

    public void terminateRequest(XmrProofInfo xmrProofInfo) {
        String key = xmrProofInfo.getKey();
        XmrTransferProofRequester requester = map.getOrDefault(key, null);
        if (requester != null) {
            log.info("Terminating API request for {}", key);
            requester.stop();
            cleanup(key);
        }
    }
    private void cleanup(String identifier) {
        map.remove(identifier);
    }
}
