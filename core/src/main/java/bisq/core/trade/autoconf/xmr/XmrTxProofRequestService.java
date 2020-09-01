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

import javax.inject.Inject;

import java.util.HashMap;
import java.util.Map;

import lombok.extern.slf4j.Slf4j;

import javax.annotation.Nullable;

/**
 * Handles the XMR tx proof requests for multiple trades and multiple services.
 */
@Slf4j
class XmrTxProofRequestService {
    private final Map<String, XmrTxProofRequest> map = new HashMap<>();
    private final Socks5ProxyProvider socks5ProxyProvider;

    @Inject
    public XmrTxProofRequestService(Socks5ProxyProvider provider) {
        socks5ProxyProvider = provider;
    }

    @Nullable
    XmrTxProofRequest getRequest(XmrTxProofModel model) {
        XmrTxProofRequest request = new XmrTxProofRequest(socks5ProxyProvider, model);
        String uid = request.getUID();
        if (map.containsKey(uid)) {
            log.warn("We started a proof request for uid {} already", uid);
            return null;
        }

        map.put(uid, request);
        return request;
    }

    // Get number of requests with at least 1 SUCCESS result
    int numRequestsWithSuccessResult() {
        return (int) map.values().stream()
                .filter(request -> request.numSuccessResults() > 0)
                .count();
    }

    void stopAllRequest() {
        map.values().forEach(XmrTxProofRequest::stop);
        map.clear();
    }
}
