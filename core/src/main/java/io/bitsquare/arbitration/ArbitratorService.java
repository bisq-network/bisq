/*
 * This file is part of Bitsquare.
 *
 * Bitsquare is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * Bitsquare is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Bitsquare. If not, see <http://www.gnu.org/licenses/>.
 */

package io.bitsquare.arbitration;

import io.bitsquare.common.handlers.ErrorMessageHandler;
import io.bitsquare.common.handlers.ResultHandler;
import io.bitsquare.p2p.Address;
import io.bitsquare.p2p.P2PService;
import io.bitsquare.p2p.storage.HashSetChangedListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Used to store arbitrators profile and load map of arbitrators
 */
public class ArbitratorService {
    private static final Logger log = LoggerFactory.getLogger(ArbitratorService.class);

    private P2PService p2PService;

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

    public void addHashSetChangedListener(HashSetChangedListener hashSetChangedListener) {
        p2PService.addHashSetChangedListener(hashSetChangedListener);
    }

    public void addArbitrator(Arbitrator arbitrator, final ResultHandler resultHandler, ErrorMessageHandler errorMessageHandler) {
        log.debug("addArbitrator arbitrator.hashCode() " + arbitrator.hashCode());
        boolean result = p2PService.addData(arbitrator);
        if (result) {
            log.trace("Add arbitrator to network was successful. Arbitrator = " + arbitrator);
            resultHandler.handleResult();
        } else {
            errorMessageHandler.handleErrorMessage("Add arbitrator failed");
        }
    }

    public void removeArbitrator(Arbitrator arbitrator, ResultHandler resultHandler, ErrorMessageHandler errorMessageHandler) {
        log.debug("removeArbitrator arbitrator.hashCode() " + arbitrator.hashCode());
        if (p2PService.removeData(arbitrator)) {
            log.trace("Remove arbitrator from network was successful. Arbitrator = " + arbitrator);
            resultHandler.handleResult();
        } else {
            errorMessageHandler.handleErrorMessage("Remove arbitrator failed");
        }
    }

    P2PService getP2PService() {
        return p2PService;
    }

    public Map<Address, Arbitrator> getArbitrators() {
        final Map<Address, Arbitrator> arbitratorsMap = p2PService.getDataMap().values().stream()
                .filter(e -> e.expirablePayload instanceof Arbitrator)
                .map(e -> (Arbitrator) e.expirablePayload)
                .collect(Collectors.toMap(e -> e.getArbitratorAddress(), e -> e));

        return arbitratorsMap;
    }
}
