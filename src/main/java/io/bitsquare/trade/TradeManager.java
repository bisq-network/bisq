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

package io.bitsquare.trade;

import io.bitsquare.btc.BlockChainFacade;
import io.bitsquare.btc.WalletFacade;
import io.bitsquare.crypto.CryptoFacade;
import io.bitsquare.msg.MessageFacade;
import io.bitsquare.persistence.Persistence;
import io.bitsquare.settings.Settings;
import io.bitsquare.trade.handlers.ErrorMessageHandler;
import io.bitsquare.trade.handlers.TransactionResultHandler;
import io.bitsquare.trade.protocol.createoffer.CreateOfferCoordinator;
import io.bitsquare.trade.protocol.trade.TradeMessage;
import io.bitsquare.trade.protocol.trade.offerer.BuyerAcceptsOfferProtocol;
import io.bitsquare.trade.protocol.trade.offerer.BuyerAcceptsOfferProtocolListener;
import io.bitsquare.trade.protocol.trade.offerer.messages.BankTransferInitedMessage;
import io.bitsquare.trade.protocol.trade.offerer.messages.DepositTxPublishedMessage;
import io.bitsquare.trade.protocol.trade.offerer.messages.RequestTakerDepositPaymentMessage;
import io.bitsquare.trade.protocol.trade.offerer.messages.RespondToTakeOfferRequestMessage;
import io.bitsquare.trade.protocol.trade.taker.SellerTakesOfferProtocol;
import io.bitsquare.trade.protocol.trade.taker.SellerTakesOfferProtocolListener;
import io.bitsquare.trade.protocol.trade.taker.messages.PayoutTxPublishedMessage;
import io.bitsquare.trade.protocol.trade.taker.messages.RequestOffererPublishDepositTxMessage;
import io.bitsquare.trade.protocol.trade.taker.messages.RequestTakeOfferMessage;
import io.bitsquare.trade.protocol.trade.taker.messages.TakeOfferFeePayedMessage;
import io.bitsquare.user.User;

import com.google.bitcoin.core.Coin;
import com.google.bitcoin.core.Transaction;
import com.google.bitcoin.utils.Fiat;

import java.util.HashMap;
import java.util.Map;

import javax.inject.Inject;

import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableMap;

import net.tomp2p.peers.PeerAddress;

import org.jetbrains.annotations.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The domain for the trading
 * TODO: Too messy, need to be improved a lot....
 */
public class TradeManager {
    private static final Logger log = LoggerFactory.getLogger(TradeManager.class);

    private final User user;
    private final Settings settings;
    private final Persistence persistence;
    private final MessageFacade messageFacade;
    private final BlockChainFacade blockChainFacade;
    private final WalletFacade walletFacade;
    private final CryptoFacade cryptoFacade;

    //TODO store TakerAsSellerProtocol in trade
    private final Map<String, SellerTakesOfferProtocol> takerAsSellerProtocolMap = new HashMap<>();
    private final Map<String, BuyerAcceptsOfferProtocol> offererAsBuyerProtocolMap = new HashMap<>();
    private final Map<String, CreateOfferCoordinator> createOfferCoordinatorMap = new HashMap<>();

    private final ObservableMap<String, Offer> offers = FXCollections.observableHashMap();
    private final ObservableMap<String, Trade> pendingTrades = FXCollections.observableHashMap();
    private final ObservableMap<String, Trade> closedTrades = FXCollections.observableHashMap();

    // the latest pending trade
    private Trade currentPendingTrade;
    final StringProperty featureNotImplementedWarning = new SimpleStringProperty();


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    public TradeManager(User user, Settings settings, Persistence persistence, MessageFacade messageFacade,
                        BlockChainFacade blockChainFacade, WalletFacade walletFacade, CryptoFacade cryptoFacade) {
        this.user = user;
        this.settings = settings;
        this.persistence = persistence;
        this.messageFacade = messageFacade;
        this.blockChainFacade = blockChainFacade;
        this.walletFacade = walletFacade;
        this.cryptoFacade = cryptoFacade;

        Object offersObject = persistence.read(this, "offers");
        if (offersObject instanceof Map) {
            offers.putAll((Map<String, Offer>) offersObject);
        }

        Object pendingTradesObject = persistence.read(this, "pendingTrades");
        if (pendingTradesObject instanceof Map) {
            pendingTrades.putAll((Map<String, Trade>) pendingTradesObject);
        }

        Object closedTradesObject = persistence.read(this, "closedTrades");
        if (closedTradesObject instanceof Map) {
            closedTrades.putAll((Map<String, Trade>) closedTradesObject);
        }

        messageFacade.addIncomingTradeMessageListener(this::onIncomingTradeMessage);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Public Methods
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void cleanup() {
        messageFacade.removeIncomingTradeMessageListener(this::onIncomingTradeMessage);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Manage offers
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void requestPlaceOffer(String id,
                                  Direction direction,
                                  Fiat price,
                                  Coin amount,
                                  Coin minAmount,
                                  TransactionResultHandler resultHandler,
                                  ErrorMessageHandler errorMessageHandler) {

        Offer offer = new Offer(id,
                user.getMessagePublicKey(),
                direction,
                price.getValue(),
                amount,
                minAmount,
                user.getCurrentBankAccount().getBankAccountType(),
                user.getCurrentBankAccount().getCurrency(),
                user.getCurrentBankAccount().getCountry(),
                user.getCurrentBankAccount().getUid(),
                settings.getAcceptedArbitrators(),
                settings.getCollateral(),
                settings.getAcceptedCountries(),
                settings.getAcceptedLanguageLocales());

        if (createOfferCoordinatorMap.containsKey(offer.getId())) {
            errorMessageHandler.onFault("A createOfferCoordinator for the offer with the id " + offer.getId() + " " +
                    "already exists.");
        }
        else {
            CreateOfferCoordinator createOfferCoordinator = new CreateOfferCoordinator(persistence,
                    offer,
                    walletFacade,
                    messageFacade,
                    (transactionId) -> {
                        try {
                            offer.setOfferFeePaymentTxID(transactionId.getHashAsString());
                            addOffer(offer);
                            createOfferCoordinatorMap.remove(offer.getId());

                            resultHandler.onResult(transactionId);
                        } catch (Exception e) {
                            //TODO retry policy
                            errorMessageHandler.onFault("Could not save offer. Reason: " +
                                    (e.getCause() != null ? e.getCause().getMessage() : e.toString()));
                            createOfferCoordinatorMap.remove(offer.getId());
                        }
                    },
                    (message, throwable) -> {
                        errorMessageHandler.onFault(message);
                        createOfferCoordinatorMap.remove(offer.getId());
                    });
            createOfferCoordinatorMap.put(offer.getId(), createOfferCoordinator);
            createOfferCoordinator.start();
        }
    }

    private void addOffer(Offer offer) {
        if (offers.containsKey(offer.getId()))
            log.error("An offer with the id " + offer.getId() + " already exists. ");

        offers.put(offer.getId(), offer);
        persistOffers();
    }

    public void removeOffer(Offer offer) {
        if (!offers.containsKey(offer.getId()))
            log.error("offers does not contain the offer with the ID " + offer.getId());

        offers.remove(offer.getId());
        persistOffers();

        messageFacade.removeOffer(offer);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Manage trades
    ///////////////////////////////////////////////////////////////////////////////////////////

    public Trade createTrade(Offer offer) {
        if (pendingTrades.containsKey(offer.getId()))
            log.error("trades contains already an trade with the ID " + offer.getId());

        Trade trade = new Trade(offer);
        pendingTrades.put(offer.getId(), trade);
        persistPendingTrades();

        return trade;
    }

    public void closeTrade(Trade trade) {
        if (!pendingTrades.containsKey(trade.getId()))
            log.error("trades does not contain the trade with the ID " + trade.getId());

        pendingTrades.remove(trade.getId());
        persistPendingTrades();

        closedTrades.put(trade.getId(), trade);
        persistClosedTrades();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Trading protocols
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void createOffererAsBuyerProtocol(String offerId, PeerAddress sender) {
        log.trace("createOffererAsBuyerProtocol offerId = " + offerId);
        if (offers.containsKey(offerId)) {
            Offer offer = offers.get(offerId);

            Trade trade = createTrade(offer);
            currentPendingTrade = trade;

            BuyerAcceptsOfferProtocol buyerAcceptsOfferProtocol = new BuyerAcceptsOfferProtocol(trade,
                    sender,
                    messageFacade,
                    walletFacade,
                    blockChainFacade,
                    cryptoFacade,
                    user,
                    new BuyerAcceptsOfferProtocolListener() {
                        @Override
                        public void onOfferAccepted(Offer offer) {
                            trade.setState(Trade.State.OFFERER_ACCEPTED);
                            persistPendingTrades();
                            removeOffer(offer);
                        }

                        @Override
                        public void onDepositTxPublished(Transaction depositTx) {
                            trade.setDepositTx(depositTx);
                            trade.setState(Trade.State.DEPOSIT_PUBLISHED);
                            persistPendingTrades();
                            log.trace("trading onDepositTxPublishedMessage " + depositTx.getHashAsString());
                        }

                        @Override
                        public void onDepositTxConfirmedInBlockchain() {
                            log.trace("trading onDepositTxConfirmedInBlockchain");
                            trade.setState(Trade.State.DEPOSIT_CONFIRMED);
                            persistPendingTrades();
                        }

                        @Override
                        public void onPayoutTxPublished(Transaction payoutTx) {
                            trade.setPayoutTx(payoutTx);
                            trade.setState(Trade.State.COMPLETED);
                            closeTrade(trade);

                            log.debug("trading onPayoutTxPublishedMessage");
                        }

                        @Override
                        public void onFault(Throwable throwable, BuyerAcceptsOfferProtocol.State state) {
                            log.error("Error while executing trade process at state: " + state + " / " + throwable);
                            trade.setFault(throwable);
                            trade.setState(Trade.State.FAILED);
                            persistPendingTrades();
                        }

                        // probably not needed
                        @Override
                        public void onWaitingForPeerResponse(BuyerAcceptsOfferProtocol.State state) {
                            log.debug("Waiting for peers response at state " + state);
                        }

                        // probably not needed
                        @Override
                        public void onWaitingForUserInteraction(BuyerAcceptsOfferProtocol.State state) {
                            log.debug("Waiting for UI activity at state " + state);
                        }
                    });

            if (!offererAsBuyerProtocolMap.containsKey(trade.getId())) {
                offererAsBuyerProtocolMap.put(trade.getId(), buyerAcceptsOfferProtocol);
            }
            else {
                // We don't store the protocol in case we have already a pending offer. The protocol is only
                // temporary used to reply with a reject message.
                log.trace("offererAsBuyerProtocol not stored as offer is already pending.");
            }

            buyerAcceptsOfferProtocol.start();
        }
        else {
            log.warn("Incoming offer take request does not match with any saved offer. We ignore that request.");
        }
    }

    public Trade takeOffer(Coin amount, Offer offer) {
        Trade trade = createTrade(offer);
        trade.setTradeAmount(amount);

        currentPendingTrade = trade;
        SellerTakesOfferProtocolListener listener = new SellerTakesOfferProtocolListener() {
            @Override
            public void onTakeOfferRequestAccepted(Trade trade) {
                trade.setState(Trade.State.OFFERER_ACCEPTED);
                persistPendingTrades();
            }

            @Override
            public void onTakeOfferRequestRejected(Trade trade) {
                trade.setState(Trade.State.OFFERER_REJECTED);
                persistPendingTrades();
            }

            @Override
            public void onDepositTxPublished(Transaction depositTx) {
                trade.setDepositTx(depositTx);
                trade.setState(Trade.State.DEPOSIT_PUBLISHED);
                persistPendingTrades();
            }

            @Override
            public void onBankTransferInited(String tradeId) {
                trade.setState(Trade.State.PAYMENT_STARTED);
                persistPendingTrades();
            }

            @Override
            public void onPayoutTxPublished(Trade trade, Transaction payoutTx) {
                trade.setPayoutTx(payoutTx);
                trade.setState(Trade.State.COMPLETED);
                closeTrade(trade);
            }

            @Override
            public void onFault(Throwable throwable, SellerTakesOfferProtocol.State state) {
                log.error("onFault: " + throwable.getMessage() + " / " + state);
            }

            // probably not needed
            @Override
            public void onWaitingForPeerResponse(SellerTakesOfferProtocol.State state) {
                log.error("onWaitingForPeerResponse");
            }

        };

        SellerTakesOfferProtocol sellerTakesOfferProtocol = new SellerTakesOfferProtocol(
                trade, listener, messageFacade, walletFacade, blockChainFacade, cryptoFacade,
                user);
        takerAsSellerProtocolMap.put(trade.getId(), sellerTakesOfferProtocol);
        sellerTakesOfferProtocol.start();

        return trade;
    }

    //TODO we don't support interruptions yet. 
    // If the user has shut down the app we lose the offererAsBuyerProtocolMap
    // Also we don't support yet offline messaging (mail box)
    public void fiatPaymentStarted(String tradeId) {
        if (offererAsBuyerProtocolMap.get(tradeId) != null) {
            offererAsBuyerProtocolMap.get(tradeId).onUIEventBankTransferInited();
            pendingTrades.get(tradeId).setState(Trade.State.PAYMENT_STARTED);
            persistPendingTrades();
        }
        else {
            featureNotImplementedWarning.set("Sorry, you cannot continue. You have restarted the application in the " +
                    "meantime. Interruption of the trade process is not supported yet. Will need more time to be " +
                    "implemented.");
        }
    }

    public void fiatPaymentReceived(String tradeId) {
        takerAsSellerProtocolMap.get(tradeId).onUIEventFiatReceived();
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Process incoming tradeMessages
    ///////////////////////////////////////////////////////////////////////////////////////////

    // Routes the incoming messages to the responsible protocol
    private void onIncomingTradeMessage(TradeMessage tradeMessage, PeerAddress sender) {
        // log.trace("processTradingMessage TradeId " + tradeMessage.getTradeId());
        log.trace("processTradingMessage instance " + tradeMessage.getClass().getSimpleName());

        String tradeId = tradeMessage.getTradeId();

        if (tradeMessage instanceof RequestTakeOfferMessage) {
            createOffererAsBuyerProtocol(tradeId, sender);
        }
        else if (tradeMessage instanceof RespondToTakeOfferRequestMessage) {
            takerAsSellerProtocolMap.get(tradeId).onRespondToTakeOfferRequestMessage(
                    (RespondToTakeOfferRequestMessage) tradeMessage);
        }
        else if (tradeMessage instanceof TakeOfferFeePayedMessage) {
            offererAsBuyerProtocolMap.get(tradeId).onTakeOfferFeePayedMessage((TakeOfferFeePayedMessage) tradeMessage);
        }
        else if (tradeMessage instanceof RequestTakerDepositPaymentMessage) {
            takerAsSellerProtocolMap.get(tradeId).onRequestTakerDepositPaymentMessage(
                    (RequestTakerDepositPaymentMessage) tradeMessage);
        }
        else if (tradeMessage instanceof RequestOffererPublishDepositTxMessage) {
            offererAsBuyerProtocolMap.get(tradeId).onRequestOffererPublishDepositTxMessage(
                    (RequestOffererPublishDepositTxMessage) tradeMessage);
        }
        else if (tradeMessage instanceof DepositTxPublishedMessage) {
            persistPendingTrades();
            takerAsSellerProtocolMap.get(tradeId).onDepositTxPublishedMessage((DepositTxPublishedMessage) tradeMessage);
        }
        else if (tradeMessage instanceof BankTransferInitedMessage) {
            takerAsSellerProtocolMap.get(tradeId).onBankTransferInitedMessage((BankTransferInitedMessage) tradeMessage);
        }
        else if (tradeMessage instanceof PayoutTxPublishedMessage) {
            offererAsBuyerProtocolMap.get(tradeId).onPayoutTxPublishedMessage((PayoutTxPublishedMessage) tradeMessage);
        }
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Utils
    ///////////////////////////////////////////////////////////////////////////////////////////

    public boolean isOfferAlreadyInTrades(Offer offer) {
        return pendingTrades.containsKey(offer.getId());
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Setters
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void setFeatureNotImplementedWarning(String featureNotImplementedWarning) {
        this.featureNotImplementedWarning.set(featureNotImplementedWarning);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Getters
    ///////////////////////////////////////////////////////////////////////////////////////////

    public ObservableMap<String, Offer> getOffers() {
        return offers;
    }

    public ObservableMap<String, Trade> getPendingTrades() {
        return pendingTrades;
    }

    public ObservableMap<String, Trade> getClosedTrades() {
        return closedTrades;
    }

    public Trade getCurrentPendingTrade() {
        return currentPendingTrade;
    }

    public String getFeatureNotImplementedWarning() {
        return featureNotImplementedWarning.get();
    }

    public StringProperty featureNotImplementedWarningProperty() {
        return featureNotImplementedWarning;
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Private
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void persistOffers() {
        persistence.write(this, "offers", (Map<String, Offer>) new HashMap<>(offers));
    }

    private void persistPendingTrades() {
        persistence.write(this, "pendingTrades", (Map<String, Trade>) new HashMap<>(pendingTrades));
    }

    private void persistClosedTrades() {
        persistence.write(this, "closedTrades", (Map<String, Trade>) new HashMap<>(closedTrades));
    }


    @Nullable
    public Trade getTrade(String tradeId) {
        if (pendingTrades.containsKey(tradeId)) {
            return pendingTrades.get(tradeId);
        }
        else {
            return null;
        }
    }


}
