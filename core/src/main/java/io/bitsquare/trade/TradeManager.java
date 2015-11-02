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

import com.google.common.util.concurrent.FutureCallback;
import io.bitsquare.arbitration.ArbitratorManager;
import io.bitsquare.btc.AddressEntry;
import io.bitsquare.btc.TradeWalletService;
import io.bitsquare.btc.WalletService;
import io.bitsquare.common.crypto.KeyRing;
import io.bitsquare.common.handlers.FaultHandler;
import io.bitsquare.common.handlers.ResultHandler;
import io.bitsquare.p2p.Address;
import io.bitsquare.p2p.Message;
import io.bitsquare.p2p.P2PService;
import io.bitsquare.p2p.P2PServiceListener;
import io.bitsquare.p2p.messaging.DecryptedMailListener;
import io.bitsquare.p2p.messaging.DecryptedMailboxListener;
import io.bitsquare.p2p.messaging.DecryptedMsgWithPubKey;
import io.bitsquare.storage.Storage;
import io.bitsquare.trade.closed.ClosedTradableManager;
import io.bitsquare.trade.failed.FailedTradesManager;
import io.bitsquare.trade.handlers.TradeResultHandler;
import io.bitsquare.trade.offer.Offer;
import io.bitsquare.trade.offer.OpenOffer;
import io.bitsquare.trade.offer.OpenOfferManager;
import io.bitsquare.trade.protocol.availability.OfferAvailabilityModel;
import io.bitsquare.trade.protocol.trade.messages.PayDepositRequest;
import io.bitsquare.trade.protocol.trade.messages.TradeMessage;
import io.bitsquare.user.User;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.collections.ObservableList;
import org.bitcoinj.core.AddressFormatException;
import org.bitcoinj.core.Coin;
import org.bitcoinj.core.InsufficientMoneyException;
import org.bitcoinj.core.Transaction;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongycastle.crypto.params.KeyParameter;

import javax.inject.Inject;
import javax.inject.Named;
import java.io.File;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Optional;

import static io.bitsquare.util.Validator.nonEmptyStringOf;

public class TradeManager {
    private static final Logger log = LoggerFactory.getLogger(TradeManager.class);

    private final User user;
    private final KeyRing keyRing;
    private final WalletService walletService;
    private final TradeWalletService tradeWalletService;
    private final OpenOfferManager openOfferManager;
    private final ClosedTradableManager closedTradableManager;
    private final FailedTradesManager failedTradesManager;
    private final ArbitratorManager arbitratorManager;
    private final P2PService p2PService;

    private final Storage<TradableList<Trade>> tradableListStorage;
    private final TradableList<Trade> trades;
    private final BooleanProperty pendingTradesInitialized = new SimpleBooleanProperty();
    private P2PServiceListener p2PServiceListener;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    public TradeManager(User user,
                        KeyRing keyRing,
                        WalletService walletService,
                        TradeWalletService tradeWalletService,
                        OpenOfferManager openOfferManager,
                        ClosedTradableManager closedTradableManager,
                        FailedTradesManager failedTradesManager,
                        ArbitratorManager arbitratorManager,
                        P2PService p2PService,
                        @Named("storage.dir") File storageDir) {
        this.user = user;
        this.keyRing = keyRing;
        this.walletService = walletService;
        this.tradeWalletService = tradeWalletService;
        this.openOfferManager = openOfferManager;
        this.closedTradableManager = closedTradableManager;
        this.failedTradesManager = failedTradesManager;
        this.arbitratorManager = arbitratorManager;
        this.p2PService = p2PService;

        tradableListStorage = new Storage<>(storageDir);
        this.trades = new TradableList<>(tradableListStorage, "PendingTrades");

        p2PService.addDecryptedMailListener(new DecryptedMailListener() {
            @Override
            public void onMailMessage(DecryptedMsgWithPubKey decryptedMsgWithPubKey, Address peerAddress) {
                Message message = decryptedMsgWithPubKey.message;

                // Handler for incoming initial messages from taker
                if (message instanceof PayDepositRequest) {
                    log.trace("Received PayDepositRequest: " + message);
                    handleInitialTakeOfferRequest((PayDepositRequest) message, peerAddress);
                }
            }
        });
        p2PService.addDecryptedMailboxListener(new DecryptedMailboxListener() {
            @Override
            public void onMailboxMessageAdded(DecryptedMsgWithPubKey decryptedMsgWithPubKey, Address senderAddress) {
                log.trace("onMailboxMessageAdded decryptedMessageWithPubKey: " + decryptedMsgWithPubKey);
                log.trace("onMailboxMessageAdded senderAddress: " + senderAddress);
                Message message = decryptedMsgWithPubKey.message;
                if (message instanceof PayDepositRequest) {
                    //TODO is that used????
                    PayDepositRequest payDepositRequest = (PayDepositRequest) message;
                    log.trace("Received payDepositRequest: " + payDepositRequest);
                    if (payDepositRequest.getSenderAddress().equals(senderAddress))
                        handleInitialTakeOfferRequest(payDepositRequest, senderAddress);
                    else
                        log.warn("Peer address not matching for payDepositRequest");
                } else if (message instanceof TradeMessage) {
                    log.trace("Received TradeMessage: " + message);
                    String tradeId = ((TradeMessage) message).tradeId;
                    Optional<Trade> tradeOptional = trades.stream().filter(e -> e.getId().equals(tradeId)).findAny();
                    if (tradeOptional.isPresent())
                        tradeOptional.get().setMailboxMessage(decryptedMsgWithPubKey);
                }
            }
        });

        p2PServiceListener = new P2PServiceListener() {
            @Override
            public void onTorNodeReady() {
            }

            @Override
            public void onHiddenServiceReady() {
            }

            @Override
            public void onSetupFailed(Throwable throwable) {
            }

            @Override
            public void onAllDataReceived() {
            }

            @Override
            public void onAuthenticated() {
                initPendingTrades();
            }
        };
        p2PService.addP2PServiceListener(p2PServiceListener);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Lifecycle
    ///////////////////////////////////////////////////////////////////////////////////////////

    // When all services are initialized we create the protocols for our open offers and persisted pendingTrades
    // OffererAsBuyerProtocol listens for take offer requests, so we need to instantiate it early.
    public void onAllServicesInitialized() {
        log.trace("onAllServicesInitialized");
    }

    private void initPendingTrades() {
        if (p2PServiceListener != null) p2PService.removeP2PServiceListener(p2PServiceListener);

        List<Trade> failedTrades = new ArrayList<>();
        for (Trade trade : trades) {
            // We continue an interrupted trade.
            // TODO if the peer has changed its IP address, we need to make another findPeer request. At the moment we use the peer stored in trade to
            // continue the trade, but that might fail.

            // TODO
           /* if (trade.isFailedState()) {
                failedTrades.add(trade);
            }
            else {*/
            trade.setStorage(tradableListStorage);
            trade.updateDepositTxFromWallet(tradeWalletService);
            initTrade(trade);

            // after we are authenticated we remove mailbox messages. 
            DecryptedMsgWithPubKey mailboxMessage = trade.getMailboxMessage();
            if (mailboxMessage != null) {
                p2PService.removeEntryFromMailbox(mailboxMessage);
                trade.setMailboxMessage(null);
            }
            // }
        }
        pendingTradesInitialized.set(true);

        failedTrades.stream().filter(Trade::isTakerFeePaid).forEach(this::addTradeToFailedTrades);
    }

    private void handleInitialTakeOfferRequest(TradeMessage message, Address peerAddress) {
        log.trace("handleNewMessage: message = " + message.getClass().getSimpleName() + " from " + peerAddress);
        try {
            nonEmptyStringOf(message.tradeId);
        } catch (Throwable t) {
            log.warn("Invalid requestDepositTxInputsMessage " + message.toString());
            return;
        }

        Optional<OpenOffer> openOfferOptional = openOfferManager.findOpenOffer(message.tradeId);
        if (openOfferOptional.isPresent() && openOfferOptional.get().getState() == OpenOffer.State.AVAILABLE) {
            Offer offer = openOfferOptional.get().getOffer();
            openOfferManager.reserveOpenOffer(openOfferOptional.get());

            Trade trade;
            if (offer.getDirection() == Offer.Direction.BUY)
                trade = new BuyerAsOffererTrade(offer, tradableListStorage);
            else
                trade = new SellerAsOffererTrade(offer, tradableListStorage);

            trade.setStorage(tradableListStorage);
            initTrade(trade);
            trades.add(trade);
            ((OffererTrade) trade).handleTakeOfferRequest(message, peerAddress);
        } else {
            // TODO respond
            //(RequestDepositTxInputsMessage)message.
            //  messageService.sendEncryptedMessage(peerAddress,messageWithPubKey.getMessage().);
            log.info("We received a take offer request but don't have that offer anymore.");
        }
    }

    private void initTrade(Trade trade) {
        trade.init(p2PService,
                walletService,
                tradeWalletService,
                arbitratorManager,
                this,
                openOfferManager,
                user,
                keyRing);
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Called from Offerbook when offer gets removed from P2P network
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void onOfferRemovedFromRemoteOfferBook(Offer offer) {
        offer.cancelAvailabilityRequest();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Take offer
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void checkOfferAvailability(Offer offer,
                                       ResultHandler resultHandler) {
        offer.checkOfferAvailability(getOfferAvailabilityModel(offer), resultHandler);
    }

    // When closing take offer view, we are not interested in the onCheckOfferAvailability result anymore, so remove from the map
    public void onCancelAvailabilityRequest(Offer offer) {
        offer.cancelAvailabilityRequest();
    }

    // First we check if offer is still available then we create the trade with the protocol
    public void onTakeOffer(Coin amount,
                            Offer offer,
                            String paymentAccountId,
                            TradeResultHandler tradeResultHandler) {
        final OfferAvailabilityModel model = getOfferAvailabilityModel(offer);
        offer.checkOfferAvailability(model,
                () -> {
                    if (offer.getState() == Offer.State.AVAILABLE)
                        createTrade(amount, offer, paymentAccountId, model, tradeResultHandler);
                });
    }

    private void createTrade(Coin amount,
                             Offer offer,
                             String paymentAccountId,
                             OfferAvailabilityModel model,
                             TradeResultHandler tradeResultHandler) {
        Trade trade;
        if (offer.getDirection() == Offer.Direction.BUY)
            trade = new SellerAsTakerTrade(offer, amount, model.getPeerAddress(), tradableListStorage);
        else
            trade = new BuyerAsTakerTrade(offer, amount, model.getPeerAddress(), tradableListStorage);

        trade.setTakeOfferDate(new Date());
        trade.setTakerPaymentAccountId(paymentAccountId);

        initTrade(trade);

        trades.add(trade);
        ((TakerTrade) trade).takeAvailableOffer();
        tradeResultHandler.handleResult(trade);
    }

    private OfferAvailabilityModel getOfferAvailabilityModel(Offer offer) {
        return new OfferAvailabilityModel(
                offer,
                keyRing.getPubKeyRing(),
                p2PService);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Trade
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void onWithdrawRequest(String toAddress, KeyParameter aesKey, Trade trade, ResultHandler resultHandler, FaultHandler faultHandler) {
        AddressEntry addressEntry = walletService.getAddressEntryByOfferId(trade.getId());
        String fromAddress = addressEntry.getAddressString();

        FutureCallback<Transaction> callback = new FutureCallback<Transaction>() {
            @Override
            public void onSuccess(@javax.annotation.Nullable Transaction transaction) {
                if (transaction != null) {
                    log.info("onWithdraw onSuccess tx ID:" + transaction.getHashAsString());
                    trade.setState(Trade.State.WITHDRAW_COMPLETED);
                    addTradeToClosedTrades(trade);
                    resultHandler.handleResult();
                }
            }

            @Override
            public void onFailure(@NotNull Throwable t) {
                t.printStackTrace();
                log.error(t.getMessage());
                faultHandler.handleFault("An exception occurred at requestWithdraw (onFailure).", t);
            }
        };
        try {
            walletService.sendFunds(fromAddress, toAddress, trade.getPayoutAmount(), aesKey, callback);
        } catch (AddressFormatException | InsufficientMoneyException e) {
            e.printStackTrace();
            log.error(e.getMessage());
            faultHandler.handleFault("An exception occurred at requestWithdraw.", e);
        }
    }

    // If trade was completed (closed without fault but might be closed by a dispute) we move it to the closed trades
    private void addTradeToClosedTrades(Trade trade) {
        trades.remove(trade);
        closedTradableManager.add(trade);
    }

    // If trade is in already in critical state (if taker role: taker fee; both roles: after deposit published)
    // we move the trade to failedTradesManager
    public void addTradeToFailedTrades(Trade trade) {
        trades.remove(trade);
        failedTradesManager.add(trade);
    }

    // If trade is in preparation (if taker role: before taker fee is paid; both roles: before deposit published)
    // we just remove the trade from our list. We don't store those trades.
    public void removePreparedTrade(Trade trade) {
        trades.remove(trade);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Dispute
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void closeDisputedTrade(String tradeId) {
        Optional<Trade> tradeOptional = getTradeById(tradeId);
        if (tradeOptional.isPresent()) {
            Trade trade = tradeOptional.get();
            trade.setDisputeState(Trade.DisputeState.DISPUTE_CLOSED);
            addTradeToClosedTrades(trade);
        }
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Getters
    ///////////////////////////////////////////////////////////////////////////////////////////

    public ObservableList<Trade> getTrades() {
        return trades.getObservableList();
    }

    public BooleanProperty pendingTradesInitializedProperty() {
        return pendingTradesInitialized;
    }

    public boolean isMyOffer(Offer offer) {
        return offer.isMyOffer(keyRing);
    }

    public Optional<Trade> getTradeById(String tradeId) {
        return trades.stream().filter(e -> e.getId().equals(tradeId)).findFirst();
    }

}