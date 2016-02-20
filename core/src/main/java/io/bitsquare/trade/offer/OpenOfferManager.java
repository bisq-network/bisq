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

import com.google.inject.Inject;
import io.bitsquare.app.Log;
import io.bitsquare.btc.TradeWalletService;
import io.bitsquare.btc.WalletService;
import io.bitsquare.common.Timer;
import io.bitsquare.common.UserThread;
import io.bitsquare.common.crypto.KeyRing;
import io.bitsquare.common.handlers.ErrorMessageHandler;
import io.bitsquare.common.handlers.ResultHandler;
import io.bitsquare.p2p.BootstrapListener;
import io.bitsquare.p2p.Message;
import io.bitsquare.p2p.NodeAddress;
import io.bitsquare.p2p.P2PService;
import io.bitsquare.p2p.messaging.DecryptedDirectMessageListener;
import io.bitsquare.p2p.messaging.DecryptedMsgWithPubKey;
import io.bitsquare.p2p.messaging.SendDirectMessageListener;
import io.bitsquare.p2p.peers.PeerManager;
import io.bitsquare.storage.Storage;
import io.bitsquare.trade.TradableList;
import io.bitsquare.trade.closed.ClosedTradableManager;
import io.bitsquare.trade.handlers.TransactionResultHandler;
import io.bitsquare.trade.protocol.availability.messages.OfferAvailabilityRequest;
import io.bitsquare.trade.protocol.availability.messages.OfferAvailabilityResponse;
import io.bitsquare.trade.protocol.placeoffer.PlaceOfferModel;
import io.bitsquare.trade.protocol.placeoffer.PlaceOfferProtocol;
import io.bitsquare.user.User;
import javafx.collections.ObservableList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import javax.inject.Named;
import java.io.File;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static com.google.inject.internal.util.$Preconditions.checkNotNull;
import static io.bitsquare.util.Validator.nonEmptyStringOf;


public class OpenOfferManager implements PeerManager.Listener, DecryptedDirectMessageListener {
    private static final Logger log = LoggerFactory.getLogger(OpenOfferManager.class);

    private static final long RETRY_DELAY_AFTER_ALL_CON_LOST_SEC = 5;

    private final KeyRing keyRing;
    private final User user;
    private final P2PService p2PService;
    private final WalletService walletService;
    private final TradeWalletService tradeWalletService;
    private final OfferBookService offerBookService;
    private final ClosedTradableManager closedTradableManager;

    private final TradableList<OpenOffer> openOffers;
    private final Storage<TradableList<OpenOffer>> openOffersStorage;
    private boolean stopped;
    private Timer periodicRepublishOffersTimer, periodicRefreshOffersTimer, republishOffersTimer;
    private BootstrapListener bootstrapListener;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor, Initialization
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    public OpenOfferManager(KeyRing keyRing,
                            User user,
                            P2PService p2PService,
                            WalletService walletService,
                            TradeWalletService tradeWalletService,
                            OfferBookService offerBookService,
                            ClosedTradableManager closedTradableManager,
                            @Named("storage.dir") File storageDir) {
        this.keyRing = keyRing;
        this.user = user;
        this.p2PService = p2PService;
        this.walletService = walletService;
        this.tradeWalletService = tradeWalletService;
        this.offerBookService = offerBookService;
        this.closedTradableManager = closedTradableManager;

        openOffersStorage = new Storage<>(storageDir);
        this.openOffers = new TradableList<>(openOffersStorage, "OpenOffers");

        // In case the app did get killed the shutDown from the modules is not called, so we use a shutdown hook
        Runtime.getRuntime().addShutdownHook(new Thread(OpenOfferManager.this::shutDown,
                "OpenOfferManager.ShutDownHook"));
    }

    public void onAllServicesInitialized() {
        bootstrapListener = new BootstrapListener() {
            @Override
            public void onBootstrapComplete() {
                OpenOfferManager.this.onBootstrapComplete();
            }
        };
        p2PService.addP2PServiceListener(bootstrapListener);
        p2PService.addDecryptedDirectMessageListener(this);
    }

    @SuppressWarnings("WeakerAccess")
    public void shutDown() {
        shutDown(null);
    }

    public void shutDown(@Nullable Runnable completeHandler) {
        stopped = true;
        p2PService.getPeerManager().removeListener(this);
        p2PService.removeDecryptedDirectMessageListener(this);
        if (bootstrapListener != null)
            p2PService.removeP2PServiceListener(bootstrapListener);

        stopPeriodicRefreshOffersTimer();
        stopPeriodicRepublishOffersTimer();

        log.info("remove all open offers at shutDown");
        // we remove own offers from offerbook when we go offline
        openOffers.forEach(openOffer -> offerBookService.removeOfferAtShutDown(openOffer.getOffer()));

        if (completeHandler != null)
            UserThread.runAfter(completeHandler::run, openOffers.size() * 200 + 300, TimeUnit.MILLISECONDS);
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // DecryptedDirectMessageListener implementation
    ///////////////////////////////////////////////////////////////////////////////////////////


    @Override
    public void onDirectMessage(DecryptedMsgWithPubKey decryptedMsgWithPubKey, NodeAddress peerNodeAddress) {
        // Handler for incoming offer availability requests
        // We get an encrypted message but don't do the signature check as we don't know the peer yet.
        // A basic sig check is in done also at decryption time
        Message message = decryptedMsgWithPubKey.message;
        if (message instanceof OfferAvailabilityRequest)
            handleOfferAvailabilityRequest((OfferAvailabilityRequest) message, peerNodeAddress);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // BootstrapListener delegate
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void onBootstrapComplete() {
        p2PService.removeP2PServiceListener(bootstrapListener);

        stopped = false;

        // Republish means we send the complete offer object
        republishOffers();
        startRepublishOffersThread();

        // Refresh is started once we get a success from republish

        p2PService.getPeerManager().addListener(this);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // PeerManager.Listener implementation
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void onAllConnectionsLost() {
        stopped = true;
        stopPeriodicRefreshOffersTimer();
        stopPeriodicRepublishOffersTimer();
    }

    @Override
    public void onNewConnectionAfterAllConnectionsLost() {
        restart();
    }

    @Override
    public void onAwakeFromStandby() {
        if (!p2PService.getNetworkNode().getAllConnections().isEmpty())
            restart();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // RepublishOffers, refreshOffers
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void startRepublishOffersThread() {
        stopped = false;
        if (periodicRepublishOffersTimer == null)
            periodicRepublishOffersTimer = UserThread.runPeriodically(OpenOfferManager.this::republishOffers,
                    Offer.TTL * 10,
                    TimeUnit.MILLISECONDS);
    }

    private void republishOffers() {
        Log.traceCall("Number of offer for republish: " + openOffers.size());
        if (!stopped) {
            stopPeriodicRefreshOffersTimer();

            for (OpenOffer openOffer : openOffers) {
                offerBookService.republishOffers(openOffer.getOffer(),
                        () -> {
                            log.debug("Successful added offer to P2P network");
                            // Refresh means we send only the dat needed to refresh the TTL (hash, signature and sequence nr.)
                            startRefreshOffersThread();
                        },
                        errorMessage -> {
                            //TODO handle with retry
                            log.error("Add offer to P2P network failed. " + errorMessage);
                            stopRepublishOffersTimer();
                            republishOffersTimer = UserThread.runAfter(OpenOfferManager.this::republishOffers,
                                    RETRY_DELAY_AFTER_ALL_CON_LOST_SEC);
                        });
                openOffer.setStorage(openOffersStorage);
            }
        } else {
            log.warn("We have stopped already. We ignore that republishOffers call.");
        }
    }

    private void startRefreshOffersThread() {
        stopped = false;
        // refresh sufficiently before offer would expire
        if (periodicRefreshOffersTimer == null)
            periodicRefreshOffersTimer = UserThread.runPeriodically(OpenOfferManager.this::refreshOffers,
                    (long) (Offer.TTL * 0.5),
                    TimeUnit.MILLISECONDS);
    }

    private void refreshOffers() {
        if (!stopped) {
            Log.traceCall("Number of offer for refresh: " + openOffers.size());
            for (OpenOffer openOffer : openOffers) {
                offerBookService.refreshOffer(openOffer.getOffer(),
                        () -> log.debug("Successful refreshed TTL for offer"),
                        errorMessage -> log.error("Refresh TTL for offer failed. " + errorMessage));
            }
        } else {
            log.warn("We have stopped already. We ignore that refreshOffers call.");
        }
    }

    private void restart() {
        startRepublishOffersThread();
        startRefreshOffersThread();
        if (republishOffersTimer == null) {
            stopped = false;
            republishOffersTimer = UserThread.runAfter(OpenOfferManager.this::republishOffers, RETRY_DELAY_AFTER_ALL_CON_LOST_SEC);
        }
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // API
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void placeOffer(Offer offer,
                           TransactionResultHandler resultHandler) {
        PlaceOfferModel model = new PlaceOfferModel(offer, walletService, tradeWalletService, offerBookService, user);
        PlaceOfferProtocol placeOfferProtocol = new PlaceOfferProtocol(
                model,
                transaction -> {
                    OpenOffer openOffer = new OpenOffer(offer, openOffersStorage);
                    openOffers.add(openOffer);
                    openOffersStorage.queueUpForSave();
                    resultHandler.handleResult(transaction);
                }
        );
        placeOfferProtocol.placeOffer();
    }

    // Remove from offerbook
    public void removeOffer(Offer offer, ResultHandler resultHandler, ErrorMessageHandler errorMessageHandler) {
        Optional<OpenOffer> openOfferOptional = findOpenOffer(offer.getId());
        if (openOfferOptional.isPresent()) {
            removeOpenOffer(openOfferOptional.get(), resultHandler, errorMessageHandler);
        } else {
            log.warn("Offer was not found in our list of open offers. We still try to remove it from the offerbook.");
            errorMessageHandler.handleErrorMessage("Offer was not found in our list of open offers. " +
                    "We still try to remove it from the offerbook.");
            offerBookService.removeOffer(offer,
                    () -> offer.setState(Offer.State.REMOVED),
                    null);
        }
    }

    // Remove from my offers
    public void removeOpenOffer(OpenOffer openOffer, ResultHandler resultHandler, ErrorMessageHandler errorMessageHandler) {
        Offer offer = openOffer.getOffer();
        offerBookService.removeOffer(offer,
                () -> {
                    offer.setState(Offer.State.REMOVED);
                    openOffer.setState(OpenOffer.State.CANCELED);
                    openOffers.remove(openOffer);
                    closedTradableManager.add(openOffer);
                    resultHandler.handleResult();
                },
                errorMessageHandler);
    }

    // Close openOffer after deposit published
    public void closeOpenOffer(Offer offer) {
        findOpenOffer(offer.getId()).ifPresent(openOffer -> {
            openOffers.remove(openOffer);
            openOffer.setState(OpenOffer.State.CLOSED);
            offerBookService.removeOffer(openOffer.getOffer(),
                    () -> log.trace("Successful removed offer"),
                    log::error);
        });
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

    public Optional<OpenOffer> getOpenOfferById(String offerId) {
        return openOffers.stream().filter(e -> e.getId().equals(offerId)).findFirst();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Offer Availability
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void handleOfferAvailabilityRequest(OfferAvailabilityRequest message, NodeAddress sender) {
        log.trace("handleNewMessage: message = " + message.getClass().getSimpleName() + " from " + sender);
        if (!stopped) {
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
                p2PService.sendEncryptedDirectMessage(sender,
                        message.getPubKeyRing(),
                        new OfferAvailabilityResponse(message.offerId, isAvailable),
                        new SendDirectMessageListener() {
                            @Override
                            public void onArrived() {
                                log.trace("OfferAvailabilityResponse successfully arrived at peer");
                            }

                            @Override
                            public void onFault() {
                                log.info("Sending OfferAvailabilityResponse failed.");
                            }
                        });
            } catch (Throwable t) {
                t.printStackTrace();
                log.info("Exception at handleRequestIsOfferAvailableMessage " + t.getMessage());
            }
        } else {
            log.warn("We have stopped already. We ignore that handleOfferAvailabilityRequest call.");
        }
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Private
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void stopPeriodicRefreshOffersTimer() {
        if (periodicRefreshOffersTimer != null) {
            periodicRefreshOffersTimer.stop();
            periodicRefreshOffersTimer = null;
        }
    }

    private void stopPeriodicRepublishOffersTimer() {
        if (periodicRepublishOffersTimer != null) {
            periodicRepublishOffersTimer.stop();
            periodicRepublishOffersTimer = null;
        }
    }

    private void stopRepublishOffersTimer() {
        if (republishOffersTimer != null) {
            republishOffersTimer.stop();
            republishOffersTimer = null;
        }
    }
}
