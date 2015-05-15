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

import io.bitsquare.arbitration.ArbitrationRepository;
import io.bitsquare.btc.AddressEntry;
import io.bitsquare.btc.BlockChainService;
import io.bitsquare.btc.TradeWalletService;
import io.bitsquare.btc.WalletService;
import io.bitsquare.common.handlers.FaultHandler;
import io.bitsquare.common.handlers.ResultHandler;
import io.bitsquare.crypto.CryptoService;
import io.bitsquare.crypto.KeyRing;
import io.bitsquare.crypto.MessageWithPubKey;
import io.bitsquare.crypto.SealedAndSignedMessage;
import io.bitsquare.p2p.AddressService;
import io.bitsquare.p2p.DecryptedMessageHandler;
import io.bitsquare.p2p.MailboxMessage;
import io.bitsquare.p2p.MailboxService;
import io.bitsquare.p2p.Message;
import io.bitsquare.p2p.MessageService;
import io.bitsquare.p2p.Peer;
import io.bitsquare.storage.Storage;
import io.bitsquare.trade.closed.ClosedTradableManager;
import io.bitsquare.trade.failed.FailedTradesManager;
import io.bitsquare.trade.handlers.TakeOfferResultHandler;
import io.bitsquare.trade.offer.Offer;
import io.bitsquare.trade.offer.OpenOffer;
import io.bitsquare.trade.offer.OpenOfferManager;
import io.bitsquare.trade.protocol.availability.OfferAvailabilityModel;
import io.bitsquare.trade.protocol.trade.messages.DepositTxInputsRequest;
import io.bitsquare.trade.protocol.trade.messages.PayDepositRequest;
import io.bitsquare.trade.protocol.trade.messages.TradeMessage;
import io.bitsquare.user.User;

import org.bitcoinj.core.AddressFormatException;
import org.bitcoinj.core.Coin;
import org.bitcoinj.core.InsufficientMoneyException;
import org.bitcoinj.core.Transaction;

import com.google.common.util.concurrent.FutureCallback;

import java.io.File;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Optional;

import javax.inject.Inject;
import javax.inject.Named;

import javafx.collections.ObservableList;

import org.jetbrains.annotations.NotNull;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static io.bitsquare.util.Validator.nonEmptyStringOf;

public class TradeManager {
    private static final Logger log = LoggerFactory.getLogger(TradeManager.class);

    private final User user;
    private final KeyRing keyRing;
    private final MessageService messageService;
    private final MailboxService mailboxService;
    private final AddressService addressService;
    private final BlockChainService blockChainService;
    private final WalletService walletService;
    private final TradeWalletService tradeWalletService;
    private final CryptoService<MailboxMessage> cryptoService;
    private final OpenOfferManager openOfferManager;
    private final ClosedTradableManager closedTradableManager;
    private final FailedTradesManager failedTradesManager;
    private final ArbitrationRepository arbitrationRepository;

    private final Storage<TradableList<Trade>> pendingTradesStorage;
    private final TradableList<Trade> pendingTrades;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    public TradeManager(User user,
                        KeyRing keyRing,
                        MessageService messageService,
                        MailboxService mailboxService,
                        AddressService addressService,
                        BlockChainService blockChainService,
                        WalletService walletService,
                        TradeWalletService tradeWalletService,
                        CryptoService<MailboxMessage> cryptoService,
                        OpenOfferManager openOfferManager,
                        ClosedTradableManager closedTradableManager,
                        FailedTradesManager failedTradesManager,
                        ArbitrationRepository arbitrationRepository,
                        @Named("storage.dir") File storageDir) {
        this.user = user;
        this.keyRing = keyRing;
        this.messageService = messageService;
        this.mailboxService = mailboxService;
        this.addressService = addressService;
        this.blockChainService = blockChainService;
        this.walletService = walletService;
        this.tradeWalletService = tradeWalletService;
        this.cryptoService = cryptoService;
        this.openOfferManager = openOfferManager;
        this.closedTradableManager = closedTradableManager;
        this.failedTradesManager = failedTradesManager;
        this.arbitrationRepository = arbitrationRepository;

        pendingTradesStorage = new Storage<>(storageDir);
        this.pendingTrades = new TradableList<>(pendingTradesStorage, "PendingTrades");
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Lifecycle
    ///////////////////////////////////////////////////////////////////////////////////////////

    // When all services are initialized we create the protocols for our open offers and persisted pendingTrades
    // OffererAsBuyerProtocol listens for take offer requests, so we need to instantiate it early.
    public void onAllServicesInitialized() {
        log.trace("onAllServicesInitialized");

        // If there are messages in our mailbox we apply it and remove them from the DHT
        // We run that before initializing the pending trades to be sure the state is correct
        mailboxService.getAllMessages(
                (encryptedMailboxMessages) -> {
                    log.trace("mailboxService.getAllMessages success");
                    setMailboxMessagesToTrades(encryptedMailboxMessages);
                    emptyMailbox();
                    initPendingTrades();
                });

        // Handler for incoming initial messages from taker
        messageService.addDecryptedMessageHandler(new DecryptedMessageHandler() {
            @Override
            public void handleMessage(MessageWithPubKey messageWithPubKey, Peer sender) {
                // We get an encrypted message but don't do the signature check as we don't know the peer yet.
                // A basic sig check is in done also at decryption time
                Message message = messageWithPubKey.getMessage();
                // Those 2 messages are initial request form the taker.
                // RequestPayDepositMessage is used also in case of SellerAsTaker but there it is handled in the protocol as it is not an initial request
                if (message instanceof DepositTxInputsRequest ||
                        (message instanceof PayDepositRequest && ((PayDepositRequest) message).isInitialRequest))
                    handleInitialTakeOfferRequest((TradeMessage) message, sender);
            }
        });
    }

    private void handleInitialTakeOfferRequest(TradeMessage message, Peer sender) {
        log.trace("handleNewMessage: message = " + message.getClass().getSimpleName() + " from " + sender);
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
                trade = new BuyerAsOffererTrade(offer, pendingTradesStorage);
            else
                trade = new SellerAsOffererTrade(offer, pendingTradesStorage);

            trade.setStorage(pendingTradesStorage);
            initTrade(trade);
            pendingTrades.add(trade);
            ((OffererTrade) trade).handleTakeOfferRequest(message, sender);
        }
        else {
            // TODO respond
            //(RequestDepositTxInputsMessage)message.
            //  messageService.sendEncryptedMessage(sender,messageWithPubKey.getMessage().);
            log.info("We received a take offer request but don't have that offer anymore.");
        }
    }

    private void initTrade(Trade trade) {
        trade.init(messageService,
                walletService,
                addressService,
                tradeWalletService,
                blockChainService,
                cryptoService,
                arbitrationRepository,
                this,
                openOfferManager,
                user,
                keyRing);
    }

    private void setMailboxMessagesToTrades(List<SealedAndSignedMessage> encryptedMessages) {
        log.trace("applyMailboxMessage encryptedMailboxMessage.size=" + encryptedMessages.size());
        for (SealedAndSignedMessage encrypted : encryptedMessages) {
            try {
                MessageWithPubKey messageWithPubKey = cryptoService.decryptAndVerifyMessage(encrypted);
                Message message = messageWithPubKey.getMessage();
                if (message instanceof MailboxMessage && message instanceof TradeMessage) {
                    String tradeId = ((TradeMessage) message).tradeId;
                    Optional<Trade> tradeOptional = pendingTrades.stream().filter(e -> e.getId().equals(tradeId)).findAny();
                    if (tradeOptional.isPresent())
                        tradeOptional.get().setMailboxMessage(messageWithPubKey);
                }
            } catch (Throwable e) {
                e.printStackTrace();
                log.error(e.getMessage());
            }
        }
    }

    private void emptyMailbox() {
        mailboxService.removeAllMessages(
                () -> log.debug("All mailbox entries removed"),
                (errorMessage, fault) -> {
                    log.error(errorMessage);
                    log.error(fault.getMessage());
                });
    }

    private void initPendingTrades() {
        List<Trade> failedTrades = new ArrayList<>();
        for (Trade trade : pendingTrades) {
            // We continue an interrupted trade.
            // TODO if the peer has changed its IP address, we need to make another findPeer request. At the moment we use the peer stored in trade to
            // continue the trade, but that might fail.

            if (trade.isFailedState()) {
                failedTrades.add(trade);
            }
            else {
                trade.setStorage(pendingTradesStorage);
                trade.updateDepositTxFromWallet(tradeWalletService);
                initTrade(trade);
            }
        }

        for (Trade trade : failedTrades) {
            if (trade.isCriticalFault())
                addTradeToFailedTrades(trade);
            else
                addTradeToClosedTrades(trade);
        }
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Called from Offerbook when offer gets removed from DHT
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void onOfferRemovedFromRemoteOfferBook(Offer offer) {
        offer.cancelAvailabilityRequest();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Take offer
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void onCheckOfferAvailability(Offer offer) {
        offer.checkOfferAvailability(getOfferAvailabilityModel(offer));
    }

    // When closing take offer view, we are not interested in the onCheckOfferAvailability result anymore, so remove from the map
    public void onCancelAvailabilityRequest(Offer offer) {
        offer.cancelAvailabilityRequest();
    }

    // First we check if offer is still available then we create the trade with the protocol
    public void onTakeOffer(Coin amount, Offer offer, TakeOfferResultHandler takeOfferResultHandler) {
        final OfferAvailabilityModel model = getOfferAvailabilityModel(offer);
        offer.checkOfferAvailability(model, () -> {
            if (offer.getState() == Offer.State.AVAILABLE)
                createTrade(amount, offer, model, takeOfferResultHandler);
        });
    }

    private void createTrade(Coin amount, Offer offer, OfferAvailabilityModel model, TakeOfferResultHandler takeOfferResultHandler) {
        Trade trade;
        if (offer.getDirection() == Offer.Direction.BUY)
            trade = new SellerAsTakerTrade(offer, amount, model.getPeer(), pendingTradesStorage);
        else
            trade = new BuyerAsTakerTrade(offer, amount, model.getPeer(), pendingTradesStorage);

        trade.setTakeOfferDate(new Date());
        initTrade(trade);
        pendingTrades.add(trade);
        ((TakerTrade) trade).takeAvailableOffer();
        takeOfferResultHandler.handleResult(trade);
    }

    private OfferAvailabilityModel getOfferAvailabilityModel(Offer offer) {
        return new OfferAvailabilityModel(
                offer,
                keyRing.getPubKeyRing(),
                messageService,
                addressService);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Trade
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void onWithdrawRequest(String toAddress, Trade trade, ResultHandler resultHandler, FaultHandler faultHandler) {
        AddressEntry addressEntry = walletService.getAddressEntry(trade.getId());
        String fromAddress = addressEntry.getAddressString();

        FutureCallback<Transaction> callback = new FutureCallback<Transaction>() {
            @Override
            public void onSuccess(@javax.annotation.Nullable Transaction transaction) {
                if (transaction != null) {
                    log.info("onWithdraw onSuccess tx ID:" + transaction.getHashAsString());

                    if (trade instanceof BuyerTrade)
                        trade.setTradeState(TradeState.BuyerState.WITHDRAW_COMPLETED);
                    else if (trade instanceof SellerTrade)
                        trade.setTradeState(TradeState.SellerState.WITHDRAW_COMPLETED);

                    pendingTrades.remove(trade);
                    closedTradableManager.add(trade);

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
            walletService.sendFunds(fromAddress, toAddress, trade.getPayoutAmount(), callback);
        } catch (AddressFormatException | InsufficientMoneyException e) {
            e.printStackTrace();
            log.error(e.getMessage());
            faultHandler.handleFault("An exception occurred at requestWithdraw.", e);
        }
    }

    // In a fault case we remove it and add it to the closed trades
    public void addTradeToClosedTrades(Trade trade) {
        pendingTrades.remove(trade);
        closedTradableManager.add(trade);
    }

    public void addTradeToFailedTrades(Trade trade) {
        pendingTrades.remove(trade);
        failedTradesManager.add(trade);
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Getters
    ///////////////////////////////////////////////////////////////////////////////////////////

    public ObservableList<Trade> getPendingTrades() {
        return pendingTrades.getObservableList();
    }

    public boolean isMyOffer(Offer offer) {
        return offer.isMyOffer(keyRing);
    }
}