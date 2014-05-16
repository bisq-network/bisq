package io.bitsquare.msg;

import com.google.inject.Inject;
import io.bitsquare.BitSquare;
import io.bitsquare.msg.listeners.*;
import io.bitsquare.trade.Offer;
import io.bitsquare.trade.offerer.OffererPaymentProtocol;
import io.bitsquare.trade.taker.TakerPaymentProtocol;
import io.bitsquare.util.DSAKeyUtil;
import io.bitsquare.util.Utilities;
import javafx.application.Platform;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
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
import java.util.*;

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
    private static String MASTER_PEER_IP = "192.168.1.33";

    private Peer myPeer;
    private int port;
    private KeyPair keyPair;
    private Peer masterPeer;
    private List<OrderBookListener> orderBookListeners = new ArrayList<>();
    private List<TakeOfferRequestListener> takeOfferRequestListeners = new ArrayList<>();

    // //TODO change to map (key: offerID) instead of list (offererPaymentProtocols, takerPaymentProtocols)
    private List<TakerPaymentProtocol> takerPaymentProtocols = new ArrayList<>();
    private List<OffererPaymentProtocol> offererPaymentProtocols = new ArrayList<>();

    private List<PingPeerListener> pingPeerListeners = new ArrayList<>();

    private Long lastTimeStamp = -3L;


    private BooleanProperty isDirty = new SimpleBooleanProperty(false);


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    public MessageFacade()
    {
       /* try
        {
            masterPeer = BootstrapMasterPeer.INSTANCE(MASTER_PEER_PORT);
        } catch (Exception e)
        {
            if (masterPeer != null)
                masterPeer.shutdown();
            System.err.println("masterPeer already instantiated by another app. " + e.getMessage());
        }  */
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Public Methods
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void init()
    {
        String keyName = BitSquare.ID;
        port = Bindings.MAX_PORT - Math.abs(new Random().nextInt()) % (Bindings.MAX_PORT - Bindings.MIN_DYN_PORT);
        if (BitSquare.ID.equals("taker"))
            port = 4501;
        else if (BitSquare.ID.equals("offerer"))
            port = 4500;

        try
        {
            createMyPeerInstance(keyName, port);
            //setupStorage();
            //TODO save periodically or get informed if network address changes
            saveMyAddressToDHT();
            setupReplyHandler();
        } catch (IOException e)
        {
            shutDown();
            log.error("Error at setup myPeerInstance" + e.getMessage());
        }
    }

    public void shutDown()
    {
        if (myPeer != null)
            myPeer.shutdown();

        if (masterPeer != null)
            masterPeer.shutdown();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Publish offer
    ///////////////////////////////////////////////////////////////////////////////////////////

    //TODO use Offer and do proper serialisation here
    public void addOffer(Offer offer) throws IOException
    {
        Number160 locationKey = Number160.createHash(offer.getCurrency().getCurrencyCode());
        Number160 contentKey = Number160.createHash(offer.getUid());
        final Data offerData = new Data(offer);
        //offerData.setTTLSeconds(5);
        final FutureDHT addFuture = myPeer.put(locationKey).setData(contentKey, offerData).start();
        addFuture.addListener(new BaseFutureAdapter<BaseFuture>()
        {
            @Override
            public void operationComplete(BaseFuture future) throws Exception
            {
                Platform.runLater(() -> onOfferAdded(offerData, future.isSuccess(), locationKey));
            }
        });
    }

    private void onOfferAdded(Data offerData, boolean success, Number160 locationKey)
    {
        setDirty(locationKey);

        for (OrderBookListener orderBookListener : orderBookListeners)
            orderBookListener.onOfferAdded(offerData, success);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Get offers
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void getOffers(String currency)
    {
        final Number160 locationKey = Number160.createHash(currency);
        final FutureDHT getOffersFuture = myPeer.get(locationKey).setAll().start();
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
        for (OrderBookListener orderBookListener : orderBookListeners)
            orderBookListener.onOffersReceived(dataMap, success);
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Remove offer
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void removeOffer(Offer offer) throws IOException
    {
        Number160 locationKey = Number160.createHash(offer.getCurrency().getCurrencyCode());
        Number160 contentKey = Number160.createHash(offer.getUid());
        log.debug("removeOffer");
        FutureDHT removeFuture = myPeer.remove(locationKey).setReturnResults().setContentKey(contentKey).start();
        removeFuture.addListener(new BaseFutureAdapter<BaseFuture>()
        {
            @Override
            public void operationComplete(BaseFuture future) throws Exception
            {
                Data data = removeFuture.getData();
                Platform.runLater(() -> onOfferRemoved(data, future.isSuccess(), locationKey));
            }
        });
    }

    private void onOfferRemoved(Data data, boolean success, Number160 locationKey)
    {
        log.debug("onOfferRemoved");
        setDirty(locationKey);

        for (OrderBookListener orderBookListener : orderBookListeners)
            orderBookListener.onOfferRemoved(data, success);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Check dirty flag for a location key
    ///////////////////////////////////////////////////////////////////////////////////////////

    public BooleanProperty getIsDirtyProperty()
    {
        return isDirty;
    }

    public void getDirtyFlag(Currency currency) throws IOException
    {
        Number160 locationKey = Number160.createHash(currency.getCurrencyCode());
        FutureDHT getFuture = myPeer.get(getDirtyLocationKey(locationKey)).start();
        getFuture.addListener(new BaseFutureListener<BaseFuture>()
        {
            @Override
            public void operationComplete(BaseFuture future) throws Exception
            {
                Data data = getFuture.getData();
                if (data != null)
                {
                    Object object = data.getObject();
                    if (object instanceof Long)
                    {
                        final long lastTimeStamp = (Long) object;
                        //System.out.println("getDirtyFlag " + lastTimeStamp);
                        Platform.runLater(() -> onGetDirtyFlag(lastTimeStamp));
                    }
                }
            }

            @Override
            public void exceptionCaught(Throwable t) throws Exception
            {
                System.out.println("getFuture exceptionCaught " + System.currentTimeMillis());
            }
        });
    }

    private void onGetDirtyFlag(long timeStamp)
    {
        // TODO don't get updates at first run....
        if (lastTimeStamp != timeStamp)
        {
            isDirty.setValue(!isDirty.get());
        }
        if (lastTimeStamp > 0)
            lastTimeStamp = timeStamp;
        else
            lastTimeStamp++;
    }

    private Number160 getDirtyLocationKey(Number160 locationKey) throws IOException
    {
        return Number160.createHash(locationKey.toString() + "Dirty");
    }

    private void setDirty(Number160 locationKey)
    {
        // we don't want to get an update from dirty for own changes, so update the lastTimeStamp to omit a change trigger
        lastTimeStamp = System.currentTimeMillis();
        FutureDHT putFuture = null;
        try
        {
            putFuture = myPeer.put(getDirtyLocationKey(locationKey)).setData(new Data(lastTimeStamp)).start();
        } catch (IOException e)
        {
            log.warn("Error at writing dirty flag (timeStamp) " + e.getMessage());
        }
        putFuture.addListener(new BaseFutureListener<BaseFuture>()
        {
            @Override
            public void operationComplete(BaseFuture future) throws Exception
            {
                //System.out.println("operationComplete");
            }

            @Override
            public void exceptionCaught(Throwable t) throws Exception
            {
                System.err.println("exceptionCaught");
            }
        });
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Send message
    ///////////////////////////////////////////////////////////////////////////////////////////

   /* public boolean sendMessage(Object message)
    {
        boolean result = false;
        if (otherPeerAddress != null)
        {
            if (peerConnection != null)
                peerConnection.close();

            peerConnection = myPeer.createPeerConnection(otherPeerAddress, 20);
            if (!peerConnection.isClosed())
            {
                FutureResponse sendFuture = myPeer.sendDirect(peerConnection).setObject(message).start();
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
    } */
      /*
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
      */

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Find peer address
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void getPeerAddress(final String pubKeyAsHex, AddressLookupListener listener)
    {
        final Number160 location = Number160.createHash(pubKeyAsHex);
        final FutureDHT getPeerAddressFuture = myPeer.get(location).start();
        getPeerAddressFuture.addListener(new BaseFutureAdapter<BaseFuture>()
        {
            @Override
            public void operationComplete(BaseFuture baseFuture) throws Exception
            {
                if (baseFuture.isSuccess() && getPeerAddressFuture.getData() != null)
                {
                    final PeerAddress peerAddress = (PeerAddress) getPeerAddressFuture.getData().getObject();
                    Platform.runLater(() -> onAddressFound(peerAddress, listener));
                }
                else
                {
                    Platform.runLater(() -> onGetPeerAddressFailed(listener));
                }
            }
        });
    }

    private void onAddressFound(final PeerAddress peerAddress, AddressLookupListener listener)
    {
        listener.onResult(peerAddress);
    }

    private void onGetPeerAddressFailed(AddressLookupListener listener)
    {
        listener.onFailed();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Trade process
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void sendTradeMessage(final PeerAddress peerAddress, final TradeMessage tradeMessage, TradeMessageListener listener)
    {
        final PeerConnection peerConnection = myPeer.createPeerConnection(peerAddress, 10);
        final FutureResponse sendFuture = myPeer.sendDirect(peerConnection).setObject(tradeMessage).start();
        sendFuture.addListener(new BaseFutureAdapter<BaseFuture>()
        {
            @Override
            public void operationComplete(BaseFuture baseFuture) throws Exception
            {
                if (sendFuture.isSuccess())
                {
                    Platform.runLater(() -> onSendTradingMessageResult(listener));
                }
                else
                {
                    Platform.runLater(() -> onSendTradingMessageFailed(listener));
                }
            }
        }
        );
    }

    private void onSendTradingMessageResult(TradeMessageListener listener)
    {
        listener.onResult();
    }

    private void onSendTradingMessageFailed(TradeMessageListener listener)
    {
        listener.onFailed();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Process incoming tradingMessage
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void processTradingMessage(TradeMessage tradeMessage, PeerAddress sender)
    {
        //TODO change to map (key: offerID) instead of list (offererPaymentProtocols, takerPaymentProtocols)
        log.info("processTradingMessage " + tradeMessage.getType().toString());
        switch (tradeMessage.getType())
        {
            case REQUEST_TAKE_OFFER:
                // That is used to initiate the OffererPaymentProtocol and to show incoming requests in the view
                for (TakeOfferRequestListener takeOfferRequestListener : takeOfferRequestListeners)
                    takeOfferRequestListener.onTakeOfferRequested(tradeMessage, sender);
                break;
            case ACCEPT_TAKE_OFFER_REQUEST:
                for (TakerPaymentProtocol takeOfferTradeListener : takerPaymentProtocols)
                    takeOfferTradeListener.onTakeOfferRequestAccepted();
                break;
            case REJECT_TAKE_OFFER_REQUEST:
                for (TakerPaymentProtocol takeOfferTradeListener : takerPaymentProtocols)
                    takeOfferTradeListener.onTakeOfferRequestRejected();
                break;
            case TAKE_OFFER_FEE_PAYED:
                for (OffererPaymentProtocol offererPaymentProtocol : offererPaymentProtocols)
                    offererPaymentProtocol.onTakeOfferFeePayed(tradeMessage);
                break;
            case REQUEST_TAKER_DEPOSIT_PAYMENT:
                for (TakerPaymentProtocol takeOfferTradeListener : takerPaymentProtocols)
                    takeOfferTradeListener.onTakerDepositPaymentRequested(tradeMessage);
                break;
            case REQUEST_OFFERER_DEPOSIT_PUBLICATION:
                for (OffererPaymentProtocol offererPaymentProtocol : offererPaymentProtocols)
                    offererPaymentProtocol.onDepositTxReadyForPublication(tradeMessage);
                break;
            case DEPOSIT_TX_PUBLISHED:
                for (TakerPaymentProtocol takeOfferTradeListener : takerPaymentProtocols)
                    takeOfferTradeListener.onDepositTxPublished(tradeMessage);
                break;
            case BANK_TX_INITED:
                for (TakerPaymentProtocol takeOfferTradeListener : takerPaymentProtocols)
                    takeOfferTradeListener.onBankTransferInited(tradeMessage);
                break;
            case PAYOUT_TX_PUBLISHED:
                for (OffererPaymentProtocol offererPaymentProtocol : offererPaymentProtocols)
                    offererPaymentProtocol.onPayoutTxPublished(tradeMessage);
                break;

            default:
                log.info("default");
                break;
        }
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Ping peer
    ///////////////////////////////////////////////////////////////////////////////////////////
    //TODO not working anymore...
    public void pingPeer(String publicKeyAsHex)
    {
        Number160 location = Number160.createHash(publicKeyAsHex);
        final FutureDHT getPeerAddressFuture = myPeer.get(location).start();
        getPeerAddressFuture.addListener(new BaseFutureAdapter<BaseFuture>()
        {
            @Override
            public void operationComplete(BaseFuture baseFuture) throws Exception
            {
                final Data data = getPeerAddressFuture.getData();
                if (data != null && data.getObject() instanceof PeerAddress)
                {
                    final PeerAddress peerAddress = (PeerAddress) data.getObject();
                    Platform.runLater(() -> onAddressFoundPingPeer(peerAddress));
                }
            }
        });
    }


    private void onAddressFoundPingPeer(PeerAddress peerAddress)
    {
        try
        {
            final PeerConnection peerConnection = myPeer.createPeerConnection(peerAddress, 10);
            if (!peerConnection.isClosed())
            {
                FutureResponse sendFuture = myPeer.sendDirect(peerConnection).setObject(PING).start();
                sendFuture.addListener(new BaseFutureAdapter<BaseFuture>()
                {
                    @Override
                    public void operationComplete(BaseFuture baseFuture) throws Exception
                    {
                        if (sendFuture.isSuccess())
                        {
                            final String pong = (String) sendFuture.getObject();
                            if (pong != null)
                                Platform.runLater(() -> onResponseFromPing(pong.equals(PONG)));
                        }
                        else
                        {
                            peerConnection.close();
                            Platform.runLater(() -> onResponseFromPing(false));
                        }
                    }
                }
                );
            }
        } catch (Exception e)
        {
            //  ClosedChannelException can happen, check out if there is a better way to ping a myPeerInstance for online status
        }
    }

    private void onResponseFromPing(boolean success)
    {
        for (PingPeerListener pingPeerListener : pingPeerListeners)
            pingPeerListener.onPingPeerResult(success);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Misc
    ///////////////////////////////////////////////////////////////////////////////////////////


    public PublicKey getPubKey()
    {
        return keyPair.getPublic();
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Event Listeners
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void addMessageListener(OrderBookListener listener)
    {
        orderBookListeners.add(listener);
    }

    public void removeMessageListener(OrderBookListener listener)
    {
        orderBookListeners.remove(listener);
    }

    public void addTakeOfferRequestListener(TakeOfferRequestListener listener)
    {
        takeOfferRequestListeners.add(listener);
    }

    public void removeTakeOfferRequestListener(TakeOfferRequestListener listener)
    {
        takeOfferRequestListeners.remove(listener);
    }

    public void addTakerPaymentProtocol(TakerPaymentProtocol listener)
    {
        takerPaymentProtocols.add(listener);
    }

    public void removeTakerPaymentProtocol(TakerPaymentProtocol listener)
    {
        takerPaymentProtocols.remove(listener);
    }

    public void addOffererPaymentProtocol(OffererPaymentProtocol listener)
    {
        offererPaymentProtocols.add(listener);
    }

    public void removeOffererPaymentProtocol(OffererPaymentProtocol listener)
    {
        offererPaymentProtocols.remove(listener);
    }

    public void addPingPeerListener(PingPeerListener listener)
    {
        pingPeerListeners.add(listener);
    }

    public void removePingPeerListener(PingPeerListener listener)
    {
        pingPeerListeners.remove(listener);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Private Methods
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void createMyPeerInstance(String keyName, int port) throws IOException
    {
        keyPair = DSAKeyUtil.getKeyPair(keyName);
        myPeer = new PeerMaker(keyPair).setPorts(port).makeAndListen();
        final FutureBootstrap futureBootstrap = myPeer.bootstrap().setBroadcast().setPorts(MASTER_PEER_PORT).start();
        // futureBootstrap.awaitUninterruptibly();
        futureBootstrap.addListener(new BaseFutureAdapter<BaseFuture>()
        {
            @Override
            public void operationComplete(BaseFuture future) throws Exception
            {
                if (futureBootstrap.getBootstrapTo() != null)
                {
                    PeerAddress masterPeerAddress = futureBootstrap.getBootstrapTo().iterator().next();
                    final FutureDiscover futureDiscover = myPeer.discover().setPeerAddress(masterPeerAddress).start();
                    //futureDiscover.awaitUninterruptibly();
                    futureDiscover.addListener(new BaseFutureListener<BaseFuture>()
                    {
                        @Override
                        public void operationComplete(BaseFuture future) throws Exception
                        {
                            //System.out.println("operationComplete");
                        }

                        @Override
                        public void exceptionCaught(Throwable t) throws Exception
                        {
                            System.err.println("exceptionCaught");
                        }
                    });
                }
            }
        });
    }

    private void setupStorage() throws IOException
    {
        //TODO BitSquare.ID just temp...
        String dirPath = Utilities.getRootDir() + BitSquare.ID + "_tomP2P";
        File dirFile = new File(dirPath);
        boolean success = true;
        if (!dirFile.exists())
            success = dirFile.mkdir();

        if (success)
            myPeer.getPeerBean().setStorage(new StorageDisk(dirPath));
        else
            log.warn("Unable to create directory " + dirPath);
    }

    private void saveMyAddressToDHT() throws IOException
    {
        Number160 location = Number160.createHash(DSAKeyUtil.getHexStringFromPublicKey(getPubKey()));
        //log.debug("saveMyAddressToDHT location "+location.toString());
        myPeer.put(location).setData(new Data(myPeer.getPeerAddress())).start();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Incoming message handler
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void setupReplyHandler()
    {
        myPeer.setObjectDataReply(new ObjectDataReply()
        {
            @Override
            public Object reply(PeerAddress sender, Object request) throws Exception
            {
                if (!sender.equals(myPeer.getPeerAddress()))
                {
                    Platform.runLater(() -> onMessage(request, sender));
                }
                return null;
            }
        });
    }

    private void onMessage(Object request, PeerAddress sender)
    {
        if (request instanceof TradeMessage)
        {
            processTradingMessage((TradeMessage) request, sender);
        }
       /* else
        {
            for (OrderBookListener orderBookListener : orderBookListeners)
                orderBookListener.onMessage(request);
        }  */
    }


}
