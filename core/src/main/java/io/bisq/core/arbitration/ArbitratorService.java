/*
 * This file is part of bisq.
 *
 * bisq is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * bisq is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with bisq. If not, see <http://www.gnu.org/licenses/>.
 */

package io.bisq.core.arbitration;

import io.bisq.common.handlers.ErrorMessageHandler;
import io.bisq.common.handlers.ResultHandler;
import io.bisq.network.p2p.storage.HashMapChangedListener;
import io.bisq.network.p2p.storage.P2PService;
import io.bisq.protobuffer.payload.arbitration.Arbitrator;
import io.bisq.protobuffer.payload.p2p.NodeAddress;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Used to store arbitrators profile and load map of arbitrators
 */
public class ArbitratorService {
    private static final Logger log = LoggerFactory.getLogger(ArbitratorService.class);

    private final P2PService p2PService;

    interface ArbitratorMapResultHandler {
        void handleResult(Map<String, Arbitrator> arbitratorsMap);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    public ArbitratorService(P2PService p2PService) {
        this.p2PService = p2PService;
    }

    public void addHashSetChangedListener(HashMapChangedListener hashMapChangedListener) {
        p2PService.addHashSetChangedListener(hashMapChangedListener);
    }

    public void addArbitrator(Arbitrator arbitrator, final ResultHandler resultHandler, ErrorMessageHandler errorMessageHandler) {
        log.debug("addArbitrator arbitrator.hashCode() " + arbitrator.hashCode());
        boolean result = p2PService.addData(arbitrator, true);
        if (result) {
            log.trace("Add arbitrator to network was successful. Arbitrator.hashCode() = " + arbitrator.hashCode());
            resultHandler.handleResult();
        } else {
            errorMessageHandler.handleErrorMessage("Add arbitrator failed");
        }
    }

    public void removeArbitrator(Arbitrator arbitrator, ResultHandler resultHandler, ErrorMessageHandler errorMessageHandler) {
        log.debug("removeArbitrator arbitrator.hashCode() " + arbitrator.hashCode());
        if (p2PService.removeData(arbitrator, true)) {
            log.trace("Remove arbitrator from network was successful. Arbitrator.hashCode() = " + arbitrator.hashCode());
            resultHandler.handleResult();
        } else {
            errorMessageHandler.handleErrorMessage("Remove arbitrator failed");
        }
    }

    P2PService getP2PService() {
        return p2PService;
    }

    public Map<NodeAddress, Arbitrator> getArbitrators() {
        Set<Arbitrator> arbitratorSet = p2PService.getDataMap().values().stream()
                .filter(data -> data.getStoragePayload() instanceof Arbitrator)
                .map(data -> (Arbitrator) data.getStoragePayload())
                .collect(Collectors.toSet());

        Map<NodeAddress, Arbitrator> map = new HashMap<>();
        for (Arbitrator arbitrator : arbitratorSet) {
            NodeAddress arbitratorNodeAddress = arbitrator.getArbitratorNodeAddress();
            if (!map.containsKey(arbitratorNodeAddress))
                map.put(arbitratorNodeAddress, arbitrator);
            else
                log.warn("arbitratorAddress already exist in arbitrator map. Seems an arbitrator object is already registered with the same address.");
        }
        return map;
    }
}
