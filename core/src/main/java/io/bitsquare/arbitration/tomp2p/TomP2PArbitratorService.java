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

package io.bitsquare.arbitration.tomp2p;

import io.bitsquare.arbitration.Arbitrator;
import io.bitsquare.arbitration.ArbitratorService;
import io.bitsquare.common.handlers.ErrorMessageHandler;
import io.bitsquare.common.handlers.ResultHandler;
import io.bitsquare.p2p.tomp2p.TomP2PDHTService;
import io.bitsquare.p2p.tomp2p.TomP2PNode;
import io.bitsquare.user.User;

import java.io.IOException;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import javax.inject.Inject;

import net.tomp2p.dht.FutureGet;
import net.tomp2p.dht.FuturePut;
import net.tomp2p.dht.FutureRemove;
import net.tomp2p.futures.BaseFuture;
import net.tomp2p.futures.BaseFutureAdapter;
import net.tomp2p.peers.Number160;
import net.tomp2p.storage.Data;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class TomP2PArbitratorService extends TomP2PDHTService implements ArbitratorService {
    private static final Logger log = LoggerFactory.getLogger(TomP2PArbitratorService.class);

    private static final Number160 LOCATION_KEY = Number160.createHash("ArbitratorService");

    private final CopyOnWriteArrayList<Listener> listeners = new CopyOnWriteArrayList<>();

    @Inject
    public TomP2PArbitratorService(TomP2PNode tomP2PNode, User user) {
        super(tomP2PNode, user);
    }

    public void addArbitrator(Arbitrator arbitrator, ResultHandler resultHandler, ErrorMessageHandler errorMessageHandler) {
        try {
            final Data arbitratorData = new Data(arbitrator);

            FuturePut addFuture = addProtectedDataToMap(LOCATION_KEY, arbitratorData);
            addFuture.addListener(new BaseFutureAdapter<BaseFuture>() {
                @Override
                public void operationComplete(BaseFuture future) throws Exception {
                    if (future.isSuccess()) {
                        log.trace("Add arbitrator to DHT was successful. Stored data: [key: " + LOCATION_KEY + ", " +
                                "values: " + arbitratorData + "]");
                        Object arbitratorDataObject = arbitratorData.object();
                        if (arbitratorDataObject instanceof Arbitrator) {
                            Arbitrator result = (Arbitrator) arbitratorDataObject;
                            executor.execute(() -> {
                                resultHandler.handleResult();
                                listeners.stream().forEach(listener -> listener.onArbitratorAdded(result));
                            });
                        }
                    }
                    else {
                        log.error("Add arbitrator to DHT failed with reason:" + addFuture.failedReason());
                        errorMessageHandler.handleErrorMessage("Add arbitrator to DHT failed with reason:" + addFuture.failedReason());
                    }
                }
            });
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void removeArbitrator(Arbitrator arbitrator) throws IOException {
        final Data arbitratorData = new Data(arbitrator);
        FutureRemove removeFuture = removeProtectedDataFromMap(LOCATION_KEY, arbitratorData);
        removeFuture.addListener(new BaseFutureAdapter<BaseFuture>() {
            @Override
            public void operationComplete(BaseFuture future) throws Exception {
                for (Data arbitratorData : removeFuture.dataMap().values()) {
                    try {
                        Object arbitratorDataObject = arbitratorData.object();
                        if (arbitratorDataObject instanceof Arbitrator) {
                            Arbitrator arbitrator = (Arbitrator) arbitratorDataObject;
                            executor.execute(() -> listeners.stream().forEach(listener -> listener.onArbitratorRemoved(arbitrator)));
                        }
                    } catch (ClassNotFoundException | IOException e) {
                        e.printStackTrace();
                    }
                }

                // We don't test futureRemove.isSuccess() as this API does not fit well to that operation,
                // it might change in future to something like foundAndRemoved and notFound
                // See discussion at: https://github.com/tomp2p/TomP2P/issues/57#issuecomment-62069840

                log.trace("Remove arbitrator from DHT was successful. Stored data: [key: " + LOCATION_KEY + ", " +
                        "values: " + arbitratorData + "]");
            }
        });
    }

    public void loadAllArbitrators(ArbitratorMapResultHandler resultHandler, ErrorMessageHandler errorMessageHandler) {
        FutureGet futureGet = getMap(LOCATION_KEY);
        futureGet.addListener(new BaseFutureAdapter<BaseFuture>() {
            @Override
            public void operationComplete(BaseFuture future) throws Exception {
                if (future.isSuccess()) {
                    log.trace("Get arbitrators from DHT was successful. Stored data: [key: " + LOCATION_KEY + ", " +
                            "values: " + futureGet.dataMap() + "]");

                    final Map<String, Arbitrator> arbitratorsMap = new HashMap<>();
                    for (Data arbitratorData : futureGet.dataMap().values()) {
                        try {
                            Object arbitratorDataObject = arbitratorData.object();
                            if (arbitratorDataObject instanceof Arbitrator) {
                                Arbitrator arbitrator = (Arbitrator) arbitratorDataObject;
                                arbitratorsMap.put(arbitrator.getId(), arbitrator);
                            }
                        } catch (ClassNotFoundException | IOException e) {
                            e.printStackTrace();
                            log.error("Get arbitrators from DHT failed with exception:" + e.getMessage());
                            errorMessageHandler.handleErrorMessage("Get arbitrators from DHT failed with exception:" + e.getMessage());
                        }
                    }
                    executor.execute(() -> {
                        resultHandler.handleResult(arbitratorsMap);
                        listeners.stream().forEach(listener -> listener.onAllArbitratorsLoaded(arbitratorsMap));
                    });
                }
                else {
                    log.error("Get arbitrators from DHT failed with reason:" + future.failedReason());
                    errorMessageHandler.handleErrorMessage("Get arbitrators from DHT failed with reason:" + future.failedReason());
                }
            }
        });
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Event Listeners
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void addListener(Listener listener) {
        listeners.add(listener);
    }

    public void removeListener(Listener listener) {
        listeners.remove(listener);
    }

}
