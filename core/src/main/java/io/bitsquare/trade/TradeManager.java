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
import io.bitsquare.trade.handlers.TakeOfferResultHandler;
import io.bitsquare.trade.offer.Offer;
import io.bitsquare.trade.offer.OpenOffer;
import io.bitsquare.trade.offer.OpenOfferManager;
import io.bitsquare.trade.protocol.availability.OfferAvailabilityModel;
import io.bitsquare.trade.protocol.availability.OfferAvailabilityProtocol;
import io.bitsquare.trade.protocol.trade.messages.RequestDepositTxInputsMessage;
import io.bitsquare.trade.protocol.trade.messages.RequestPayDepositMessage;
import io.bitsquare.trade.protocol.trade.messages.TradeMessage;
import io.bitsquare.trade.states.OffererTradeState;
import io.bitsquare.user.User;

import org.bitcoinj.core.AddressFormatException;
import org.bitcoinj.core.Coin;
import org.bitcoinj.core.InsufficientMoneyException;
import org.bitcoinj.core.Transaction;

import com.google.common.util.concurrent.FutureCallback;

import java.io.File;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
    private OpenOfferManager openOfferManager;
    private ClosedTradableManager closedTradableManager;
    private final ArbitrationRepository arbitrationRepository;

    private final Map<String, OfferAvailabilityProtocol> checkOfferAvailabilityProtocolMap = new HashMap<>();
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
                if (message instanceof RequestDepositTxInputsMessage ||
                        (message instanceof RequestPayDepositMessage && ((RequestPayDepositMessage) message).isInitialRequest))
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

            OffererTrade trade;
            if (offer.getDirection() == Offer.Direction.BUY)
                trade = new BuyerAsOffererTrade(offer, pendingTradesStorage);
            else
                trade = new SellerAsOffererTrade(offer, pendingTradesStorage);

            trade.setStorage(pendingTradesStorage);
            pendingTrades.add(trade);
            initTrade(trade);
            trade.handleTakeOfferRequest(message, sender);
            setupDepositPublishedListener(trade);
        }
        else {
            // TODO respond
            //(RequestDepositTxInputsMessage)message.
            //  messageService.sendEncryptedMessage(sender,messageWithPubKey.getMessage().);
            log.info("We received a take offer request but don't have that offer anymore.");
        }
    }

    // Only after published we consider the openOffer as closed and the trade as completely initialized
    private void setupDepositPublishedListener(Trade trade) {
        trade.processStateProperty().addListener((ov, oldValue, newValue) -> {
            log.debug("setupDepositPublishedListener state = " + newValue);
            if (newValue == OffererTradeState.ProcessState.DEPOSIT_PUBLISHED) {
                openOfferManager.closeOpenOffer(trade.getOffer());
                trade.setTakeOfferDate(new Date());
            }
        });
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

            if (trade.lifeCycleState == Trade.LifeCycleState.FAILED) {
                failedTrades.add(trade);
            }
            else {
                trade.setStorage(pendingTradesStorage);
                trade.updateDepositTxFromWallet(tradeWalletService);
                initTrade(trade);
            }
        }
        for (Trade trade : failedTrades) {
            pendingTrades.remove(trade);
            closedTradableManager.add(trade);
        }
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Called from Offerbook when offer gets removed from DHT
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void onOfferRemovedFromRemoteOfferBook(Offer offer) {
        disposeCheckOfferAvailabilityRequest(offer);
    }


  /*  private void handlePlaceOfferResult(Transaction transaction, Offer offer, TransactionResultHandler resultHandler) {
        Trade trade;
        if (offer.getDirection() == Offer.Direction.BUY)
            trade = new BuyerAsOffererTrade(offer, openOfferTradesStorage);
        else
            trade = new SellerAsOffererTrade(offer, openOfferTradesStorage);

        openOfferTrades.add(trade);
        initTrade(trade);
        setupDepositPublishedListener(trade);
        resultHandler.handleResult(transaction);
    }*/

  /*  private void setupDepositPublishedListener(Trade trade) {
        trade.processStateProperty().addListener((ov, oldValue, newValue) -> {
            log.debug("setupDepositPublishedListener state = " + newValue);
            if (newValue == OffererTradeState.ProcessState.DEPOSIT_PUBLISHED) {
                removeOpenOffer(trade.getOffer(),
                        () -> log.debug("remove offer was successful"),
                        log::error,
                        false);
                trade.setTakeOfferDate(new Date());
                pendingTrades.add(trade);
                trade.setStorage(pendingTradesStorage);
            }
        });
    }*/

  /*  public void onCancelOpenOffer(Offer offer, ResultHandler resultHandler, ErrorMessageHandler errorMessageHandler) {
        removeOpenOffer(offer, resultHandler, errorMessageHandler, true);
    }
*/
   /* private void removeOpenOffer(Offer offer,
                                 ResultHandler resultHandler,
                                 ErrorMessageHandler errorMessageHandler,
                                 boolean isCancelRequest) {
        offerBookService.removeOffer(offer,
                () -> {
                    offer.setState(Offer.State.REMOVED);
                    Optional<Trade> offererTradeOptional = openOfferTrades.stream().filter(e -> e.getId().equals(offer.getId())).findAny();
                    if (offererTradeOptional.isPresent()) {
                        Trade trade = offererTradeOptional.get();
                        openOfferTrades.remove(trade);

                        if (isCancelRequest) {
                            if (trade instanceof OffererTrade)
                                trade.setLifeCycleState(OffererTradeState.LifeCycleState.OFFER_CANCELED);
                            closedTrades.add(trade);
                            trade.disposeProtocol();
                        }
                    }

                    disposeCheckOfferAvailabilityRequest(offer);

                    resultHandler.handleResult();
                },
                (message, throwable) -> errorMessageHandler.handleErrorMessage(message));
    }*/


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Take offer
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void checkOfferAvailability(Offer offer) {
        if (!checkOfferAvailabilityProtocolMap.containsKey(offer.getId())) {
            OfferAvailabilityModel model = new OfferAvailabilityModel(
                    offer,
                    keyRing.getPubKeyRing(),
                    messageService,
                    addressService);

            OfferAvailabilityProtocol protocol = new OfferAvailabilityProtocol(model,
                    () -> disposeCheckOfferAvailabilityRequest(offer),
                    (errorMessage) -> disposeCheckOfferAvailabilityRequest(offer));
            checkOfferAvailabilityProtocolMap.put(offer.getId(), protocol);
            protocol.checkOfferAvailability();
        }
        else {
            log.error("That should never happen: onCheckOfferAvailability already called for offer with ID:" + offer.getId());
        }
    }

    // When closing take offer view, we are not interested in the onCheckOfferAvailability result anymore, so remove from the map
    public void cancelCheckOfferAvailabilityRequest(Offer offer) {
        disposeCheckOfferAvailabilityRequest(offer);
    }

    // First we check if offer is still available then we create the trade with the protocol
    public void requestTakeOffer(Coin amount, Offer offer, TakeOfferResultHandler takeOfferResultHandler) {
        OfferAvailabilityModel model = new OfferAvailabilityModel(offer, keyRing.getPubKeyRing(), messageService, addressService);
        OfferAvailabilityProtocol availabilityProtocol = new OfferAvailabilityProtocol(model,
                () -> createTrade(amount, offer, model, takeOfferResultHandler),
                (errorMessage) -> disposeCheckOfferAvailabilityRequest(offer));
        checkOfferAvailabilityProtocolMap.put(offer.getId(), availabilityProtocol);
        availabilityProtocol.checkOfferAvailability();
    }

    private void createTrade(Coin amount, Offer offer, OfferAvailabilityModel model, TakeOfferResultHandler
            takeOfferResultHandler) {
        disposeCheckOfferAvailabilityRequest(offer);
        if (offer.getState() == Offer.State.AVAILABLE) {
            Trade trade;
            if (offer.getDirection() == Offer.Direction.BUY)
                trade = new SellerAsTakerTrade(offer, amount, model.getPeer(), pendingTradesStorage);
            else
                trade = new BuyerAsTakerTrade(offer, amount, model.getPeer(), pendingTradesStorage);

            trade.setTakeOfferDate(new Date());
            initTrade(trade);
            pendingTrades.add(trade);
            if (trade instanceof TakerTrade)
                ((TakerTrade) trade).takeAvailableOffer();
            takeOfferResultHandler.handleResult(trade);
        }
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
                    trade.setLifeCycleState(Trade.LifeCycleState.COMPLETED);

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


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Called from Offerbook when offer gets removed from DHT
    ///////////////////////////////////////////////////////////////////////////////////////////

   /* public void onOfferRemovedFromRemoteOfferBook(Offer offer) {
        disposeCheckOfferAvailabilityRequest(offer);
    }
*/

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Getters
    ///////////////////////////////////////////////////////////////////////////////////////////

   /* public ObservableList<Trade> getOpenOfferTrades() {
        return openOfferTrades.getObservableList();
    }*/

    public ObservableList<Trade> getPendingTrades() {
        return pendingTrades.getObservableList();
    }

   /* public ObservableList<Trade> getClosedTrades() {
        return closedTrades.getObservableList();
    }
*/

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Misc
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void disposeCheckOfferAvailabilityRequest(Offer offer) {
        if (checkOfferAvailabilityProtocolMap.containsKey(offer.getId())) {
            OfferAvailabilityProtocol protocol = checkOfferAvailabilityProtocolMap.get(offer.getId());
            protocol.cancel();
            checkOfferAvailabilityProtocolMap.remove(offer.getId());
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
                user,
                keyRing);
    }

    public boolean isMyOffer(Offer offer) {
        return offer.isMyOffer(keyRing);
    }
}