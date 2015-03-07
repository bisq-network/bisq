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

package io.bitsquare.offer.tomp2p;

import io.bitsquare.msg.tomp2p.TomP2PNode;
import io.bitsquare.offer.Offer;
import io.bitsquare.offer.RemoteOfferBook;
import io.bitsquare.util.task.FaultHandler;
import io.bitsquare.util.task.ResultHandler;

import java.io.IOException;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;

import javax.inject.Inject;

import javafx.beans.property.LongProperty;
import javafx.beans.property.SimpleLongProperty;

import net.tomp2p.dht.FutureGet;
import net.tomp2p.dht.FuturePut;
import net.tomp2p.dht.FutureRemove;
import net.tomp2p.futures.BaseFuture;
import net.tomp2p.futures.BaseFutureAdapter;
import net.tomp2p.futures.BaseFutureListener;
import net.tomp2p.peers.Number160;
import net.tomp2p.peers.Number640;
import net.tomp2p.storage.Data;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TomP2POfferBook implements RemoteOfferBook {

    private static final Logger log = LoggerFactory.getLogger(TomP2POfferBook.class);

    private final List<Listener> offerRepositoryListeners = new ArrayList<>();
    private final LongProperty invalidationTimestamp = new SimpleLongProperty(0);

    private final TomP2PNode p2pNode;
    private Executor executor;

    @Inject
    public TomP2POfferBook(TomP2PNode p2pNode) {
        this.p2pNode = p2pNode;
    }

    public void setExecutor(Executor executor) {
        this.executor = executor;
    }

    @Override
    public void addOffer(Offer offer, ResultHandler resultHandler, FaultHandler faultHandler) {
        Number160 locationKey = Number160.createHash(offer.getCurrency().getCurrencyCode());
        try {
            final Data offerData = new Data(offer);

            // the offer is default 30 days valid
            int defaultOfferTTL = 30 * 24 * 60 * 60;
            offerData.ttlSeconds(defaultOfferTTL);
            log.trace("Add offer to DHT requested. Added data: [locationKey: " + locationKey +
                    ", hash: " + offerData.hash().toString() + "]");
            FuturePut futurePut = p2pNode.addProtectedData(locationKey, offerData);
            futurePut.addListener(new BaseFutureListener<BaseFuture>() {
                @Override
                public void operationComplete(BaseFuture future) throws Exception {
                    if (future.isSuccess()) {
                        executor.execute(() -> {
                            resultHandler.handleResult();
                            offerRepositoryListeners.stream().forEach(listener -> {
                                try {
                                    Object offerDataObject = offerData.object();
                                    if (offerDataObject instanceof Offer) {
                                        log.info("Added offer to DHT with ID: " + offerDataObject);
                                        listener.onOfferAdded((Offer) offerDataObject);
                                    }
                                } catch (ClassNotFoundException | IOException e) {
                                    e.printStackTrace();
                                    log.error("Add offer to DHT failed: " + e.getMessage());
                                }
                            });

                            writeInvalidationTimestampToDHT(offer.getCurrency().getCurrencyCode());
                            log.trace("Add offer to DHT was successful. Added data: [locationKey: " + locationKey +
                                    ", value: " + offerData + "]");
                        });
                    }
                }

                @Override
                public void exceptionCaught(Throwable ex) throws Exception {
                    executor.execute(() -> faultHandler.handleFault("Failed to add offer to DHT", ex));
                }
            });
        } catch (IOException ex) {
            executor.execute(() -> faultHandler.handleFault("Failed to add offer to DHT", ex));
        }
    }

    //TODO remove is failing, probably due Coin or Fiat class (was working before)
    // objects are identical but returned object form network might have some problem with serialisation?
    public void removeOffer(Offer offer) {
        Number160 locationKey = Number160.createHash(offer.getCurrency().getCurrencyCode());
        try {
            final Data offerData = new Data(offer);
            log.trace("Remove offer from DHT requested. Removed data: [locationKey: " + locationKey +
                    ", hash: " + offerData.hash().toString() + "]");
            FutureRemove futureRemove = p2pNode.removeFromDataMap(locationKey, offerData);
            futureRemove.addListener(new BaseFutureListener<BaseFuture>() {
                @Override
                public void operationComplete(BaseFuture future) throws Exception {
                    // We don't test futureRemove.isSuccess() as this API does not fit well to that operation, 
                    // it might change in future to something like foundAndRemoved and notFound
                    // See discussion at: https://github.com/tomp2p/TomP2P/issues/57#issuecomment-62069840

                    executor.execute(() -> {
                        offerRepositoryListeners.stream().forEach(listener -> {
                            try {
                                Object offerDataObject = offerData.object();
                                if (offerDataObject instanceof Offer) {
                                    log.trace("Remove offer from DHT was successful. Removed data: [key: " +
                                            locationKey + ", " +
                                            "offer: " + (Offer) offerDataObject + "]");
                                    listener.onOfferRemoved((Offer) offerDataObject);
                                }
                            } catch (ClassNotFoundException | IOException e) {
                                e.printStackTrace();
                                log.error("Remove offer from DHT failed. Error: " + e.getMessage());
                            }
                        });
                        writeInvalidationTimestampToDHT(offer.getCurrency().getCurrencyCode());
                    });
                }

                @Override
                public void exceptionCaught(Throwable t) throws Exception {
                    log.error("Remove offer from DHT failed. Error: " + t.getMessage());
                }
            });
        } catch (IOException e) {
            e.printStackTrace();
            log.error("Remove offer from DHT failed. Error: " + e.getMessage());
        }
    }

    public void getOffers(String currencyCode) {
        Number160 locationKey = Number160.createHash(currencyCode);
        log.trace("Get offers from DHT requested for locationKey: " + locationKey);
        FutureGet futureGet = p2pNode.getDataMap(locationKey);
        futureGet.addListener(new BaseFutureAdapter<BaseFuture>() {
            @Override
            public void operationComplete(BaseFuture future) throws Exception {
                if (future.isSuccess()) {
                    final Map<Number640, Data> dataMap = futureGet.dataMap();
                    final List<Offer> offers = new ArrayList<>();
                    if (dataMap != null) {
                        for (Data offerData : dataMap.values()) {
                            try {
                                Object offerDataObject = offerData.object();
                                if (offerDataObject instanceof Offer) {
                                    offers.add((Offer) offerDataObject);
                                }
                            } catch (ClassNotFoundException | IOException e) {
                                e.printStackTrace();
                            }
                        }

                        executor.execute(() -> offerRepositoryListeners.stream().forEach(listener ->
                                listener.onOffersReceived(offers)));
                    }

                    log.trace("Get offers from DHT was successful. Stored data: [key: " + locationKey
                            + ", values: " + futureGet.dataMap() + "]");
                }
                else {
                    final Map<Number640, Data> dataMap = futureGet.dataMap();
                    if (dataMap == null || dataMap.size() == 0) {
                        log.trace("Get offers from DHT delivered empty dataMap.");
                        executor.execute(() -> offerRepositoryListeners.stream().forEach(listener ->
                                listener.onOffersReceived(new ArrayList<>())));
                    }
                    else {
                        log.error("Get offers from DHT  was not successful with reason:" + future.failedReason());
                    }
                }
            }
        });
    }

    @Override
    public void addListener(Listener listener) {
        offerRepositoryListeners.add(listener);
    }

    @Override
    public void removeListener(Listener listener) {
        offerRepositoryListeners.remove(listener);
    }

    /*
     * We store the timestamp of any change of the offer list (add, remove offer) and we poll
     * in intervals for changes. If we detect a change we request the offer list from the DHT.
     * Polling should be replaced by a push based solution later.
     */

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Polling
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void writeInvalidationTimestampToDHT(String currencyCode) {
        invalidationTimestamp.set(System.currentTimeMillis());
        try {
            FuturePut putFuture = p2pNode.putData(getInvalidatedLocationKey(currencyCode),
                    new Data(invalidationTimestamp.get()));
            putFuture.addListener(new BaseFutureListener<BaseFuture>() {
                @Override
                public void operationComplete(BaseFuture future) throws Exception {
                    if (future.isSuccess())
                        log.trace("Update invalidationTimestamp to DHT was successful. TimeStamp=" +
                                invalidationTimestamp.get());
                    else
                        log.error("Update invalidationTimestamp to DHT failed with reason:" + putFuture.failedReason());
                }

                @Override
                public void exceptionCaught(Throwable t) throws Exception {
                    log.error("Update invalidationTimestamp to DHT failed with exception:" + t.getMessage());
                }
            });
        } catch (IOException e) {
            log.error("Update invalidationTimestamp to DHT failed with exception:" + e.getMessage());
        }
    }

    public LongProperty invalidationTimestampProperty() {
        return invalidationTimestamp;
    }

    public void requestInvalidationTimeStampFromDHT(String currencyCode) {
        FutureGet futureGet = p2pNode.getData(getInvalidatedLocationKey(currencyCode));
        futureGet.addListener(new BaseFutureListener<BaseFuture>() {
            @Override
            public void operationComplete(BaseFuture future) throws Exception {
                if (future.isSuccess()) {
                    Data data = futureGet.data();
                    if (data != null && data.object() instanceof Long) {
                        final Object object = data.object();
                        executor.execute(() -> {
                            Long timeStamp = (Long) object;
                            //log.trace("Get invalidationTimestamp from DHT was successful. TimeStamp=" + timeStamp);
                            invalidationTimestamp.set(timeStamp);
                        });
                    }
                    else if (data != null) {
                        log.error("Get invalidationTimestamp from DHT failed. Data = " + data);
                    }
                }
                else if (futureGet.data() == null) {
                    // OK as nothing is set at the moment
                    // log.trace("Get invalidationTimestamp from DHT returns null. That is ok for the startup.");
                }
                else {
                    log.error("Get invalidationTimestamp from DHT failed with reason:" + futureGet.failedReason());
                }
            }

            @Override
            public void exceptionCaught(Throwable t) throws Exception {
                log.error("Get invalidationTimestamp from DHT failed with exception:" + t.getMessage());
                t.printStackTrace();
            }
        });
    }

    private Number160 getInvalidatedLocationKey(String currencyCode) {
        return Number160.createHash(currencyCode + "lastChangeTimestamp");
    }
}
