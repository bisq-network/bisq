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

package io.bitsquare.trade.offer;

import io.bitsquare.btc.TradeWalletService;
import io.bitsquare.btc.WalletService;
import io.bitsquare.common.handlers.ErrorMessageHandler;
import io.bitsquare.common.handlers.ResultHandler;
import io.bitsquare.crypto.KeyRing;
import io.bitsquare.crypto.MessageWithPubKey;
import io.bitsquare.fiat.FiatAccount;
import io.bitsquare.p2p.DecryptedMessageHandler;
import io.bitsquare.p2p.Message;
import io.bitsquare.p2p.MessageService;
import io.bitsquare.p2p.Peer;
import io.bitsquare.p2p.listener.SendMessageListener;
import io.bitsquare.storage.Storage;
import io.bitsquare.trade.TradableList;
import io.bitsquare.trade.closed.ClosedTradableManager;
import io.bitsquare.trade.handlers.TransactionResultHandler;
import io.bitsquare.trade.protocol.availability.messages.OfferAvailabilityRequest;
import io.bitsquare.trade.protocol.availability.messages.OfferAvailabilityResponse;
import io.bitsquare.trade.protocol.placeoffer.PlaceOfferModel;
import io.bitsquare.trade.protocol.placeoffer.PlaceOfferProtocol;
import io.bitsquare.user.AccountSettings;
import io.bitsquare.user.User;

import org.bitcoinj.core.Coin;
import org.bitcoinj.utils.Fiat;

import com.google.inject.Inject;

import java.io.File;

import java.util.Optional;

import javax.inject.Named;

import javafx.collections.ObservableList;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static com.google.inject.internal.util.$Preconditions.checkNotNull;
import static io.bitsquare.util.Validator.nonEmptyStringOf;

public class OpenOfferManager {
    private static final Logger log = LoggerFactory.getLogger(OpenOfferManager.class);

    private final User user;
    private final KeyRing keyRing;
    private final AccountSettings accountSettings;
    private final WalletService walletService;
    private MessageService messageService;
    private final TradeWalletService tradeWalletService;
    private final OfferBookService offerBookService;
    private ClosedTradableManager closedTradableManager;

    private final TradableList<OpenOffer> openOffers;
    private final Storage<TradableList<OpenOffer>> openOffersStorage;
    private boolean shutDownRequested;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    public OpenOfferManager(User user,
                            KeyRing keyRing,
                            AccountSettings accountSettings,
                            WalletService walletService,
                            MessageService messageService,
                            TradeWalletService tradeWalletService,
                            OfferBookService offerBookService,
                            ClosedTradableManager closedTradableManager,
                            @Named("storage.dir") File storageDir) {
        this.user = user;
        this.keyRing = keyRing;
        this.accountSettings = accountSettings;
        this.walletService = walletService;
        this.messageService = messageService;
        this.tradeWalletService = tradeWalletService;
        this.offerBookService = offerBookService;
        this.closedTradableManager = closedTradableManager;

        openOffersStorage = new Storage<>(storageDir);
        this.openOffers = new TradableList<>(openOffersStorage, "OpenOffers");

        // In case the app did get killed the shutDown from the modules is not called, so we use a shutdown hook
        Thread shutDownHookThread = new Thread(OpenOfferManager.this::shutDown, "OpenOfferManager.ShutDownHook");
        Runtime.getRuntime().addShutdownHook(shutDownHookThread);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Lifecycle
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void onAllServicesInitialized() {
        log.trace("onAllServicesInitialized");

        // Handler for incoming offer availability requests
        messageService.addDecryptedMessageHandler(new DecryptedMessageHandler() {
            @Override
            public void handleMessage(MessageWithPubKey messageWithPubKey, Peer sender) {
                // We get an encrypted message but don't do the signature check as we don't know the peer yet.
                // A basic sig check is in done also at decryption time
                Message message = messageWithPubKey.getMessage();
                if (message instanceof OfferAvailabilityRequest)
                    handleOfferAvailabilityRequest((OfferAvailabilityRequest) message, sender);
            }
        });

        for (OpenOffer openOffer : openOffers) {
            // We add own offers to offerbook when we go online again
            offerBookService.addOffer(openOffer.getOffer(),
                    () -> log.debug("Successful added offer to DHT"),
                    (message, throwable) -> log.error("Add offer to DHT failed. " + message));
            //setupDepositPublishedListener(openOffer);
            openOffer.setStorage(openOffersStorage);
        }
    }

    public void shutDown() {
        if (!shutDownRequested) {
            log.debug("shutDown");
            shutDownRequested = true;
            // we remove own offers form offerbook when we go offline
            for (OpenOffer openOffer : openOffers) {
                offerBookService.removeOfferAtShutDown(openOffer.getOffer());
            }
        }
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // API
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void onPlaceOffer(String id,
                             Offer.Direction direction,
                             Fiat price,
                             Coin amount,
                             Coin minAmount,
                             TransactionResultHandler resultHandler,
                             ErrorMessageHandler errorMessageHandler) {

        FiatAccount fiatAccount = user.currentFiatAccountProperty().get();
        Offer offer = new Offer(id,
                keyRing.getPubKeyRing(),
                direction,
                price.getValue(),
                amount,
                minAmount,
                fiatAccount.type,
                fiatAccount.currencyCode,
                fiatAccount.country,
                fiatAccount.id,
                accountSettings.getAcceptedArbitratorIds(),
                accountSettings.getSecurityDeposit(),
                accountSettings.getAcceptedCountries(),
                accountSettings.getAcceptedLanguageLocaleCodes());

        PlaceOfferModel model = new PlaceOfferModel(offer, walletService, tradeWalletService, offerBookService);

        PlaceOfferProtocol placeOfferProtocol = new PlaceOfferProtocol(
                model,
                transaction -> {
                    OpenOffer openOffer = new OpenOffer(offer, openOffersStorage);
                    openOffers.add(openOffer);
                    openOffersStorage.queueUpForSave();
                    resultHandler.handleResult(transaction);
                },
                errorMessageHandler::handleErrorMessage
        );

        placeOfferProtocol.placeOffer();
    }


    public void onCancelOpenOffer(Offer offer, ResultHandler resultHandler, ErrorMessageHandler errorMessageHandler) {
        Optional<OpenOffer> openOfferOptional = findOpenOffer(offer.getId());
        if (openOfferOptional.isPresent())
            onCancelOpenOffer(openOfferOptional.get(), resultHandler, errorMessageHandler);
    }

    public void onCancelOpenOffer(OpenOffer openOffer, ResultHandler resultHandler, ErrorMessageHandler errorMessageHandler) {
        offerBookService.removeOffer(openOffer.getOffer(),
                () -> {
                    openOffer.getOffer().setState(Offer.State.REMOVED);
                    openOffer.setState(OpenOffer.State.CANCELED);
                    openOffers.remove(openOffer);
                    closedTradableManager.add(openOffer);
                    //disposeCheckOfferAvailabilityRequest(offer);
                    resultHandler.handleResult();
                },
                (message, throwable) -> errorMessageHandler.handleErrorMessage(message));
    }

    public void reserveOpenOffer(OpenOffer openOffer) {
        openOffer.setState(OpenOffer.State.RESERVED);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Getters
    ///////////////////////////////////////////////////////////////////////////////////////////

    public boolean isMyOffer(Offer offer) {
        return offer.isMyOffer(keyRing);
    }

    public ObservableList<OpenOffer> getOpenOffers() {
        return openOffers.getObservableList();
    }

    public Optional<OpenOffer> findOpenOffer(String offerId) {
        return openOffers.stream().filter(openOffer -> openOffer.getId().equals(offerId)).findAny();
    }

    // Close openOffer after deposit published
    public void closeOpenOffer(Offer offer) {
        findOpenOffer(offer.getId()).ifPresent(openOffer -> {
            openOffers.remove(openOffer);
            openOffer.setState(OpenOffer.State.CLOSED);
            offerBookService.removeOffer(openOffer.getOffer(),
                    () -> log.trace("Successful removed offer"),
                    (message, throwable) -> log.error(message));
        });

    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Offer Availability
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void handleOfferAvailabilityRequest(OfferAvailabilityRequest message, Peer sender) {
        log.trace("handleNewMessage: message = " + message.getClass().getSimpleName() + " from " + sender);
        try {
            nonEmptyStringOf(message.offerId);
            checkNotNull(message.getPubKeyRing());
        } catch (Throwable t) {
            log.warn("Invalid message " + message.toString());
            return;
        }

        Optional<OpenOffer> openOfferOptional = findOpenOffer(message.offerId);
        boolean isAvailable = openOfferOptional.isPresent() && openOfferOptional.get().getState() == OpenOffer.State.AVAILABLE;
        try {
            OfferAvailabilityResponse offerAvailabilityResponse = new OfferAvailabilityResponse(message.offerId, isAvailable);
            messageService.sendEncryptedMessage(sender,
                    message.getPubKeyRing(),
                    offerAvailabilityResponse,
                    new SendMessageListener() {
                        @Override
                        public void handleResult() {
                            log.trace("ReportOfferAvailabilityMessage successfully arrived at peer");
                        }

                        @Override
                        public void handleFault() {
                            log.info("Sending ReportOfferAvailabilityMessage failed.");
                        }
                    });
        } catch (Throwable t) {
            t.printStackTrace();
            log.info("Exception at handleRequestIsOfferAvailableMessage " + t.getMessage());
        }
    }
}
