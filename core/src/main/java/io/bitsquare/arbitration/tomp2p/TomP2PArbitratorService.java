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
import io.bitsquare.arbitration.listeners.ArbitratorListener;
import io.bitsquare.network.tomp2p.TomP2PDHTService;
import io.bitsquare.network.tomp2p.TomP2PNode;

import java.io.IOException;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

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

    private static final String ARBITRATORS_ROOT = "ArbitratorsRoot";

    private final List<ArbitratorListener> arbitratorListeners = new ArrayList<>();


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    public TomP2PArbitratorService(TomP2PNode tomP2PNode) {
        super(tomP2PNode);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Arbitrators
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void addArbitrator(Arbitrator arbitrator) {
        Number160 locationKey = Number160.createHash(ARBITRATORS_ROOT);
        try {
            final Data arbitratorData = new Data(arbitrator);

            FuturePut addFuture = addProtectedData(locationKey, arbitratorData);
            addFuture.addListener(new BaseFutureAdapter<BaseFuture>() {
                @Override
                public void operationComplete(BaseFuture future) throws Exception {
                    executor.execute(() -> arbitratorListeners.stream().forEach(listener ->
                    {
                        try {
                            Object arbitratorDataObject = arbitratorData.object();
                            if (arbitratorDataObject instanceof Arbitrator) {
                                listener.onArbitratorAdded((Arbitrator) arbitratorDataObject);
                            }
                        } catch (ClassNotFoundException | IOException e) {
                            e.printStackTrace();
                            log.error(e.toString());
                        }
                    }));

                    if (future.isSuccess()) {
                        log.trace("Add arbitrator to DHT was successful. Stored data: [key: " + locationKey + ", " +
                                "values: " + arbitratorData + "]");
                    }
                    else {
                        log.error("Add arbitrator to DHT failed with reason:" + addFuture.failedReason());
                    }
                }
            });
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void removeArbitrator(Arbitrator arbitrator) throws IOException {
        Number160 locationKey = Number160.createHash(ARBITRATORS_ROOT);
        final Data arbitratorData = new Data(arbitrator);
        FutureRemove removeFuture = removeFromDataMap(locationKey, arbitratorData);
        removeFuture.addListener(new BaseFutureAdapter<BaseFuture>() {
            @Override
            public void operationComplete(BaseFuture future) throws Exception {
                executor.execute(() -> arbitratorListeners.stream().forEach(listener ->
                {
                    for (Data arbitratorData : removeFuture.dataMap().values()) {
                        try {
                            Object arbitratorDataObject = arbitratorData.object();
                            if (arbitratorDataObject instanceof Arbitrator) {
                                Arbitrator arbitrator = (Arbitrator) arbitratorDataObject;
                                listener.onArbitratorRemoved(arbitrator);
                            }
                        } catch (ClassNotFoundException | IOException e) {
                            e.printStackTrace();
                        }
                    }
                }));

                // We don't test futureRemove.isSuccess() as this API does not fit well to that operation,
                // it might change in future to something like foundAndRemoved and notFound
                // See discussion at: https://github.com/tomp2p/TomP2P/issues/57#issuecomment-62069840

                log.trace("Remove arbitrator from DHT was successful. Stored data: [key: " + locationKey + ", " +
                        "values: " + arbitratorData + "]");
            }
        });
    }

    public void getArbitrators(Locale languageLocale) {
        Number160 locationKey = Number160.createHash(ARBITRATORS_ROOT);
        FutureGet futureGet = getDataMap(locationKey);
        futureGet.addListener(new BaseFutureAdapter<BaseFuture>() {
            @Override
            public void operationComplete(BaseFuture future) throws Exception {
                executor.execute(() -> arbitratorListeners.stream().forEach(listener ->
                {
                    List<Arbitrator> arbitrators = new ArrayList<>();
                    for (Data arbitratorData : futureGet.dataMap().values()) {
                        try {
                            Object arbitratorDataObject = arbitratorData.object();
                            if (arbitratorDataObject instanceof Arbitrator) {
                                arbitrators.add((Arbitrator) arbitratorDataObject);
                            }
                        } catch (ClassNotFoundException | IOException e) {
                            e.printStackTrace();
                            log.error("Get arbitrators from DHT failed with exception:" + e.getMessage());
                        }
                    }

                    listener.onArbitratorsReceived(arbitrators);
                }));
                if (future.isSuccess()) {
                    log.trace("Get arbitrators from DHT was successful. Stored data: [key: " + locationKey + ", " +
                            "values: " + futureGet.dataMap() + "]");
                }
                else {
                    log.error("Get arbitrators from DHT failed with reason:" + future.failedReason());
                }
            }
        });
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Event Listeners
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void addArbitratorListener(ArbitratorListener listener) {
        arbitratorListeners.add(listener);
    }

    public void removeArbitratorListener(ArbitratorListener listener) {
        arbitratorListeners.remove(listener);
    }

}
