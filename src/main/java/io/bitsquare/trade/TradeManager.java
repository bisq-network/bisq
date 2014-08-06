package io.bitsquare.trade;

import com.google.bitcoin.core.Coin;
import com.google.bitcoin.core.Transaction;
import com.google.bitcoin.core.TransactionConfidence;
import com.google.bitcoin.core.Utils;
import io.bitsquare.btc.BlockChainFacade;
import io.bitsquare.btc.WalletFacade;
import io.bitsquare.crypto.CryptoFacade;
import io.bitsquare.gui.popups.Popups;
import io.bitsquare.msg.MessageFacade;
import io.bitsquare.msg.listeners.TakeOfferRequestListener;
import io.bitsquare.storage.Persistence;
import io.bitsquare.trade.handlers.ErrorMessageHandler;
import io.bitsquare.trade.handlers.PublishTransactionResultHandler;
import io.bitsquare.trade.protocol.TradeMessage;
import io.bitsquare.trade.protocol.createoffer.CreateOfferCoordinator;
import io.bitsquare.trade.protocol.offerer.*;
import io.bitsquare.trade.protocol.taker.*;
import io.bitsquare.user.User;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javax.inject.Inject;
import net.tomp2p.peers.PeerAddress;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TradeManager
{
    private static final Logger log = LoggerFactory.getLogger(TradeManager.class);

    private final User user;
    private final Persistence persistence;
    private final MessageFacade messageFacade;
    private final BlockChainFacade blockChainFacade;
    private final WalletFacade walletFacade;
    private final CryptoFacade cryptoFacade;

    private final List<TakeOfferRequestListener> takeOfferRequestListeners = new ArrayList<>();

    //TODO store TakerAsSellerProtocol in trade
    private final Map<String, ProtocolForTakerAsSeller> takerAsSellerProtocolMap = new HashMap<>();
    private final Map<String, ProtocolForOffererAsBuyer> offererAsBuyerProtocolMap = new HashMap<>();
    private final Map<String, CreateOfferCoordinator> createOfferCoordinatorMap = new HashMap<>();

    private final StringProperty newTradeProperty = new SimpleStringProperty();

    private final Map<String, Offer> offers;
    private final Map<String, Trade> trades;

    private Trade pendingTrade;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    public TradeManager(User user, Persistence persistence, MessageFacade messageFacade, BlockChainFacade blockChainFacade, WalletFacade walletFacade, CryptoFacade cryptoFacade)
    {
        this.user = user;
        this.persistence = persistence;
        this.messageFacade = messageFacade;
        this.blockChainFacade = blockChainFacade;
        this.walletFacade = walletFacade;
        this.cryptoFacade = cryptoFacade;

        Object offersObject = persistence.read(this, "offers");
        if (offersObject instanceof HashMap)
        {
            offers = (Map<String, Offer>) offersObject;
        }
        else
        {
            offers = new HashMap<>();
        }

        Object tradesObject = persistence.read(this, "trades");
        if (tradesObject instanceof HashMap)
        {
            trades = (Map<String, Trade>) tradesObject;
        }
        else
        {
            trades = new HashMap<>();
        }

        messageFacade.addIncomingTradeMessageListener(this::onIncomingTradeMessage);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Public Methods
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void cleanup()
    {
        messageFacade.removeIncomingTradeMessageListener(this::onIncomingTradeMessage);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Event Listeners
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void addTakeOfferRequestListener(TakeOfferRequestListener listener)
    {
        takeOfferRequestListeners.add(listener);
    }

    public void removeTakeOfferRequestListener(TakeOfferRequestListener listener)
    {
        takeOfferRequestListeners.remove(listener);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Manage offers
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void requestPlaceOffer(Offer offer, PublishTransactionResultHandler resultHandler, ErrorMessageHandler errorMessageHandler)
    {
        if (createOfferCoordinatorMap.containsKey(offer.getId()))
        {
            errorMessageHandler.onFault("A createOfferCoordinator for the offer with the id " + offer.getId() + " already exists.");
        }
        else
        {
            CreateOfferCoordinator createOfferCoordinator = new CreateOfferCoordinator(offer, walletFacade, messageFacade);
            createOfferCoordinatorMap.put(offer.getId(), createOfferCoordinator);
            createOfferCoordinator.start(
                    (transactionId) -> {
                        try
                        {
                            addOffer(offer);
                            offer.setOfferFeePaymentTxID(transactionId);
                            createOfferCoordinatorMap.remove(offer.getId());

                            resultHandler.onResult(transactionId);
                        } catch (Exception e)
                        {
                            //TODO retry policy
                            errorMessageHandler.onFault("Could not save offer. Reason: " + e.getMessage());
                            createOfferCoordinatorMap.remove(offer.getId());
                        }
                    },
                    (message, throwable) -> {
                        errorMessageHandler.onFault(message);
                        createOfferCoordinatorMap.remove(offer.getId());
                    });
        }
    }

    private void addOffer(Offer offer) throws IOException
    {
        if (offers.containsKey(offer.getId()))
            throw new IllegalStateException("An offer with the id " + offer.getId() + " already exists. ");

        offers.put(offer.getId(), offer);
        persistOffers();
    }

    public void removeOffer(Offer offer)
    {     //TODO
       /* if (!offers.containsKey(offer.getId()))
        {
            throw new IllegalStateException("offers does not contain the offer with the ID " + offer.getId());
        }*/

        offers.remove(offer.getId());
        persistOffers();

        messageFacade.removeOffer(offer);
    }

    public Trade takeOffer(Coin amount, Offer offer, ProtocolForTakerAsSellerListener listener)
    {
        Trade trade = createTrade(offer);
        trade.setTradeAmount(amount);

        ProtocolForTakerAsSeller protocolForTakerAsSeller = new ProtocolForTakerAsSeller(trade, listener, messageFacade, walletFacade, blockChainFacade, cryptoFacade, user);
        takerAsSellerProtocolMap.put(trade.getId(), protocolForTakerAsSeller);
        protocolForTakerAsSeller.start();

        return trade;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Manage trades
    ///////////////////////////////////////////////////////////////////////////////////////////

    public Trade createTrade(Offer offer)
    {
        if (trades.containsKey(offer.getId()))
        {
            throw new IllegalStateException("trades contains already an trade with the ID " + offer.getId());
        }

        Trade trade = new Trade(offer);
        trades.put(offer.getId(), trade);
        saveTrades();

        // for updating UIs
        this.newTradeProperty.set(trade.getId());

        return trade;
    }

    public void removeTrade(Trade trade)
    {
        if (!trades.containsKey(trade.getId()))
        {
            throw new IllegalStateException("trades does not contain the trade with the ID " + trade.getId());
        }

        trades.remove(trade.getId());
        saveTrades();

        // for updating UIs
        this.newTradeProperty.set(null);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Trading protocols
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void createOffererAsBuyerProtocol(String offerId, PeerAddress sender)
    {
        log.trace("createOffererAsBuyerProtocol offerId = " + offerId);
        if (offers.containsKey(offerId))
        {
            Offer offer = offers.get(offerId);

            Trade trade = createTrade(offer);
            pendingTrade = trade;

            ProtocolForOffererAsBuyer protocolForOffererAsBuyer = new ProtocolForOffererAsBuyer(trade,
                                                                                                sender,
                                                                                                messageFacade,
                                                                                                walletFacade,
                                                                                                blockChainFacade,
                                                                                                cryptoFacade,
                                                                                                user,
                                                                                                new ProtocolForOffererAsBuyerListener()
                                                                                                {
                                                                                                    @Override
                                                                                                    public void onOfferAccepted(Offer offer)
                                                                                                    {
                                                                                                        removeOffer(offer);
                                                                                                    }

                                                                                                    @Override
                                                                                                    public void onDepositTxPublished(String depositTxID)
                                                                                                    {
                                                                                                        log.trace("trading onDepositTxPublishedMessage " + depositTxID);
                                                                                                    }

                                                                                                    @Override
                                                                                                    public void onDepositTxConfirmedUpdate(TransactionConfidence confidence)
                                                                                                    {
                                                                                                        log.trace("trading onDepositTxConfirmedUpdate");
                                                                                                    }

                                                                                                    @Override
                                                                                                    public void onPayoutTxPublished(String payoutTxAsHex)
                                                                                                    {
                                                                                                        Transaction payoutTx = new Transaction(walletFacade.getWallet().getParams(),
                                                                                                                                               Utils.parseAsHexOrBase58(payoutTxAsHex));
                                                                                                        trade.setPayoutTransaction(payoutTx);
                                                                                                        trade.setState(Trade.State.COMPLETED);
                                                                                                        log.debug("trading onPayoutTxPublishedMessage");
                                                                                                    }

                                                                                                    @Override
                                                                                                    public void onFault(Throwable throwable, ProtocolForOffererAsBuyer.State state)
                                                                                                    {
                                                                                                        log.error("Error while executing trade process at state: " + state + " / " + throwable);
                                                                                                        Popups.openErrorPopup("Error while executing trade process",
                                                                                                                              "Error while executing trade process at state: " + state + " / " +
                                                                                                                                      throwable);
                                                                                                    }

                                                                                                    @Override
                                                                                                    public void onWaitingForPeerResponse(ProtocolForOffererAsBuyer.State state)
                                                                                                    {
                                                                                                        log.debug("Waiting for peers response at state " + state);
                                                                                                    }

                                                                                                    @Override
                                                                                                    public void onCompleted(ProtocolForOffererAsBuyer.State state)
                                                                                                    {
                                                                                                        log.debug("Trade protocol completed at state " + state);
                                                                                                    }

                                                                                                    @Override
                                                                                                    public void onWaitingForUserInteraction(ProtocolForOffererAsBuyer.State state)
                                                                                                    {
                                                                                                        log.debug("Waiting for UI activity at state " + state);
                                                                                                    }


                                                                                                    @Override
                                                                                                    public void onDepositTxConfirmedInBlockchain()
                                                                                                    {
                                                                                                        log.trace("trading onDepositTxConfirmedInBlockchain");
                                                                                                    }

                                                                                                });

            if (!offererAsBuyerProtocolMap.containsKey(trade.getId()))
            {
                offererAsBuyerProtocolMap.put(trade.getId(), protocolForOffererAsBuyer);
            }
            else
            {
                // We don't store the protocol in case we have already a pending offer. The protocol is only temporary used to reply with a reject message.
                log.trace("offererAsBuyerProtocol not stored as offer is already pending.");
            }

            protocolForOffererAsBuyer.start();
        }
        else
        {
            log.warn("Incoming offer take request does not match with any saved offer. We ignore that request.");
        }
    }

    public void bankTransferInited(String tradeUID)
    {
        offererAsBuyerProtocolMap.get(tradeUID).onUIEventBankTransferInited();
    }

    public void onFiatReceived(String tradeUID)
    {
        takerAsSellerProtocolMap.get(tradeUID).onUIEventFiatReceived();
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Process incoming tradeMessages
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void onIncomingTradeMessage(TradeMessage tradeMessage, PeerAddress sender)
    {
        // log.trace("processTradingMessage TradeId " + tradeMessage.getTradeId());
        log.trace("processTradingMessage instance " + tradeMessage.getClass().getSimpleName());

        String tradeId = tradeMessage.getTradeId();

        if (tradeMessage instanceof RequestTakeOfferMessage)
        {
            createOffererAsBuyerProtocol(tradeId, sender);
            takeOfferRequestListeners.stream().forEach(e -> e.onTakeOfferRequested(tradeId, sender));
        }
        else if (tradeMessage instanceof RespondToTakeOfferRequestMessage)
        {
            takerAsSellerProtocolMap.get(tradeId).onRespondToTakeOfferRequestMessage((RespondToTakeOfferRequestMessage) tradeMessage);
        }
        else if (tradeMessage instanceof TakeOfferFeePayedMessage)
        {
            offererAsBuyerProtocolMap.get(tradeId).onTakeOfferFeePayedMessage((TakeOfferFeePayedMessage) tradeMessage);
        }
        else if (tradeMessage instanceof RequestTakerDepositPaymentMessage)
        {
            takerAsSellerProtocolMap.get(tradeId).onRequestTakerDepositPaymentMessage((RequestTakerDepositPaymentMessage) tradeMessage);
        }
        else if (tradeMessage instanceof RequestOffererPublishDepositTxMessage)
        {
            offererAsBuyerProtocolMap.get(tradeId).onRequestOffererPublishDepositTxMessage((RequestOffererPublishDepositTxMessage) tradeMessage);
        }
        else if (tradeMessage instanceof DepositTxPublishedMessage)
        {
            takerAsSellerProtocolMap.get(tradeId).onDepositTxPublishedMessage((DepositTxPublishedMessage) tradeMessage);
        }
        else if (tradeMessage instanceof BankTransferInitedMessage)
        {
            takerAsSellerProtocolMap.get(tradeId).onBankTransferInitedMessage((BankTransferInitedMessage) tradeMessage);
        }
        else if (tradeMessage instanceof PayoutTxPublishedMessage)
        {
            offererAsBuyerProtocolMap.get(tradeId).onPayoutTxPublishedMessage((PayoutTxPublishedMessage) tradeMessage);
        }
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Utils
    ///////////////////////////////////////////////////////////////////////////////////////////

    public boolean isOfferAlreadyInTrades(Offer offer)
    {
        return trades.containsKey(offer.getId());
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Getters
    ///////////////////////////////////////////////////////////////////////////////////////////

    public Map<String, Trade> getTrades()
    {
        return trades;
    }

    public Map<String, Offer> getOffers()
    {
        return offers;
    }

    public Offer getOffer(String offerId)
    {
        return offers.get(offerId);
    }

    public Trade getPendingTrade()
    {
        return pendingTrade;
    }

    public final StringProperty getNewTradeProperty()
    {
        return this.newTradeProperty;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Private
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void persistOffers()
    {
        persistence.write(this, "offers", offers);
    }

    private void saveTrades()
    {
        persistence.write(this, "trades", trades);
    }

    @Nullable
    public Trade getTrade(String tradeId)
    {
        if (trades.containsKey(tradeId))
        {
            return trades.get(trades);
        }
        else
        {
            return null;
        }
    }


}
