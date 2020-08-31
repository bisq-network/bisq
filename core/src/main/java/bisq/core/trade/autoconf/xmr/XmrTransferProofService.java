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

import bisq.common.handlers.FaultHandler;

import javax.inject.Inject;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

import lombok.extern.slf4j.Slf4j;

/**
 * Manages the XMR transfers proof requests for multiple trades and multiple services.
 */
@Slf4j
class XmrTransferProofService {
    private final Map<String, XmrTransferProofRequest> map = new HashMap<>();
    private final Socks5ProxyProvider socks5ProxyProvider;

    @Inject
    private XmrTransferProofService(Socks5ProxyProvider provider) {
        socks5ProxyProvider = provider;
    }

    void requestProof(XmrProofInfo xmrProofInfo,
                      Consumer<XmrAutoConfirmResult> resultHandler,
                      FaultHandler faultHandler) {
        String uid = xmrProofInfo.getUID();
        if (map.containsKey(uid)) {
            log.warn("We started a proof request for uid {} already", uid);
            return;
        }
        log.info("requesting tx proof with uid {}", uid);

        XmrTransferProofRequest requester = new XmrTransferProofRequest(
                socks5ProxyProvider,
                xmrProofInfo,
                result -> {
                    if (result.isSuccessState()) {
                        cleanup(uid);
                    }
                    resultHandler.accept(result);
                },
                (errorMsg, throwable) -> {
                    cleanup(uid);
                    faultHandler.handleFault(errorMsg, throwable);
                });
        map.put(uid, requester);
        requester.request();
    }

    void terminateRequest(XmrProofInfo xmrProofInfo) {
        String uid = xmrProofInfo.getUID();
        XmrTransferProofRequest requester = map.getOrDefault(uid, null);
        if (requester != null) {
            log.info("Terminating API request for request with uid {}", uid);
            requester.stop();
            cleanup(uid);
        }
    }

    private void cleanup(String key) {
        map.remove(key);
    }
}
