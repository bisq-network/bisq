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

import io.bitsquare.account.AccountSettings;
import io.bitsquare.bank.BankAccount;
import io.bitsquare.btc.BlockChainService;
import io.bitsquare.btc.WalletService;
import io.bitsquare.crypto.SignatureService;
import io.bitsquare.msg.Message;
import io.bitsquare.msg.MessageService;
import io.bitsquare.msg.listeners.OutgoingMessageListener;
import io.bitsquare.network.Peer;
import io.bitsquare.offer.Direction;
import io.bitsquare.offer.Offer;
import io.bitsquare.offer.RemoteOfferBook;
import io.bitsquare.persistence.Persistence;
import io.bitsquare.trade.handlers.TransactionResultHandler;
import io.bitsquare.trade.protocol.placeoffer.PlaceOfferProtocol;
import io.bitsquare.trade.protocol.trade.TradeMessage;
import io.bitsquare.trade.protocol.trade.offerer.BuyerAcceptsOfferProtocol;
import io.bitsquare.trade.protocol.trade.offerer.BuyerAcceptsOfferProtocolListener;
import io.bitsquare.trade.protocol.trade.offerer.messages.BankTransferInitedMessage;
import io.bitsquare.trade.protocol.trade.offerer.messages.DepositTxPublishedMessage;
import io.bitsquare.trade.protocol.trade.offerer.messages.RequestTakerDepositPaymentMessage;
import io.bitsquare.trade.protocol.trade.offerer.messages.RespondToIsOfferAvailableMessage;
import io.bitsquare.trade.protocol.trade.offerer.messages.RespondToTakeOfferRequestMessage;
import io.bitsquare.trade.protocol.trade.taker.SellerTakesOfferProtocol;
import io.bitsquare.trade.protocol.trade.taker.SellerTakesOfferProtocolListener;
import io.bitsquare.trade.protocol.trade.taker.messages.PayoutTxPublishedMessage;
import io.bitsquare.trade.protocol.trade.taker.messages.RequestIsOfferAvailableMessage;
import io.bitsquare.trade.protocol.trade.taker.messages.RequestOffererPublishDepositTxMessage;
import io.bitsquare.trade.protocol.trade.taker.messages.RequestTakeOfferMessage;
import io.bitsquare.trade.protocol.trade.taker.messages.TakeOfferFeePayedMessage;
import io.bitsquare.user.User;
import io.bitsquare.util.task.ErrorMessageHandler;
import io.bitsquare.util.task.FaultHandler;
import io.bitsquare.util.task.ResultHandler;

import org.bitcoinj.core.Coin;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.utils.Fiat;

import java.util.HashMap;
import java.util.Map;

import javax.inject.Inject;

import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableMap;

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
    private final AccountSettings accountSettings;
    private final Persistence persistence;
    private final MessageService messageService;
    private final BlockChainService blockChainService;
    private final WalletService walletService;
    private final SignatureService signatureService;
    private final RemoteOfferBook remoteOfferBook;

    //TODO store TakerAsSellerProtocol in trade
    private final Map<String, SellerTakesOfferProtocol> takerAsSellerProtocolMap = new HashMap<>();
    private final Map<String, BuyerAcceptsOfferProtocol> offererAsBuyerProtocolMap = new HashMap<>();

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
    public TradeManager(User user, AccountSettings accountSettings, Persistence persistence,
                        MessageService messageService, BlockChainService blockChainService,
                        WalletService walletService, SignatureService signatureService,
                        RemoteOfferBook remoteOfferBook) {
        this.user = user;
        this.accountSettings = accountSettings;
        this.persistence = persistence;
        this.messageService = messageService;
        this.blockChainService = blockChainService;
        this.walletService = walletService;
        this.signatureService = signatureService;
        this.remoteOfferBook = remoteOfferBook;

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

        messageService.addIncomingMessageListener(this::onIncomingTradeMessage);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Public Methods
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void cleanup() {
        messageService.removeIncomingMessageListener(this::onIncomingTradeMessage);
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

        BankAccount currentBankAccount = user.getCurrentBankAccount().get();
        Offer offer = new Offer(id,
                user.getMessagePublicKey(),
                direction,
                price.getValue(),
                amount,
                minAmount,
                currentBankAccount.getBankAccountType(),
                currentBankAccount.getCurrency(),
                currentBankAccount.getCountry(),
                currentBankAccount.getUid(),
                accountSettings.getAcceptedArbitrators(),
                accountSettings.getSecurityDeposit(),
                accountSettings.getAcceptedCountries(),
                accountSettings.getAcceptedLanguageLocales());

        PlaceOfferProtocol placeOfferProtocol = new PlaceOfferProtocol(
                offer,
                walletService,
                remoteOfferBook,
                (transaction) -> {
                    saveOffer(offer);
                    resultHandler.onResult(transaction);
                },
                (message, throwable) -> errorMessageHandler.handleErrorMessage(message)
        );

        placeOfferProtocol.placeOffer();
    }

    private void saveOffer(Offer offer) {
        offers.put(offer.getId(), offer);
        persistOffers();
    }

    public void requestRemoveOffer(Offer offer, ResultHandler resultHandler, FaultHandler faultHandler) {
        if (offers.containsKey(offer.getId()))
            offers.remove(offer.getId());
        else
            log.error("offers does not contain the offer with the ID " + offer.getId());

        persistOffers();

        remoteOfferBook.removeOffer(offer, resultHandler, faultHandler);
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

    private void createOffererAsBuyerProtocol(String offerId, Peer sender) {
        log.trace("createOffererAsBuyerProtocol offerId = " + offerId);
        if (offers.containsKey(offerId)) {
            Offer offer = offers.get(offerId);

            Trade trade = createTrade(offer);
            currentPendingTrade = trade;

            BuyerAcceptsOfferProtocol buyerAcceptsOfferProtocol = new BuyerAcceptsOfferProtocol(trade,
                    sender,
                    messageService,
                    walletService,
                    blockChainService,
                    signatureService,
                    user,
                    new BuyerAcceptsOfferProtocolListener() {
                        @Override
                        public void onOfferAccepted(Offer offer) {
                            trade.setState(Trade.State.OFFERER_ACCEPTED);
                            persistPendingTrades();
                            requestRemoveOffer(offer,
                                    () -> log.debug("remove was successful"),
                                    (message, throwable) -> log.error(message));
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
                            // We close the trade when the user has withdrawn his trade funds (see #283)
                            //closeTrade(trade);

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
                // We don't store the protocol in case we have already an open offer. The protocol is only
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
                // We close the trade when the user has withdrawn his trade funds (see #283)
                //closeTrade(trade);
            }

            @Override
            public void onFault(Throwable throwable, SellerTakesOfferProtocol.State state) {
                log.error("onFault: " + throwable.getMessage() + " / " + state);
            }

            // probably not needed
            @Override
            public void onWaitingForPeerResponse(SellerTakesOfferProtocol.State state) {
                log.debug("onWaitingForPeerResponse");
            }

        };

        SellerTakesOfferProtocol sellerTakesOfferProtocol = new SellerTakesOfferProtocol(
                trade, listener, messageService, walletService, blockChainService, signatureService,
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
    private void onIncomingTradeMessage(Message message, Peer sender) {
        if (!(message instanceof TradeMessage))
            throw new IllegalArgumentException("message must be of type TradeMessage");
        TradeMessage tradeMessage = (TradeMessage) message;

        log.trace("onIncomingTradeMessage instance " + tradeMessage.getClass().getSimpleName());
        log.trace("onIncomingTradeMessage sender " + sender);

        String tradeId = tradeMessage.getTradeId();
        if (tradeId != null) {
            if (tradeMessage instanceof RequestIsOfferAvailableMessage) {
                // TODO Does not fit in any of the 2 protocols, but should not be here as well...
                // Lets keep it until we refactor the trade process
                boolean isOfferOpen = getTrade(tradeId) == null;
                RespondToIsOfferAvailableMessage replyMessage =
                        new RespondToIsOfferAvailableMessage(tradeId, isOfferOpen);
                messageService.sendMessage(sender, replyMessage, new OutgoingMessageListener() {
                    @Override
                    public void onResult() {
                        log.trace("RespondToTakeOfferRequestMessage successfully arrived at peer");
                    }

                    @Override
                    public void onFailed() {
                        log.error("AcceptTakeOfferRequestMessage  did not arrive at peer");
                    }
                });
            }
            else if (tradeMessage instanceof RequestTakeOfferMessage) {
                createOffererAsBuyerProtocol(tradeId, sender);
            }
            else if (tradeMessage instanceof RespondToTakeOfferRequestMessage) {
                takerAsSellerProtocolMap.get(tradeId).onRespondToTakeOfferRequestMessage(
                        (RespondToTakeOfferRequestMessage) tradeMessage);
            }
            else if (tradeMessage instanceof TakeOfferFeePayedMessage) {
                offererAsBuyerProtocolMap.get(tradeId).onTakeOfferFeePayedMessage((TakeOfferFeePayedMessage)
                        tradeMessage);
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
                takerAsSellerProtocolMap.get(tradeId).onDepositTxPublishedMessage((DepositTxPublishedMessage)
                        tradeMessage);
            }
            else if (tradeMessage instanceof BankTransferInitedMessage) {
                // Here happened a null pointer. I assume the only possible reason was that we got a null for the 
                // tradeID
                // as the takerAsSellerProtocolMap need to have that trade got added earlier.
                // For getting better info we add a check. tradeId is checked above.
                if (takerAsSellerProtocolMap.get(tradeId) == null)
                    log.error("takerAsSellerProtocolMap.get(tradeId) = null. That must not happen.");
                takerAsSellerProtocolMap.get(tradeId).onBankTransferInitedMessage((BankTransferInitedMessage)
                        tradeMessage);
            }
            else if (tradeMessage instanceof PayoutTxPublishedMessage) {
                offererAsBuyerProtocolMap.get(tradeId).onPayoutTxPublishedMessage((PayoutTxPublishedMessage)
                        tradeMessage);
            }
        }
        else {
            log.error("tradeId from onIncomingTradeMessage is null. That must not happen.");
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
