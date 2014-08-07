package io.bitsquare.msg;

import com.google.common.util.concurrent.FutureCallback;
import com.google.inject.name.Named;
import io.bitsquare.msg.listeners.*;
import io.bitsquare.trade.Offer;
import io.bitsquare.trade.protocol.TradeMessage;
import io.bitsquare.user.Arbitrator;
import io.bitsquare.user.User;
import java.io.IOException;
import java.security.PublicKey;
import java.util.ArrayList;
import java.util.Currency;
import java.util.List;
import java.util.Locale;
import javafx.application.Platform;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javax.annotation.Nullable;
import javax.inject.Inject;
import net.tomp2p.dht.FutureGet;
import net.tomp2p.dht.FuturePut;
import net.tomp2p.dht.FutureRemove;
import net.tomp2p.dht.PeerDHT;
import net.tomp2p.futures.BaseFuture;
import net.tomp2p.futures.BaseFutureAdapter;
import net.tomp2p.futures.BaseFutureListener;
import net.tomp2p.futures.FutureDirect;
import net.tomp2p.peers.Number160;
import net.tomp2p.peers.PeerAddress;
import net.tomp2p.storage.Data;
import net.tomp2p.utils.Utils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * That facade delivers direct messaging and DHT functionality from the TomP2P library
 * It is the translating domain specific functionality to the messaging layer.
 * The TomP2P library codebase shall not be used outside that facade.
 * That way we limit the dependency of the TomP2P library only to that class (and it's sub components).
 * <p>
 * TODO: improve callbacks that Platform.runLater is not necessary. We call usually that methods form teh UI thread.
 */
public class MessageFacade implements MessageBroker
{

    public static interface AddOfferListener
    {
        void onComplete();

        void onFailed(String reason, Throwable throwable);
    }

    private static final Logger log = LoggerFactory.getLogger(MessageFacade.class);
    private static final String ARBITRATORS_ROOT = "ArbitratorsRoot";

    public P2PNode getP2pNode()
    {
        return p2pNode;
    }

    private P2PNode p2pNode;

    private final List<OrderBookListener> orderBookListeners = new ArrayList<>();
    private final List<ArbitratorListener> arbitratorListeners = new ArrayList<>();
    private final List<IncomingTradeMessageListener> incomingTradeMessageListeners = new ArrayList<>();
    private User user;
    private Boolean useDiskStorage;
    private SeedNodeAddress.StaticSeedNodeAddresses defaultStaticSeedNodeAddresses;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    public MessageFacade(User user, @Named("useDiskStorage") Boolean useDiskStorage, @Named("defaultSeedNode") SeedNodeAddress.StaticSeedNodeAddresses defaultStaticSeedNodeAddresses)
    {
        this.user = user;
        this.useDiskStorage = useDiskStorage;
        this.defaultStaticSeedNodeAddresses = defaultStaticSeedNodeAddresses;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Public Methods
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void init(BootstrapListener bootstrapListener)
    {
        p2pNode = new P2PNode(user.getMessageKeyPair(), useDiskStorage, defaultStaticSeedNodeAddresses, this);
        p2pNode.start(new FutureCallback<PeerDHT>()
        {
            @Override
            public void onSuccess(@Nullable PeerDHT result)
            {
                log.debug("p2pNode.start success result = " + result);
                Platform.runLater(() -> bootstrapListener.onCompleted());
            }

            @Override
            public void onFailure(Throwable t)
            {
                log.error(t.toString());
                Platform.runLater(() -> bootstrapListener.onFailed(t));
            }
        });
    }

    public void shutDown()
    {
        if (p2pNode != null)
            p2pNode.shutDown();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Find peer address by publicKey
    ///////////////////////////////////////////////////////////////////////////////////////////


    public void getPeerAddress(PublicKey publicKey, GetPeerAddressListener listener)
    {
        final Number160 locationKey = Utils.makeSHAHash(publicKey.getEncoded());
        try
        {
            FutureGet futureGet = p2pNode.getDomainProtectedData(locationKey, publicKey);

            futureGet.addListener(new BaseFutureAdapter<BaseFuture>()
            {
                @Override
                public void operationComplete(BaseFuture baseFuture) throws Exception
                {
                    if (baseFuture.isSuccess() && futureGet.data() != null)
                    {
                        final PeerAddress peerAddress = (PeerAddress) futureGet.data().object();
                        Platform.runLater(() -> listener.onResult(peerAddress));
                    }
                    else
                    {
                        Platform.runLater(() -> listener.onFailed());
                    }
                }
            });
        } catch (IOException | ClassNotFoundException e)
        {
            e.printStackTrace();
            log.error(e.toString());
        }
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Offer
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void addOffer(Offer offer, AddOfferListener addOfferListener)
    {
        Number160 locationKey = Number160.createHash(offer.getCurrency().getCurrencyCode());
        try
        {
            final Data data = new Data(offer);
            // the offer is default 30 days valid
            int defaultOfferTTL = 30 * 24 * 60 * 60 * 1000;
            data.ttlSeconds(defaultOfferTTL);

            FuturePut futurePut = p2pNode.addProtectedData(locationKey, data);
            futurePut.addListener(new BaseFutureListener<BaseFuture>()
            {
                @Override
                public void operationComplete(BaseFuture future) throws Exception
                {
                    if (future.isSuccess())
                    {
                        Platform.runLater(() -> {
                            addOfferListener.onComplete();
                            orderBookListeners.stream().forEach(listener -> listener.onOfferAdded(data, future.isSuccess()));

                            // TODO will be removed when we don't use polling anymore
                            setDirty(locationKey);
                            log.trace("Add offer to DHT was successful. Stored data: [key: " + locationKey + ", value: " + data + "]");
                        });
                    }
                    else
                    {
                        Platform.runLater(() -> {
                            addOfferListener.onFailed("Add offer to DHT failed.", new Exception("Add offer to DHT failed. Reason: " + future.failedReason()));
                            log.error("Add offer to DHT failed. Reason: " + future.failedReason());
                        });
                    }
                }

                @Override
                public void exceptionCaught(Throwable t) throws Exception
                {
                    Platform.runLater(() -> {
                        addOfferListener.onFailed("Add offer to DHT failed with an exception.", t);
                        log.error("Add offer to DHT failed with an exception: " + t.getMessage());
                    });
                }
            });
        } catch (IOException | ClassNotFoundException e)
        {
            Platform.runLater(() -> {
                addOfferListener.onFailed("Add offer to DHT failed with an exception.", e);
                log.error("Add offer to DHT failed with an exception: " + e.getMessage());
            });
        }
    }

    public void removeOffer(Offer offer)
    {
        Number160 locationKey = Number160.createHash(offer.getCurrency().getCurrencyCode());
        try
        {
            final Data data = new Data(offer);
            FutureRemove futureRemove = p2pNode.removeFromDataMap(locationKey, data);
            futureRemove.addListener(new BaseFutureListener<BaseFuture>()
            {
                @Override
                public void operationComplete(BaseFuture future) throws Exception
                {
                    Platform.runLater(() -> {
                        orderBookListeners.stream().forEach(orderBookListener -> orderBookListener.onOfferRemoved(data, future.isSuccess()));
                        setDirty(locationKey);
                    });
                    if (future.isSuccess())
                    {
                        log.trace("Remove offer from DHT was successful. Stored data: [key: " + locationKey + ", value: " + data + "]");
                    }
                    else
                    {
                        log.error("Remove offer from DHT failed. Reason: " + future.failedReason());
                    }
                }

                @Override
                public void exceptionCaught(Throwable t) throws Exception
                {
                    log.error(t.toString());
                }
            });
        } catch (IOException | ClassNotFoundException e)
        {
            e.printStackTrace();
        }
    }

    public void getOffers(String currencyCode)
    {
        Number160 locationKey = Number160.createHash(currencyCode);
        FutureGet futureGet = p2pNode.getDataMap(locationKey);
        futureGet.addListener(new BaseFutureAdapter<BaseFuture>()
        {
            @Override
            public void operationComplete(BaseFuture baseFuture) throws Exception
            {
                Platform.runLater(() -> orderBookListeners.stream().forEach(orderBookListener -> orderBookListener.onOffersReceived(futureGet.dataMap(), baseFuture.isSuccess())));
                if (baseFuture.isSuccess())
                {
                    log.trace("Get offers from DHT was successful. Stored data: [key: " + locationKey + ", values: " + futureGet.dataMap() + "]");
                }
                else
                {
                    log.error("Get offers from DHT failed with reason:" + baseFuture.failedReason());
                }
            }
        });
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Trade process
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void sendTradeMessage(PeerAddress peerAddress, TradeMessage tradeMessage, OutgoingTradeMessageListener listener)
    {
        FutureDirect futureDirect = p2pNode.sendData(peerAddress, tradeMessage);
        futureDirect.addListener(new BaseFutureListener<BaseFuture>()
        {
            @Override
            public void operationComplete(BaseFuture future) throws Exception
            {
                if (futureDirect.isSuccess())
                {
                    Platform.runLater(() -> listener.onResult());
                }
                else
                {
                    Platform.runLater(() -> listener.onFailed());
                }
            }

            @Override
            public void exceptionCaught(Throwable t) throws Exception
            {
                Platform.runLater(() -> listener.onFailed());
            }
        });
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Arbitrators
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void addArbitrator(Arbitrator arbitrator)
    {
        Number160 locationKey = Number160.createHash(ARBITRATORS_ROOT);
        try
        {
            final Data arbitratorData = new Data(arbitrator);

            FuturePut addFuture = p2pNode.addProtectedData(locationKey, arbitratorData);
            addFuture.addListener(new BaseFutureAdapter<BaseFuture>()
            {
                @Override
                public void operationComplete(BaseFuture future) throws Exception
                {
                    Platform.runLater(() -> arbitratorListeners.stream().forEach(listener -> listener.onArbitratorAdded(arbitratorData, addFuture.isSuccess())));
                    if (addFuture.isSuccess())
                    {
                        log.trace("Add arbitrator to DHT was successful. Stored data: [key: " + locationKey + ", values: " + arbitratorData + "]");
                    }
                    else
                    {
                        log.error("Add arbitrator to DHT failed with reason:" + addFuture.failedReason());
                    }
                }
            });
        } catch (IOException e)
        {
            e.printStackTrace();
        } catch (ClassNotFoundException e)
        {
            e.printStackTrace();
        }
    }

    public void removeArbitrator(Arbitrator arbitrator) throws IOException, ClassNotFoundException
    {
        Number160 locationKey = Number160.createHash(ARBITRATORS_ROOT);
        final Data arbitratorData = new Data(arbitrator);
        FutureRemove removeFuture = p2pNode.removeFromDataMap(locationKey, arbitratorData);
        removeFuture.addListener(new BaseFutureAdapter<BaseFuture>()
        {
            @Override
            public void operationComplete(BaseFuture future) throws Exception
            {
                Platform.runLater(() -> arbitratorListeners.stream().forEach(listener -> listener.onArbitratorRemoved(arbitratorData, removeFuture.isSuccess())));
                if (removeFuture.isSuccess())
                {
                    log.trace("Remove arbitrator from DHT was successful. Stored data: [key: " + locationKey + ", values: " + arbitratorData + "]");
                }
                else
                {
                    log.error("Remove arbitrators from DHT failed with reason:" + removeFuture.failedReason());
                }
            }
        });
    }

    public void getArbitrators(Locale languageLocale)
    {
        Number160 locationKey = Number160.createHash(ARBITRATORS_ROOT);
        FutureGet futureGet = p2pNode.getDataMap(locationKey);
        futureGet.addListener(new BaseFutureAdapter<BaseFuture>()
        {
            @Override
            public void operationComplete(BaseFuture baseFuture) throws Exception
            {
                Platform.runLater(() -> arbitratorListeners.stream().forEach(listener -> listener.onArbitratorsReceived(futureGet.dataMap(), baseFuture.isSuccess())));
                if (baseFuture.isSuccess())
                {
                    log.trace("Get arbitrators from DHT was successful. Stored data: [key: " + locationKey + ", values: " + futureGet.dataMap() + "]");
                }
                else
                {
                    log.error("Get arbitrators from DHT failed with reason:" + baseFuture.failedReason());
                }
            }
        });
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Event Listeners
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void addOrderBookListener(OrderBookListener listener)
    {
        orderBookListeners.add(listener);
    }

    public void removeOrderBookListener(OrderBookListener listener)
    {
        orderBookListeners.remove(listener);
    }

   /* public void addPingPeerListener(PingPeerListener listener)
    {
        pingPeerListeners.add(listener);
    }

    public void removePingPeerListener(PingPeerListener listener)
    {
        pingPeerListeners.remove(listener);
    }    */

    public void addArbitratorListener(ArbitratorListener listener)
    {
        arbitratorListeners.add(listener);
    }

    public void removeArbitratorListener(ArbitratorListener listener)
    {
        arbitratorListeners.remove(listener);
    }

    public void addIncomingTradeMessageListener(IncomingTradeMessageListener listener)
    {
        incomingTradeMessageListeners.add(listener);
    }

    public void removeIncomingTradeMessageListener(IncomingTradeMessageListener listener)
    {
        incomingTradeMessageListeners.remove(listener);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Check dirty flag for a location key
    ///////////////////////////////////////////////////////////////////////////////////////////

    // TODO just temp...
    public BooleanProperty getIsDirtyProperty()
    {
        return isDirty;
    }

    public void getDirtyFlag(Currency currency)
    {
        Number160 locationKey = Number160.createHash(currency.getCurrencyCode());
        try
        {
            FutureGet getFuture = p2pNode.getData(getDirtyLocationKey(locationKey));
            getFuture.addListener(new BaseFutureListener<BaseFuture>()
            {
                @Override
                public void operationComplete(BaseFuture future) throws Exception
                {
                    Data data = getFuture.data();
                    if (data != null)
                    {
                        Object object = data.object();
                        if (object instanceof Long)
                        {
                            Platform.runLater(() -> onGetDirtyFlag((Long) object));
                        }
                    }
                }

                @Override
                public void exceptionCaught(Throwable t) throws Exception
                {
                    log.error("getFuture exceptionCaught " + t.toString());
                }
            });
        } catch (IOException | ClassNotFoundException e)
        {
            e.printStackTrace();
        }
    }

    private Long lastTimeStamp = -3L;
    private final BooleanProperty isDirty = new SimpleBooleanProperty(false);

    private void onGetDirtyFlag(long timeStamp)
    {
        // TODO don't get updates at first execute....
        if (lastTimeStamp != timeStamp)
        {
            isDirty.setValue(!isDirty.get());
        }
        if (lastTimeStamp > 0)
        {
            lastTimeStamp = timeStamp;
        }
        else
        {
            lastTimeStamp++;
        }
    }

    public void setDirty(Number160 locationKey)
    {
        // we don't want to get an update from dirty for own changes, so update the lastTimeStamp to omit a change trigger
        lastTimeStamp = System.currentTimeMillis();
        try
        {
            FuturePut putFuture = p2pNode.putData(getDirtyLocationKey(locationKey), new Data(lastTimeStamp));
            putFuture.addListener(new BaseFutureListener<BaseFuture>()
            {
                @Override
                public void operationComplete(BaseFuture future) throws Exception
                {
                    // log.trace("operationComplete");
                }

                @Override
                public void exceptionCaught(Throwable t) throws Exception
                {
                    log.warn("Error at writing dirty flag (timeStamp) " + t.toString());
                }
            });
        } catch (IOException | ClassNotFoundException e)
        {
            log.warn("Error at writing dirty flag (timeStamp) " + e.getMessage());
        }
    }

    private Number160 getDirtyLocationKey(Number160 locationKey)
    {
        return Number160.createHash(locationKey + "Dirty");
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Incoming message handler
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void handleMessage(Object message, PeerAddress peerAddress)
    {
        if (message instanceof TradeMessage)
        {
            log.error("####################");
            Platform.runLater(() -> incomingTradeMessageListeners.stream().forEach(e -> e.onMessage((TradeMessage) message, peerAddress)));
        }
    }
}
