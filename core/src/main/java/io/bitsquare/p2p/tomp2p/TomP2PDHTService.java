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

import io.bitsquare.p2p.DHTService;

import java.security.PublicKey;

import javax.inject.Inject;

import net.tomp2p.dht.FutureGet;
import net.tomp2p.dht.FuturePut;
import net.tomp2p.dht.FutureRemove;
import net.tomp2p.peers.Number160;
import net.tomp2p.storage.Data;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TomP2PDHTService extends TomP2PService implements DHTService {
    private static final Logger log = LoggerFactory.getLogger(TomP2PDHTService.class);


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    public TomP2PDHTService(TomP2PNode tomP2PNode) {
        super(tomP2PNode);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // DHT methods
    ///////////////////////////////////////////////////////////////////////////////////////////

    // TODO remove all security features for the moment. There are some problems with a "wrong signature!" msg in
    // the logs
    @Override
    public FuturePut putDomainProtectedData(Number160 locationKey, Data data) {
        log.trace("putDomainProtectedData");
        return peerDHT.put(locationKey).data(data).start();
    }

    @Override
    public FuturePut putData(Number160 locationKey, Data data) {
        log.trace("putData");
        return peerDHT.put(locationKey).data(data).start();
    }

    @Override
    public FutureGet getDomainProtectedData(Number160 locationKey, PublicKey publicKey) {
        log.trace("getDomainProtectedData");
        return peerDHT.get(locationKey).start();
    }

    @Override
    public FutureGet getData(Number160 locationKey) {
        //log.trace("getData");
        return peerDHT.get(locationKey).start();
    }

    @Override
    public FuturePut addProtectedData(Number160 locationKey, Data data) {
        log.trace("addProtectedData");
        return peerDHT.add(locationKey).data(data).start();
    }

    @Override
    public FutureRemove removeFromDataMap(Number160 locationKey, Data data) {
        Number160 contentKey = data.hash();
        log.trace("removeFromDataMap with contentKey " + contentKey.toString());
        return peerDHT.remove(locationKey).contentKey(contentKey).start();
    }

    @Override
    public FutureGet getDataMap(Number160 locationKey) {
        log.trace("getDataMap");
        return peerDHT.get(locationKey).all().start();
    }


//
//    public FuturePut putDomainProtectedData(Number160 locationKey, Data data) {
//        log.trace("putDomainProtectedData");
//        data.protectEntry(keyPair);
//        final Number160 ownerKeyHash = Utils.makeSHAHash(keyPair.getPublic().getEncoded());
//        return peerDHT.put(locationKey).data(data).keyPair(keyPair).domainKey(ownerKeyHash).protectDomain().start();
//    }
//
//    // No protection, everybody can write.
//    public FuturePut putData(Number160 locationKey, Data data) {
//        log.trace("putData");
//        return peerDHT.put(locationKey).data(data).start();
//    }
//
//    // Not public readable. Only users with the public key of the peer who stored the data can read that data
//    public FutureGet getDomainProtectedData(Number160 locationKey, PublicKey publicKey) {
//        log.trace("getDomainProtectedData");
//        final Number160 ownerKeyHash = Utils.makeSHAHash(publicKey.getEncoded());
//        return peerDHT.get(locationKey).domainKey(ownerKeyHash).start();
//    }
//
//    // No protection, everybody can read.
//    public FutureGet getData(Number160 locationKey) {
//        log.trace("getData");
//        return peerDHT.get(locationKey).start();
//    }
//
//    // No domain protection, but entry protection
//    public FuturePut addProtectedData(Number160 locationKey, Data data) {
//        log.trace("addProtectedData");
//        data.protectEntry(keyPair);
//        log.trace("addProtectedData with contentKey " + data.hash().toString());
//        return peerDHT.add(locationKey).data(data).keyPair(keyPair).start();
//    }
//
//    // No domain protection, but entry protection
//    public FutureRemove removeFromDataMap(Number160 locationKey, Data data) {
//        log.trace("removeFromDataMap");
//        Number160 contentKey = data.hash();
//        log.trace("removeFromDataMap with contentKey " + contentKey.toString());
//        return peerDHT.remove(locationKey).contentKey(contentKey).keyPair(keyPair).start();
//    }
//
//    // Public readable
//    public FutureGet getDataMap(Number160 locationKey) {
//        log.trace("getDataMap");
//        return peerDHT.get(locationKey).all().start();
//    }

    // Send signed payLoad to peer
//    public FutureDirect sendData(PeerAddress peerAddress, Object payLoad) {
//        // use 30 seconds as max idle time before connection get closed
//        FuturePeerConnection futurePeerConnection = peerDHT.peer().createPeerConnection(peerAddress, 30000);
//        FutureDirect futureDirect = peerDHT.peer().sendDirect(futurePeerConnection).object(payLoad).sign().start();
//        futureDirect.addListener(new BaseFutureListener<BaseFuture>() {
//            @Override
//            public void operationComplete(BaseFuture future) throws Exception {
//                if (futureDirect.isSuccess()) {
//                    log.debug("sendMessage completed");
//                }
//                else {
//                    log.error("sendData failed with Reason " + futureDirect.failedReason());
//                }
//            }
//
//            @Override
//            public void exceptionCaught(Throwable t) throws Exception {
//                log.error("Exception at sendData " + t.toString());
//            }
//        });
//
//        return futureDirect;
//    }
//


}
