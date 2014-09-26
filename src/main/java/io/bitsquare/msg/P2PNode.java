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

package io.bitsquare.msg;

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;

import com.google.inject.name.Named;

import java.io.File;
import java.io.IOException;

import java.security.KeyPair;
import java.security.PublicKey;

import java.util.Timer;
import java.util.TimerTask;

import javax.annotation.Nullable;

import javax.inject.Inject;

import net.tomp2p.connection.DSASignatureFactory;
import net.tomp2p.dht.FutureGet;
import net.tomp2p.dht.FuturePut;
import net.tomp2p.dht.FutureRemove;
import net.tomp2p.dht.PeerDHT;
import net.tomp2p.dht.StorageMemory;
import net.tomp2p.futures.BaseFuture;
import net.tomp2p.futures.BaseFutureListener;
import net.tomp2p.futures.FutureDirect;
import net.tomp2p.futures.FuturePeerConnection;
import net.tomp2p.peers.Number160;
import net.tomp2p.peers.PeerAddress;
import net.tomp2p.storage.Data;
import net.tomp2p.storage.Storage;
import net.tomp2p.storage.StorageDisk;
import net.tomp2p.utils.Utils;

import org.jetbrains.annotations.NotNull;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import lighthouse.files.AppDirectory;

/**
 * The fully bootstrapped P2PNode which is responsible himself for his availability in the messaging system. It saves
 * for instance the IP address periodically.
 * This class is offering generic functionality of TomP2P needed for Bitsquare, like data and domain protection.
 * It does not handle any domain aspects of Bitsquare.
 */
public class P2PNode {
    private static final Logger log = LoggerFactory.getLogger(P2PNode.class);

    private KeyPair keyPair;
    private final Boolean useDiskStorage;
    private MessageBroker messageBroker;

    private PeerAddress storedPeerAddress;
    private PeerDHT peerDHT;
    private Storage storage;
    private BootstrappedPeerFactory bootstrappedPeerFactory;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    public P2PNode(BootstrappedPeerFactory bootstrappedPeerFactory,
                   @Named("useDiskStorage") Boolean useDiskStorage) {
        this.bootstrappedPeerFactory = bootstrappedPeerFactory;
        this.useDiskStorage = useDiskStorage;
    }

    // for unit testing
    P2PNode(KeyPair keyPair, PeerDHT peerDHT) {
        this.keyPair = keyPair;
        this.peerDHT = peerDHT;
        peerDHT.peerBean().keyPair(keyPair);
        messageBroker = (message, peerAddress) -> {
        };
        useDiskStorage = false;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Public methods
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void setMessageBroker(MessageBroker messageBroker) {
        this.messageBroker = messageBroker;
    }

    public void setKeyPair(@NotNull KeyPair keyPair) {
        this.keyPair = keyPair;
        bootstrappedPeerFactory.setKeyPair(keyPair);
    }

    public void start(FutureCallback<PeerDHT> callback) {
        useDiscStorage(useDiskStorage);

        bootstrappedPeerFactory.setStorage(storage);
        setupTimerForIPCheck();

        ListenableFuture<PeerDHT> bootstrapComplete = bootstrap();
        Futures.addCallback(bootstrapComplete, callback);
    }


    public void shutDown() {
        if (peerDHT != null && peerDHT.peer() != null)
            peerDHT.peer().shutdown();

        if (storage != null)
            storage.close();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Generic DHT methods
    ///////////////////////////////////////////////////////////////////////////////////////////

    // The data and the domain are protected by that key pair.
    public FuturePut putDomainProtectedData(Number160 locationKey, Data data) {
        data.protectEntry(keyPair);
        final Number160 ownerKeyHash = Utils.makeSHAHash(keyPair.getPublic().getEncoded());
        return peerDHT.put(locationKey).data(data).keyPair(keyPair).domainKey(ownerKeyHash).protectDomain().start();
    }

    // No protection, everybody can write.
    public FuturePut putData(Number160 locationKey, Data data) {
        return peerDHT.put(locationKey).data(data).start();
    }

    // Not public readable. Only users with the public key of the peer who stored the data can read that data
    public FutureGet getDomainProtectedData(Number160 locationKey, PublicKey publicKey) {
        final Number160 ownerKeyHash = Utils.makeSHAHash(publicKey.getEncoded());
        return peerDHT.get(locationKey).domainKey(ownerKeyHash).start();
    }

    // No protection, everybody can read.
    public FutureGet getData(Number160 locationKey) {
        return peerDHT.get(locationKey).start();
    }

    // No domain protection, but entry protection
    public FuturePut addProtectedData(Number160 locationKey, Data data) {
        data.protectEntry(keyPair);
        log.trace("addProtectedData with contentKey " + data.hash().toString());
        return peerDHT.add(locationKey).data(data).keyPair(keyPair).start();
    }

    // No domain protection, but entry protection
    public FutureRemove removeFromDataMap(Number160 locationKey, Data data) {
        Number160 contentKey = data.hash();
        log.trace("removeFromDataMap with contentKey " + contentKey.toString());
        return peerDHT.remove(locationKey).contentKey(contentKey).keyPair(keyPair).start();
    }

    // Public readable
    public FutureGet getDataMap(Number160 locationKey) {
        return peerDHT.get(locationKey).all().start();
    }

    // Send signed payLoad to peer
    public FutureDirect sendData(PeerAddress peerAddress, Object payLoad) {
        // use 30 seconds as max idle time before connection get closed
        FuturePeerConnection futurePeerConnection = peerDHT.peer().createPeerConnection(peerAddress, 30000);
        FutureDirect futureDirect = peerDHT.peer().sendDirect(futurePeerConnection).object(payLoad).sign().start();
        futureDirect.addListener(new BaseFutureListener<BaseFuture>() {
            @Override
            public void operationComplete(BaseFuture future) throws Exception {
                if (futureDirect.isSuccess()) {
                    log.debug("sendMessage completed");
                }
                else {
                    log.error("sendData failed with Reason " + futureDirect.failedReason());
                }
            }

            @Override
            public void exceptionCaught(Throwable t) throws Exception {
                log.error("Exception at sendData " + t.toString());
            }
        });

        return futureDirect;
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Private
    ///////////////////////////////////////////////////////////////////////////////////////////

    private ListenableFuture<PeerDHT> bootstrap() {
        ListenableFuture<PeerDHT> bootstrapComplete = bootstrappedPeerFactory.start();
        Futures.addCallback(bootstrapComplete, new FutureCallback<PeerDHT>() {
            @Override
            public void onSuccess(@Nullable PeerDHT peerDHT) {
                try {
                    if (peerDHT != null) {
                        P2PNode.this.peerDHT = peerDHT;
                        setupReplyHandler();
                        FuturePut futurePut = storePeerAddress();
                        futurePut.addListener(new BaseFutureListener<BaseFuture>() {
                            @Override
                            public void operationComplete(BaseFuture future) throws Exception {
                                if (future.isSuccess()) {
                                    storedPeerAddress = peerDHT.peerAddress();
                                    log.debug("storedPeerAddress = " + storedPeerAddress);
                                }
                                else {
                                    log.error("");
                                }
                            }

                            @Override
                            public void exceptionCaught(Throwable t) throws Exception {
                                log.error(t.toString());
                            }
                        });
                    }
                    else {
                        log.error("peerDHT is null");
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                    log.error(e.toString());
                }
            }

            @Override
            public void onFailure(@NotNull Throwable t) {
                log.error(t.toString());
            }
        });
        return bootstrapComplete;
    }

    private void setupReplyHandler() {
        peerDHT.peer().objectDataReply((sender, request) -> {
            if (!sender.equals(peerDHT.peer().peerAddress()))
                if (messageBroker != null) messageBroker.handleMessage(request, sender);
                else
                    log.error("Received msg from myself. That should never happen.");
            return null;
        });
    }

    private void setupTimerForIPCheck() {
        Timer timer = new Timer();
        long checkIfIPChangedPeriod = 600 * 1000;
        timer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                if (peerDHT != null && !storedPeerAddress.equals(peerDHT.peerAddress())) {
                    try {
                        storePeerAddress();
                    } catch (IOException e) {
                        e.printStackTrace();
                        log.error(e.toString());
                    }
                }
            }
        }, checkIfIPChangedPeriod, checkIfIPChangedPeriod);
    }

    private FuturePut storePeerAddress() throws IOException {
        Number160 locationKey = Utils.makeSHAHash(keyPair.getPublic().getEncoded());
        Data data = new Data(peerDHT.peerAddress());
        return putDomainProtectedData(locationKey, data);
    }

    private void useDiscStorage(boolean useDiscStorage) {
        if (useDiscStorage) {
            File path = new File(AppDirectory.dir().toFile() + "/tomP2P");
            if (!path.exists()) {
                boolean created = path.mkdir();
                if (!created)
                    throw new RuntimeException("Could not create the directory '" + path + "'");
            }
            storage = new StorageDisk(Number160.ZERO, path, new DSASignatureFactory());
        }
        else {
            storage = new StorageMemory();
        }
    }
}
