package io.bitsquare.msg;

import com.google.bitcoin.core.Utils;
import com.google.inject.Inject;
import io.bitsquare.btc.WalletFacade;
import javafx.application.Platform;
import net.tomp2p.connection.Bindings;
import net.tomp2p.connection.PeerConnection;
import net.tomp2p.futures.*;
import net.tomp2p.p2p.Peer;
import net.tomp2p.p2p.PeerMaker;
import net.tomp2p.peers.Number160;
import net.tomp2p.peers.PeerAddress;
import net.tomp2p.rpc.ObjectDataReply;
import net.tomp2p.storage.Data;
import net.tomp2p.storage.StorageDisk;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.security.KeyPair;
import java.security.PublicKey;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * That facade delivers messaging functionality from the TomP2P library
 * The TomP2P library codebase shall not be used outside that facade.
 * That way a change of the library will only affect that class.
 */
public class MessageFacade
{
    private static final Logger log = LoggerFactory.getLogger(MessageFacade.class);

    public static final String PING = "ping";
    public static final String PONG = "pong";
    private static final int MASTER_PEER_PORT = 5000;
    private static String MASTER_PEER_IP = "127.0.0.1";

    private Peer myPeerInstance;
    private int port;
    private KeyPair keyPair;
    private Peer masterPeer;
    private PeerAddress otherPeerAddress;
    private PeerConnection peerConnection;
    private List<MessageListener> messageListeners = new ArrayList<>();

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    public MessageFacade()
    {
        try
        {
            masterPeer = BootstrapMasterPeer.INSTANCE(MASTER_PEER_PORT);
        } catch (Exception e)
        {
            if (masterPeer != null)
                masterPeer.shutdown();
            log.info("masterPeer already instantiated by another app. " + e.getMessage());
        }
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Public Methods
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void init()
    {
        String keyName = WalletFacade.WALLET_PREFIX;
        port = Bindings.MAX_PORT - Math.abs(new Random().nextInt()) % (Bindings.MAX_PORT - Bindings.MIN_DYN_PORT);
        if (WalletFacade.WALLET_PREFIX.equals("taker"))
            port = 4501;
        else if (WalletFacade.WALLET_PREFIX.equals("offerer"))
            port = 4500;

        try
        {
            createMyPeerInstance(keyName, port);
            setupStorage();
            saveMyAddressToDHT();
            setupReplyHandler();
        } catch (IOException e)
        {
            shutDown();
            log.error("Error at setup peer" + e.getMessage());
        }

        //log.info("myPeerInstance knows: " + myPeerInstance.getPeerBean().getPeerMap().getAll());
    }

    public void shutDown()
    {
        if (peerConnection != null)
            peerConnection.close();

        if (myPeerInstance != null)
            myPeerInstance.shutdown();

        if (masterPeer != null)
            masterPeer.shutdown();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Publish offer
    ///////////////////////////////////////////////////////////////////////////////////////////

    //TODO use Offer and do proper serialisation here
    public void publishOffer(String currency, Object offerObject) throws IOException
    {
        Number160 locationKey = Number160.createHash(currency);
        Data offerData = new Data(offerObject);
        offerData.setTTLSeconds(5);
        FutureDHT putFuture = myPeerInstance.add(locationKey).setData(offerData).start();
        putFuture.addListener(new BaseFutureAdapter<BaseFuture>()
        {
            @Override
            public void operationComplete(BaseFuture future) throws Exception
            {
                Platform.runLater(() -> onOfferPublished(future.isSuccess()));
            }
        });
    }

    private void onOfferPublished(boolean success)
    {
        for (MessageListener messageListener : messageListeners)
            messageListener.onOfferPublished(success);
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Get offers
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void getOffers(String currency)
    {
        Number160 locationKey = Number160.createHash(currency);
        final FutureDHT getOffersFuture = myPeerInstance.get(locationKey).setAll().start();
        getOffersFuture.addListener(new BaseFutureAdapter<BaseFuture>()
        {
            @Override
            public void operationComplete(BaseFuture future) throws Exception
            {
                final Map<Number160, Data> dataMap = getOffersFuture.getDataMap();
                Platform.runLater(() -> onOffersReceived(dataMap, future.isSuccess()));
            }
        });
    }

    private void onOffersReceived(Map<Number160, Data> dataMap, boolean success)
    {
        for (MessageListener messageListener : messageListeners)
            messageListener.onOffersReceived(dataMap, success);
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Remove offer
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void removeOffer(String currency, Object offerObject) throws IOException
    {
        Data offerData = new Data(offerObject);
        Number160 locationKey = Number160.createHash(currency);
        Number160 contentKey = offerData.getHash();
        FutureDHT putFuture = myPeerInstance.remove(locationKey).setContentKey(contentKey).start();
        putFuture.addListener(new BaseFutureAdapter<BaseFuture>()
        {
            @Override
            public void operationComplete(BaseFuture future) throws Exception
            {
                Platform.runLater(() -> onOfferRemoved(future.isSuccess()));
            }
        });
    }

    private void onOfferRemoved(boolean success)
    {
        for (MessageListener messageListener : messageListeners)
            messageListener.onOfferRemoved(success);
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Send message
    ///////////////////////////////////////////////////////////////////////////////////////////

    public boolean sendMessage(String message)
    {
        boolean result = false;
        if (otherPeerAddress != null)
        {
            if (peerConnection != null)
                peerConnection.close();

            peerConnection = myPeerInstance.createPeerConnection(otherPeerAddress, 20);
            if (!peerConnection.isClosed())
            {
                FutureResponse sendFuture = myPeerInstance.sendDirect(peerConnection).setObject(message).start();
                sendFuture.addListener(new BaseFutureAdapter<BaseFuture>()
                {
                    @Override
                    public void operationComplete(BaseFuture baseFuture) throws Exception
                    {
                        if (sendFuture.isSuccess())
                        {
                            final Object object = sendFuture.getObject();
                            Platform.runLater(() -> onResponseFromSend(object));
                        }
                        else
                        {
                            Platform.runLater(() -> onSendFailed());
                        }
                    }
                }
                );
                result = true;
            }
        }
        return result;
    }

    private void onResponseFromSend(Object response)
    {
        for (MessageListener messageListener : messageListeners)
            messageListener.onResponseFromSend(response);
    }

    private void onSendFailed()
    {
        for (MessageListener messageListener : messageListeners)
            messageListener.onSendFailed();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Find peer
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void findPeer(String pubKeyAsHex)
    {
        final FutureDHT getPeerAddressFuture = myPeerInstance.get(getPubKeyHash(pubKeyAsHex)).start();
        getPeerAddressFuture.addListener(new BaseFutureAdapter<BaseFuture>()
        {
            @Override
            public void operationComplete(BaseFuture baseFuture) throws Exception
            {
                final PeerAddress peerAddress = (PeerAddress) getPeerAddressFuture.getData().getObject();
                Platform.runLater(() -> onPeerFound(peerAddress));
            }
        });
    }

    private void onPeerFound(PeerAddress peerAddress)
    {
        if (!peerAddress.equals(myPeerInstance.getPeerAddress()))
        {
            otherPeerAddress = peerAddress;
            for (MessageListener messageListener : messageListeners)
                messageListener.onPeerFound();
        }
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Misc
    ///////////////////////////////////////////////////////////////////////////////////////////

    public boolean isOtherPeerDefined()
    {
        return otherPeerAddress != null;
    }

    public String getPubKeyAsHex()
    {
        return Utils.bytesToHexString(keyPair.getPublic().getEncoded());
    }

    public PublicKey getPubKey()
    {
        return keyPair.getPublic();
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Event Listeners
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void addMessageListener(MessageListener listener)
    {
        messageListeners.add(listener);
    }

    public void removeMessageListener(MessageListener listener)
    {
        messageListeners.remove(listener);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Private Methods
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void createMyPeerInstance(String keyName, int port) throws IOException
    {
        keyPair = MsgKeyUtil.getKeyPair(keyName);
        myPeerInstance = new PeerMaker(keyPair).setPorts(port).makeAndListen();

        //TODO use list of multiple master bootstrap peers
        /*PeerAddress bootstrapServerPeerAddress = new PeerAddress(BootstrapMasterPeer.ID, new InetSocketAddress(InetAddress.getByName(MASTER_PEER_IP), port));
        FutureBootstrap futureBootstrap = myPeerInstance.bootstrap().setPeerAddress(bootstrapServerPeerAddress).start();
         */
        FutureBootstrap futureBootstrap = myPeerInstance.bootstrap().setBroadcast().setPorts(MASTER_PEER_PORT).start();
        if (futureBootstrap != null)
        {
            futureBootstrap.awaitUninterruptibly();
            if (futureBootstrap.getBootstrapTo() != null)
            {
                PeerAddress peerAddress = futureBootstrap.getBootstrapTo().iterator().next();
                myPeerInstance.discover().setPeerAddress(peerAddress).start().awaitUninterruptibly();
            }
        }
    }

    private void setupStorage() throws IOException
    {
        //TODO WalletFacade.WALLET_PREFIX just temp...
        String dirPath = io.bitsquare.util.Utils.getRootDir() + "tomP2P_" + WalletFacade.WALLET_PREFIX;
        File dirFile = new File(dirPath);
        boolean success = true;
        if (!dirFile.exists())
            success = dirFile.mkdir();

        if (success)
            myPeerInstance.getPeerBean().setStorage(new StorageDisk(dirPath));
        else
            log.warn("Unable to create directory " + dirPath);
    }

    private void saveMyAddressToDHT() throws IOException
    {
        myPeerInstance.put(getPubKeyHash(getPubKeyAsHex())).setData(new Data(myPeerInstance.getPeerAddress())).start();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Incoming message handler
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void setupReplyHandler()
    {
        myPeerInstance.setObjectDataReply(new ObjectDataReply()
        {
            @Override
            public Object reply(PeerAddress sender, Object request) throws Exception
            {
                String reply = null;
                if (!sender.equals(myPeerInstance.getPeerAddress()))
                {
                    otherPeerAddress = sender;

                    Platform.runLater(() -> onMessage(request));
                    if (request.equals(PING))
                    {
                        Platform.runLater(() -> onPing());
                    }
                }
                return reply;
            }
        });
    }

    private void onMessage(Object message)
    {
        for (MessageListener messageListener : messageListeners)
            messageListener.onMessage(message);
    }

    private void onPing()
    {
        for (MessageListener messageListener : messageListeners)
            messageListener.onPing();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Utils
    ///////////////////////////////////////////////////////////////////////////////////////////

    private Number160 getPubKeyHash(String pubKeyAsHex)
    {
        return net.tomp2p.utils.Utils.makeSHAHash(pubKeyAsHex);
    }

}
