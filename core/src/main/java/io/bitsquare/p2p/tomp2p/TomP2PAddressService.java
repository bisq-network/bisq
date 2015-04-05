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

import io.bitsquare.p2p.AddressService;
import io.bitsquare.p2p.NetworkException;
import io.bitsquare.p2p.Peer;
import io.bitsquare.p2p.listener.GetPeerAddressListener;
import io.bitsquare.user.User;

import java.io.IOException;

import java.security.PublicKey;

import java.util.Timer;
import java.util.TimerTask;

import javax.inject.Inject;

import net.tomp2p.dht.FutureGet;
import net.tomp2p.dht.FuturePut;
import net.tomp2p.dht.FutureRemove;
import net.tomp2p.futures.BaseFuture;
import net.tomp2p.futures.BaseFutureAdapter;
import net.tomp2p.futures.BaseFutureListener;
import net.tomp2p.peers.Number160;
import net.tomp2p.peers.PeerAddress;
import net.tomp2p.storage.Data;
import net.tomp2p.utils.Utils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TomP2PAddressService extends TomP2PDHTService implements AddressService {
    private static final Logger log = LoggerFactory.getLogger(TomP2PAddressService.class);

    private static final int IP_CHECK_PERIOD = 2 * 60 * 1000;           // Cheap call if nothing changes, so set it short to 2 min.
    private static final int STORE_ADDRESS_PERIOD = 5 * 60 * 1000;      // Save every 5 min.
    private static final int ADDRESS_TTL = STORE_ADDRESS_PERIOD * 2;    // TTL 10 min.

    private final Number160 locationKey;
    private PeerAddress storedPeerAddress;
    private Timer timerForStoreAddress;
    private Timer timerForIPCheck;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    public TomP2PAddressService(TomP2PNode tomP2PNode, User user) {
        super(tomP2PNode, user);

        locationKey = Utils.makeSHAHash(user.getP2pSigKeyPair().getPublic().getEncoded());
    }

    @Override
    public void bootstrapCompleted() {
        super.bootstrapCompleted();
        setupTimerForIPCheck();
        setupTimerForStoreAddress();
        storeAddress();
    }

    @Override
    public void shutDown() {
        if (timerForIPCheck != null)
            timerForIPCheck.cancel();
        if (timerForStoreAddress != null)
            timerForStoreAddress.cancel();
        removeAddress();
        super.shutDown();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Find peer address by publicKey
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void findPeerAddress(PublicKey p2pSigPubKey, GetPeerAddressListener listener) {
        final Number160 locationKey = Utils.makeSHAHash(p2pSigPubKey.getEncoded());
        FutureGet futureGet = getDataOfProtectedDomain(locationKey, p2pSigPubKey);
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

    private void setupTimerForStoreAddress() {
        timerForStoreAddress = new Timer();
        timerForStoreAddress.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                if (storedPeerAddress != null && peerDHT != null && !storedPeerAddress.equals(peerDHT.peerAddress()))
                    storeAddress();
            }
        }, STORE_ADDRESS_PERIOD, STORE_ADDRESS_PERIOD);
    }

    private void setupTimerForIPCheck() {
        timerForIPCheck = new Timer();
        timerForIPCheck.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                if (storedPeerAddress != null && peerDHT != null && !storedPeerAddress.equals(peerDHT.peerAddress()))
                    storeAddress();
            }
        }, IP_CHECK_PERIOD, IP_CHECK_PERIOD);
    }

    private void storeAddress() {
        try {
            Data data = new Data(new TomP2PPeer(peerDHT.peerAddress()));
            // We set a short time-to-live to make getAddress checks fail fast in case if the offerer is offline and to support cheap offerbook state updates
            data.ttlSeconds(ADDRESS_TTL);
            log.debug("storePeerAddress " + peerDHT.peerAddress().toString());
            FuturePut futurePut = putDataToMyProtectedDomain(locationKey, data);
            futurePut.addListener(new BaseFutureListener<BaseFuture>() {
                @Override
                public void operationComplete(BaseFuture future) throws Exception {
                    if (future.isSuccess()) {
                        storedPeerAddress = peerDHT.peerAddress();
                        log.debug("storedPeerAddress = " + storedPeerAddress);
                    }
                    else {
                        log.error("storedPeerAddress not successful");
                        throw new NetworkException("Storing address was not successful. Reason: " + future.failedReason());
                    }
                }

                @Override
                public void exceptionCaught(Throwable t) throws Exception {
                    log.error("Exception at storedPeerAddress " + t.toString());
                    throw new NetworkException("Exception at storeAddress.", t);
                }
            });
        } catch (IOException e) {
            e.printStackTrace();
            log.error("Exception at storePeerAddress " + e.toString());
        }
    }

    private void removeAddress() {
        try {
            FutureRemove futureRemove = removeDataFromMyProtectedDomain(locationKey);
            if (futureRemove != null) {
                boolean success = futureRemove.awaitUninterruptibly(1000);
                log.debug("removeDataFromMyProtectedDomain success=" + success);
            }
        } catch (Throwable t) {
            t.printStackTrace();
            log.error(t.getMessage());
        }
    }

}
