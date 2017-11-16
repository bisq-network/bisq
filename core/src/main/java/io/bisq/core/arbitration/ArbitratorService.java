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

package io.bisq.core.arbitration;

import io.bisq.common.app.DevEnv;
import io.bisq.common.handlers.ErrorMessageHandler;
import io.bisq.common.handlers.ResultHandler;
import io.bisq.common.util.Utilities;
import io.bisq.core.app.BisqEnvironment;
import io.bisq.core.filter.FilterManager;
import io.bisq.network.p2p.NodeAddress;
import io.bisq.network.p2p.P2PService;
import io.bisq.network.p2p.storage.HashMapChangedListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Used to store arbitrators profile and load map of arbitrators
 */
public class ArbitratorService {
    private static final Logger log = LoggerFactory.getLogger(ArbitratorService.class);

    private final P2PService p2PService;
    private final FilterManager filterManager;

    interface ArbitratorMapResultHandler {
        void handleResult(Map<String, Arbitrator> arbitratorsMap);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    public ArbitratorService(P2PService p2PService, FilterManager filterManager) {
        this.p2PService = p2PService;
        this.filterManager = filterManager;
    }

    public void addHashSetChangedListener(HashMapChangedListener hashMapChangedListener) {
        p2PService.addHashSetChangedListener(hashMapChangedListener);
    }

    public void addArbitrator(Arbitrator arbitrator, final ResultHandler resultHandler, ErrorMessageHandler errorMessageHandler) {
        log.debug("addArbitrator arbitrator.hashCode() " + arbitrator.hashCode());
        if (!BisqEnvironment.getBaseCurrencyNetwork().isMainnet() ||
                !Utilities.encodeToHex(arbitrator.getRegistrationPubKey()).equals(DevEnv.DEV_PRIVILEGE_PUB_KEY)) {
            boolean result = p2PService.addProtectedStorageEntry(arbitrator, true);
            if (result) {
                log.trace("Add arbitrator to network was successful. Arbitrator.hashCode() = " + arbitrator.hashCode());
                resultHandler.handleResult();
            } else {
                errorMessageHandler.handleErrorMessage("Add arbitrator failed");
            }
        } else {
            log.error("Attempt to publish dev arbitrator on mainnet.");
            errorMessageHandler.handleErrorMessage("Add arbitrator failed. Attempt to publish dev arbitrator on mainnet.");
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
        final List<String> bannedArbitrators = filterManager.getFilter() != null ? filterManager.getFilter().getArbitrators() : null;
        if (bannedArbitrators != null)
            log.warn("bannedArbitrators=" + bannedArbitrators);
        Set<Arbitrator> arbitratorSet = p2PService.getDataMap().values().stream()
                .filter(data -> data.getProtectedStoragePayload() instanceof Arbitrator)
                .map(data -> (Arbitrator) data.getProtectedStoragePayload())
                .filter(a -> bannedArbitrators == null ||
                        !bannedArbitrators.contains(a.getNodeAddress().getHostName()))
                .collect(Collectors.toSet());

        Map<NodeAddress, Arbitrator> map = new HashMap<>();
        for (Arbitrator arbitrator : arbitratorSet) {
            NodeAddress arbitratorNodeAddress = arbitrator.getNodeAddress();
            if (!map.containsKey(arbitratorNodeAddress))
                map.put(arbitratorNodeAddress, arbitrator);
            else
                log.warn("arbitratorAddress already exist in arbitrator map. Seems an arbitrator object is already registered with the same address.");
        }
        return map;
    }
}
