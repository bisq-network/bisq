package io.bitsquare.msg;

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import io.bitsquare.BitSquare;
import io.bitsquare.util.DSAKeyUtil;
import io.bitsquare.util.StorageDirectory;
import java.io.File;
import java.io.IOException;
import java.security.KeyPair;
import java.security.PublicKey;
import java.util.Timer;
import java.util.TimerTask;
import javax.annotation.Nullable;
import net.tomp2p.connection.DSASignatureFactory;
import net.tomp2p.dht.*;
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

/**
 * The fully bootstrapped P2PNode which is responsible himself for his availability in the messaging system. It saves for instance the IP address periodically.
 * This class is offering generic functionality of TomP2P needed for Bitsquare, like data and domain protection.
 * It does not handle any domain aspects of Bitsquare.
 */
public class P2PNode
{
    private static final Logger log = LoggerFactory.getLogger(P2PNode.class);

    private Thread bootstrapToLocalhostThread;
    private Thread bootstrapToServerThread;

    // just for lightweight client test
    public static void main(String[] args)
    {
        P2PNode p2pNode = new P2PNode(DSAKeyUtil.generateKeyPair(), false, SeedNodeAddress.StaticSeedNodeAddresses.DIGITAL_OCEAN,
                                      (message, peerAddress) -> log.debug("handleMessage: message= " + message + "/ peerAddress=" + peerAddress));
        p2pNode.start(new FutureCallback<PeerDHT>()
        {
            @Override
            public void onSuccess(@Nullable PeerDHT result)
            {
                log.debug("p2pNode.start success result = " + result);
            }

            @Override
            public void onFailure(Throwable t)
            {
                log.error(t.toString());
            }
        });
        for (; ; )
        {
        }
    }

    private final KeyPair keyPair;
    private final Boolean useDiskStorage;
    private final SeedNodeAddress.StaticSeedNodeAddresses defaultStaticSeedNodeAddresses;
    private final MessageBroker messageBroker;

    private PeerAddress storedPeerAddress;
    private PeerDHT peerDHT;
    private Storage storage;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    public P2PNode(KeyPair keyPair, Boolean useDiskStorage, SeedNodeAddress.StaticSeedNodeAddresses defaultStaticSeedNodeAddresses, MessageBroker messageBroker)
    {
        this.keyPair = keyPair;
        this.useDiskStorage = useDiskStorage;
        this.defaultStaticSeedNodeAddresses = defaultStaticSeedNodeAddresses;
        this.messageBroker = messageBroker;
    }

    // for unit testing
    P2PNode(KeyPair keyPair, PeerDHT peerDHT)
    {
        this.keyPair = keyPair;
        this.peerDHT = peerDHT;
        messageBroker = (message, peerAddress) -> {
        };
        useDiskStorage = false;
        defaultStaticSeedNodeAddresses = SeedNodeAddress.StaticSeedNodeAddresses.LOCALHOST;
        peerDHT.peerBean().keyPair(keyPair);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Public methods
    ///////////////////////////////////////////////////////////////////////////////////////////


    public void start(FutureCallback<PeerDHT> callback)
    {
        useDiscStorage(useDiskStorage);
        setupTimerForIPCheck();

       /* FutureCallback<PeerDHT> localCallback = new FutureCallback<PeerDHT>()
        {
            @Override
            public void onSuccess(@Nullable PeerDHT result)
            {
                log.debug("p2pNode.start success result = " + result);
                callback.onSuccess(result);
                bootstrapThreadCompleted();
            }

            @Override
            public void onFailure(Throwable t)
            {
                log.error(t.toString());
                callback.onFailure(t);
            }
        };   */

        ListenableFuture<PeerDHT> bootstrapComplete = bootstrap(new SeedNodeAddress(defaultStaticSeedNodeAddresses));
        Futures.addCallback(bootstrapComplete, callback);

        // bootstrapToLocalhostThread = runBootstrapThread(localCallback, new SeedNodeAddress(defaultStaticSeedNodeAddresses));
        // bootstrapToServerThread = runBootstrapThread(localCallback, new SeedNodeAddress(SeedNodeAddress.StaticSeedNodeAddresses.DIGITAL_OCEAN));
    }

    // TODO: start multiple threads for bootstrapping, so we can get it done faster.

  /*  public void bootstrapThreadCompleted()
    {
        if (bootstrapToLocalhostThread != null)
            bootstrapToLocalhostThread.interrupt();

        if (bootstrapToServerThread != null)
            bootstrapToServerThread.interrupt();
    }

    private Thread runBootstrapThread(FutureCallback<PeerDHT> callback, SeedNodeAddress seedNodeAddress)
    {
        Thread bootstrapThread = new Thread(new Runnable()
        {
            @Override
            public void run()
            {
                log.debug("runBootstrapThread");
                ListenableFuture<PeerDHT> bootstrapComplete = bootstrap(seedNodeAddress);
                Futures.addCallback(bootstrapComplete, callback);
            }
        });
        bootstrapThread.start();
        return bootstrapThread;
    }   */

    public void shutDown()
    {
        if (peerDHT != null && peerDHT.peer() != null)
            peerDHT.peer().shutdown();

        if (storage != null)
            storage.close();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Generic DHT methods
    ///////////////////////////////////////////////////////////////////////////////////////////

    // The data and the domain are protected by that key pair.
    public FuturePut putDomainProtectedData(Number160 locationKey, Data data) throws IOException, ClassNotFoundException
    {
        data.protectEntry(keyPair);
        final Number160 ownerKeyHash = Utils.makeSHAHash(keyPair.getPublic().getEncoded());
        return peerDHT.put(locationKey).data(data).keyPair(keyPair).domainKey(ownerKeyHash).protectDomain().start();
    }

    // No protection, everybody can write.
    public FuturePut putData(Number160 locationKey, Data data) throws IOException, ClassNotFoundException
    {
        return peerDHT.put(locationKey).data(data).start();
    }

    // Not public readable. Only users with the public key of the peer who stored the data can read that data
    public FutureGet getDomainProtectedData(Number160 locationKey, PublicKey publicKey) throws IOException, ClassNotFoundException
    {
        final Number160 ownerKeyHash = Utils.makeSHAHash(publicKey.getEncoded());
        return peerDHT.get(locationKey).domainKey(ownerKeyHash).start();
    }

    // No protection, everybody can read.
    public FutureGet getData(Number160 locationKey) throws IOException, ClassNotFoundException
    {
        return peerDHT.get(locationKey).start();
    }

    // No domain protection, but entry protection
    public FuturePut addProtectedData(Number160 locationKey, Data data) throws IOException, ClassNotFoundException
    {
        data.protectEntry(keyPair);
        log.trace("addProtectedData with contentKey " + data.hash().toString());
        return peerDHT.add(locationKey).data(data).keyPair(keyPair).start();
    }

    // No domain protection, but entry protection
    public FutureRemove removeFromDataMap(Number160 locationKey, Data data) throws IOException, ClassNotFoundException
    {
        Number160 contentKey = data.hash();
        log.trace("removeFromDataMap with contentKey " + contentKey.toString());
        return peerDHT.remove(locationKey).contentKey(contentKey).keyPair(keyPair).start();
    }

    // Public readable
    public FutureGet getDataMap(Number160 locationKey)
    {
        return peerDHT.get(locationKey).all().start();
    }

    // Send signed payLoad to peer
    public FutureDirect sendData(PeerAddress peerAddress, Object payLoad)
    {
        // use 30 seconds as max idle time before connection get closed
        FuturePeerConnection futurePeerConnection = peerDHT.peer().createPeerConnection(peerAddress, 30000);
        FutureDirect futureDirect = peerDHT.peer().sendDirect(futurePeerConnection).object(payLoad).sign().start();
        futureDirect.addListener(new BaseFutureListener<BaseFuture>()
        {
            @Override
            public void operationComplete(BaseFuture future) throws Exception
            {
                if (futureDirect.isSuccess())
                {
                    log.debug("sendMessage completed");
                }
                else
                {
                    log.error("sendData failed with Reason " + futureDirect.failedReason());
                }
            }

            @Override
            public void exceptionCaught(Throwable t) throws Exception
            {
                log.error("Exception at sendData " + t.toString());
            }
        });

        return futureDirect;
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Private
    ///////////////////////////////////////////////////////////////////////////////////////////

    private ListenableFuture<PeerDHT> bootstrap(SeedNodeAddress seedNodeAddress)
    {
        BootstrappedPeerFactory bootstrappedPeerFactory = new BootstrappedPeerFactory(keyPair, storage, seedNodeAddress);
        ListenableFuture<PeerDHT> bootstrapComplete = bootstrappedPeerFactory.start();
        Futures.addCallback(bootstrapComplete, new FutureCallback<PeerDHT>()
        {
            @Override
            public void onSuccess(@Nullable PeerDHT peerDHT)
            {
                try
                {
                    if (peerDHT != null)
                    {
                        P2PNode.this.peerDHT = peerDHT;
                        setupReplyHandler();
                        FuturePut futurePut = storePeerAddress();
                        futurePut.addListener(new BaseFutureListener<BaseFuture>()
                        {
                            @Override
                            public void operationComplete(BaseFuture future) throws Exception
                            {
                                if (future.isSuccess())
                                {
                                    storedPeerAddress = peerDHT.peerAddress();
                                    log.debug("storedPeerAddress = " + storedPeerAddress);
                                }
                                else
                                {
                                    log.error("");
                                }
                            }

                            @Override
                            public void exceptionCaught(Throwable t) throws Exception
                            {
                                log.error(t.toString());
                            }
                        });
                    }
                    else
                    {
                        log.error("peerDHT is null");
                    }
                } catch (IOException | ClassNotFoundException e)
                {
                    e.printStackTrace();
                    log.error(e.toString());
                }
            }

            @Override
            public void onFailure(@NotNull Throwable t)
            {
                log.error(t.toString());
            }
        });
        return bootstrapComplete;
    }

    private void setupReplyHandler()
    {
        peerDHT.peer().objectDataReply((sender, request) -> {
            if (!sender.equals(peerDHT.peer().peerAddress()))
                if (messageBroker != null) messageBroker.handleMessage(request, sender);
                else
                    log.error("Received msg from myself. That should never happen.");
            return null;
        });
    }

    private void setupTimerForIPCheck()
    {
        Timer timer = new Timer();
        long checkIfIPChangedPeriod = 600 * 1000;
        timer.scheduleAtFixedRate(new TimerTask()
        {
            @Override
            public void run()
            {
                if (peerDHT != null && !storedPeerAddress.equals(peerDHT.peerAddress()))
                {
                    try
                    {
                        storePeerAddress();
                    } catch (IOException | ClassNotFoundException e)
                    {
                        e.printStackTrace();
                        log.error(e.toString());
                    }
                }
            }
        }, checkIfIPChangedPeriod, checkIfIPChangedPeriod);
    }

    private FuturePut storePeerAddress() throws IOException, ClassNotFoundException
    {
        Number160 locationKey = Utils.makeSHAHash(keyPair.getPublic().getEncoded());
        Data data = new Data(peerDHT.peerAddress());
        return putDomainProtectedData(locationKey, data);
    }

    private void useDiscStorage(boolean useDiscStorage)
    {
        if (useDiscStorage)
        {
            try
            {

                File path = new File(StorageDirectory.getStorageDirectory().getCanonicalPath() + "/" + BitSquare.getAppName() + "_tomP2P");
                if (!path.exists())
                {
                    boolean created = path.mkdir();
                    if (!created)
                        throw new RuntimeException("Could not create the directory '" + path + "'");
                }
                storage = new StorageDisk(Number160.ZERO, path, new DSASignatureFactory());

            } catch (IOException e)
            {
                e.printStackTrace();
            }
        }
        else
        {
            storage = new StorageMemory();
        }
    }
}
