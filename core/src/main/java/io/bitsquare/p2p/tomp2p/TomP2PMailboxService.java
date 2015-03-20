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

package io.bitsquare.p2p.tomp2p;

import io.bitsquare.common.handlers.FaultHandler;
import io.bitsquare.common.handlers.ResultHandler;
import io.bitsquare.offer.Offer;
import io.bitsquare.offer.OfferBookService;
import io.bitsquare.p2p.AddressService;
import io.bitsquare.p2p.Message;
import io.bitsquare.p2p.Peer;
import io.bitsquare.p2p.listener.GetPeerAddressListener;
import io.bitsquare.user.User;

import java.io.IOException;

import java.security.PublicKey;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javax.inject.Inject;

import net.tomp2p.dht.FutureGet;
import net.tomp2p.dht.FuturePut;
import net.tomp2p.dht.FutureRemove;
import net.tomp2p.futures.BaseFuture;
import net.tomp2p.futures.BaseFutureAdapter;
import net.tomp2p.futures.BaseFutureListener;
import net.tomp2p.peers.Number160;
import net.tomp2p.peers.Number640;
import net.tomp2p.storage.Data;
import net.tomp2p.utils.Utils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TomP2PMailboxService extends TomP2PDHTService implements AddressService {
    private static final Logger log = LoggerFactory.getLogger(TomP2PMailboxService.class);


    private final List<OfferBookService.Listener> offerRepositoryListeners = new ArrayList<>();

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    public TomP2PMailboxService(TomP2PNode tomP2PNode, User user) {
        super(tomP2PNode, user);
    }

    @Override
    public void bootstrapCompleted() {
        super.bootstrapCompleted();
    }

    @Override
    public void shutDown() {
        super.shutDown();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Find peer address by publicKey
    ///////////////////////////////////////////////////////////////////////////////////////////

    // public void findPeerAddress(PublicKey publicKey, GetPeerAddressListener listener) {
    //     final Number160 locationKey = Utils.makeSHAHash(publicKey.getEncoded());

    public void saveMessage(PublicKey publicKey, Message message, ResultHandler resultHandler, FaultHandler faultHandler) {
        final Number160 locationKey = Utils.makeSHAHash(publicKey.getEncoded());

        // Number160 locationKey = Number160.createHash(offer.getCurrency().getCurrencyCode());
        try {
            final Data offerData = new Data(message);

            // the offer is default 30 days valid
            int defaultOfferTTL = 30 * 24 * 60 * 60;
            offerData.ttlSeconds(defaultOfferTTL);
            log.trace("Add offer to DHT requested. Added data: [locationKey: " + locationKey +
                    ", hash: " + offerData.hash().toString() + "]");
            FuturePut futurePut = addProtectedDataToMap(locationKey, offerData);
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

    public void removeOffer(Offer offer, ResultHandler resultHandler, FaultHandler faultHandler) {
        Number160 locationKey = Number160.createHash(offer.getCurrency().getCurrencyCode());
        try {
            final Data offerData = new Data(offer);
            log.trace("Remove offer from DHT requested. Removed data: [locationKey: " + locationKey +
                    ", hash: " + offerData.hash().toString() + "]");
            FutureRemove futureRemove = removeProtectedDataFromMap(locationKey, offerData);
            futureRemove.addListener(new BaseFutureListener<BaseFuture>() {
                @Override
                public void operationComplete(BaseFuture future) throws Exception {
                    // We don't test futureRemove.isSuccess() as this API does not fit well to that operation, 
                    // it might change in future to something like foundAndRemoved and notFound
                    // See discussion at: https://github.com/tomp2p/TomP2P/issues/57#issuecomment-62069840
                    log.trace("isRemoved? " + futureRemove.isRemoved());
                    executor.execute(() -> {
                        resultHandler.handleResult();
                        offerRepositoryListeners.stream().forEach(listener -> {
                            try {
                                Object offerDataObject = offerData.object();
                                if (offerDataObject instanceof Offer) {
                                    log.trace("Remove offer from DHT was successful. Removed data: [key: " +
                                            locationKey + ", " +
                                            "offer: " + offerDataObject + "]");
                                    listener.onOfferRemoved((Offer) offerDataObject);
                                }
                            } catch (ClassNotFoundException | IOException e) {
                                e.printStackTrace();
                                log.error("Remove offer from DHT failed. Error: " + e.getMessage());
                                faultHandler.handleFault("Remove offer from DHT failed. Error: " + e.getMessage(), e);
                            }
                        });
                    });
                }

                @Override
                public void exceptionCaught(Throwable t) throws Exception {
                    log.error("Remove offer from DHT failed. Error: " + t.getMessage());
                    faultHandler.handleFault("Remove offer from DHT failed. Error: " + t.getMessage(), t);
                }
            });
        } catch (IOException e) {
            e.printStackTrace();
            log.error("Remove offer from DHT failed. Error: " + e.getMessage());
            faultHandler.handleFault("Remove offer from DHT failed. Error: " + e.getMessage(), e);
        }
    }

    public void getOffers(String currencyCode) {
        Number160 locationKey = Number160.createHash(currencyCode);
        log.trace("Get offers from DHT requested for locationKey: " + locationKey);
        FutureGet futureGet = getMap(locationKey);
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
    public void findPeerAddress(PublicKey publicKey, GetPeerAddressListener listener) {
        final Number160 locationKey = Utils.makeSHAHash(publicKey.getEncoded());
        FutureGet futureGet = getDataOfProtectedDomain(locationKey, publicKey);
        log.trace("findPeerAddress called");
        futureGet.addListener(new BaseFutureAdapter<BaseFuture>() {
            @Override
            public void operationComplete(BaseFuture baseFuture) throws Exception {
                if (baseFuture.isSuccess() && futureGet.data() != null) {
                    final Peer peer = (Peer) futureGet.data().object();
                    log.trace("Peer found in DHT. Peer = " + peer);
                    executor.execute(() -> listener.onResult(peer));
                }
                else {
                    log.error("getPeerAddress failed. failedReason = " + baseFuture.failedReason());
                    executor.execute(listener::onFailed);
                }
            }
        });
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Private
    ///////////////////////////////////////////////////////////////////////////////////////////

    private Number160 getLocationKey(String currencyCode) {
        return Number160.createHash(currencyCode + "mailbox");
    }

}
