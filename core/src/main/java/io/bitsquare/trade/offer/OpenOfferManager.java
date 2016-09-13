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
import io.bitsquare.app.DevFlags;
import io.bitsquare.app.Log;
import io.bitsquare.btc.AddressEntry;
import io.bitsquare.btc.TradeWalletService;
import io.bitsquare.btc.WalletService;
import io.bitsquare.btc.pricefeed.PriceFeedService;
import io.bitsquare.common.Timer;
import io.bitsquare.common.UserThread;
import io.bitsquare.common.crypto.KeyRing;
import io.bitsquare.common.handlers.ErrorMessageHandler;
import io.bitsquare.common.handlers.ResultHandler;
import io.bitsquare.crypto.DecryptedMsgWithPubKey;
import io.bitsquare.p2p.BootstrapListener;
import io.bitsquare.p2p.Message;
import io.bitsquare.p2p.NodeAddress;
import io.bitsquare.p2p.P2PService;
import io.bitsquare.p2p.messaging.DecryptedDirectMessageListener;
import io.bitsquare.p2p.messaging.SendDirectMessageListener;
import io.bitsquare.p2p.peers.PeerManager;
import io.bitsquare.storage.Storage;
import io.bitsquare.trade.TradableList;
import io.bitsquare.trade.closed.ClosedTradableManager;
import io.bitsquare.trade.exceptions.MarketPriceNotAvailableException;
import io.bitsquare.trade.exceptions.TradePriceOutOfToleranceException;
import io.bitsquare.trade.handlers.TransactionResultHandler;
import io.bitsquare.trade.protocol.availability.AvailabilityResult;
import io.bitsquare.trade.protocol.availability.messages.OfferAvailabilityRequest;
import io.bitsquare.trade.protocol.availability.messages.OfferAvailabilityResponse;
import io.bitsquare.trade.protocol.placeoffer.PlaceOfferModel;
import io.bitsquare.trade.protocol.placeoffer.PlaceOfferProtocol;
import io.bitsquare.user.Preferences;
import io.bitsquare.user.User;
import javafx.collections.ObservableList;
import org.bitcoinj.core.Coin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import javax.inject.Named;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static com.google.inject.internal.util.$Preconditions.checkNotNull;
import static io.bitsquare.util.Validator.nonEmptyStringOf;


public class OpenOfferManager implements PeerManager.Listener, DecryptedDirectMessageListener {
    private static final Logger log = LoggerFactory.getLogger(OpenOfferManager.class);

    private static final long RETRY_REPUBLISH_DELAY_SEC = 10;
    private static final long REPUBLISH_AGAIN_AT_STARTUP_DELAY_SEC = 30;
    private static final long REPUBLISH_INTERVAL_MS = TimeUnit.MINUTES.toMillis(DevFlags.STRESS_TEST_MODE ? 20 : 20);
    private static final long REFRESH_INTERVAL_MS = TimeUnit.MINUTES.toMillis(DevFlags.STRESS_TEST_MODE ? 4 : 4);

    private final KeyRing keyRing;
    private final User user;
    private final P2PService p2PService;
    private final WalletService walletService;
    private final TradeWalletService tradeWalletService;
    private final OfferBookService offerBookService;
    private final ClosedTradableManager closedTradableManager;
    private Preferences preferences;

    private final TradableList<OpenOffer> openOffers;
    private final Storage<TradableList<OpenOffer>> openOffersStorage;
    private boolean stopped;
    private Timer periodicRepublishOffersTimer, periodicRefreshOffersTimer, retryRepublishOffersTimer;


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
                            PriceFeedService priceFeedService,
                            Preferences preferences,
                            @Named(Storage.DIR_KEY) File storageDir) {
        this.keyRing = keyRing;
        this.user = user;
        this.p2PService = p2PService;
        this.walletService = walletService;
        this.tradeWalletService = tradeWalletService;
        this.offerBookService = offerBookService;
        this.closedTradableManager = closedTradableManager;
        this.preferences = preferences;

        openOffersStorage = new Storage<>(storageDir);
        openOffers = new TradableList<>(openOffersStorage, "OpenOffers");
        openOffers.forEach(e -> e.getOffer().setPriceFeedService(priceFeedService));

        // In case the app did get killed the shutDown from the modules is not called, so we use a shutdown hook
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            UserThread.execute(OpenOfferManager.this::shutDown);
        }, "OpenOfferManager.ShutDownHook"));
    }

    public void onAllServicesInitialized() {
        p2PService.addDecryptedDirectMessageListener(this);

        if (p2PService.isBootstrapped())
            onBootstrapComplete();
        else
            p2PService.addP2PServiceListener(new BootstrapListener() {
                @Override
                public void onBootstrapComplete() {
                    OpenOfferManager.this.onBootstrapComplete();
                }
            });
    }

    @SuppressWarnings("WeakerAccess")
    public void shutDown() {
        shutDown(null);
    }

    public void shutDown(@Nullable Runnable completeHandler) {
        stopped = true;
        p2PService.getPeerManager().removeListener(this);
        p2PService.removeDecryptedDirectMessageListener(this);

        stopPeriodicRefreshOffersTimer();
        stopPeriodicRepublishOffersTimer();
        stopRetryRepublishOffersTimer();

        log.debug("remove all open offers at shutDown");
        // we remove own offers from offerbook when we go offline
        // Normally we use a delay for broadcasting to the peers, but at shut down we want to get it fast out

        final int size = openOffers.size();
        if (offerBookService.isBootstrapped()) {
            openOffers.forEach(openOffer -> offerBookService.removeOfferAtShutDown(openOffer.getOffer()));
            if (completeHandler != null)
                UserThread.runAfter(completeHandler::run, size * 200 + 500, TimeUnit.MILLISECONDS);
        } else {
            if (completeHandler != null)
                completeHandler.run();
        }
    }

    public void removeAllOpenOffers(@Nullable Runnable completeHandler) {
        final int size = openOffers.size();
        List<OpenOffer> openOffersList = new ArrayList<>(openOffers);
        openOffersList.forEach(openOffer -> removeOpenOffer(openOffer, () -> {
        }, errorMessage -> {
        }));
        if (completeHandler != null)
            UserThread.runAfter(completeHandler::run, size * 200 + 500, TimeUnit.MILLISECONDS);
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
        stopped = false;

        // Republish means we send the complete offer object
        republishOffers();
        startPeriodicRepublishOffersTimer();

        // Refresh is started once we get a success from republish

        // We republish after a bit as it might be that our connected node still has the offer in the data map
        // but other peers have it already removed because of expired TTL.
        // Those other not directly connected peers would not get the broadcast of the new offer, as the first 
        // connected peer (seed node) does nto broadcast if it has the data in the map.
        // To update quickly to the whole network we repeat the republishOffers call after a few seconds when we 
        // are better connected to the network. There is no guarantee that all peers will receive it but we have
        // also our periodic timer, so after that longer interval the offer should be available to all peers.
        if (retryRepublishOffersTimer == null)
            retryRepublishOffersTimer = UserThread.runAfter(OpenOfferManager.this::republishOffers,
                    REPUBLISH_AGAIN_AT_STARTUP_DELAY_SEC);

        p2PService.getPeerManager().addListener(this);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // PeerManager.Listener implementation
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void onAllConnectionsLost() {
        log.info("onAllConnectionsLost");
        stopped = true;
        stopPeriodicRefreshOffersTimer();
        stopPeriodicRepublishOffersTimer();
        stopRetryRepublishOffersTimer();

        restart();
    }

    @Override
    public void onNewConnectionAfterAllConnectionsLost() {
        log.info("onNewConnectionAfterAllConnectionsLost");
        stopped = false;
        restart();
    }

    @Override
    public void onAwakeFromStandby() {
        log.info("onAwakeFromStandby");
        stopped = false;
        if (!p2PService.getNetworkNode().getAllConnections().isEmpty())
            restart();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // API
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void placeOffer(Offer offer, Coin reservedFundsForOffer, boolean useSavingsWallet, TransactionResultHandler resultHandler) {
        PlaceOfferModel model = new PlaceOfferModel(offer, reservedFundsForOffer, useSavingsWallet, walletService, tradeWalletService, offerBookService, user);
        PlaceOfferProtocol placeOfferProtocol = new PlaceOfferProtocol(
                model,
                transaction -> {
                    OpenOffer openOffer = new OpenOffer(offer, openOffersStorage);
                    openOffers.add(openOffer);
                    openOffersStorage.queueUpForSave();
                    resultHandler.handleResult(transaction);
                    if (!stopped) {
                        startPeriodicRepublishOffersTimer();
                        startPeriodicRefreshOffersTimer();
                    } else {
                        log.debug("We have stopped already. We ignore that placeOfferProtocol.placeOffer.onResult call.");
                    }
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
                    walletService.swapTradeEntryToAvailableEntry(offer.getId(), AddressEntry.Context.OFFER_FUNDING);
                    walletService.swapTradeEntryToAvailableEntry(offer.getId(), AddressEntry.Context.RESERVED_FOR_TRADE);
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
            AvailabilityResult availabilityResult;
            if (openOfferOptional.isPresent()) {
                if (openOfferOptional.get().getState() == OpenOffer.State.AVAILABLE) {
                    final Offer offer = openOfferOptional.get().getOffer();
                    if (!preferences.getIgnoreTradersList().stream().filter(i -> i.equals(offer.getOffererNodeAddress().getHostNameWithoutPostFix())).findAny().isPresent()) {
                        availabilityResult = AvailabilityResult.AVAILABLE;
                        List<NodeAddress> acceptedArbitrators = user.getAcceptedArbitratorAddresses();
                        if (acceptedArbitrators != null && !acceptedArbitrators.isEmpty()) {
                            // We need to be backward compatible. takersTradePrice was not used before 0.4.9.
                            if (message.takersTradePrice > 0) {
                                // Check also tradePrice to avoid failures after taker fee is paid caused by a too big difference 
                                // in trade price between the peers. Also here poor connectivity might cause market price API connection 
                                // losses and therefore an outdated market price.
                                try {
                                    offer.checkTradePriceTolerance(message.takersTradePrice);
                                } catch (TradePriceOutOfToleranceException e) {
                                    log.warn("Trade price check failed because takers price is outside out tolerance.");
                                    availabilityResult = AvailabilityResult.PRICE_OUT_OF_TOLERANCE;
                                } catch (MarketPriceNotAvailableException e) {
                                    log.warn(e.getMessage());
                                    availabilityResult = AvailabilityResult.MARKET_PRICE_NOT_AVAILABLE;
                                } catch (Throwable e) {
                                    log.warn("Trade price check failed. " + e.getMessage());
                                    availabilityResult = AvailabilityResult.UNKNOWN_FAILURE;
                                }
                            }
                        } else {
                            log.warn("acceptedArbitrators is null or empty: acceptedArbitrators=" + acceptedArbitrators);
                            availabilityResult = AvailabilityResult.NO_ARBITRATORS;
                        }
                    } else {
                        availabilityResult = AvailabilityResult.USER_IGNORED;
                    }
                } else {
                    availabilityResult = AvailabilityResult.OFFER_TAKEN;
                }
            } else {
                log.warn("handleOfferAvailabilityRequest: openOffer not found. That should never happen.");
                availabilityResult = AvailabilityResult.OFFER_TAKEN;
            }

            try {
                p2PService.sendEncryptedDirectMessage(sender,
                        message.getPubKeyRing(),
                        new OfferAvailabilityResponse(message.offerId, availabilityResult),
                        new SendDirectMessageListener() {
                            @Override
                            public void onArrived() {
                                log.trace("OfferAvailabilityResponse successfully arrived at peer");
                            }

                            @Override
                            public void onFault() {
                                log.debug("Sending OfferAvailabilityResponse failed.");
                            }
                        });
            } catch (Throwable t) {
                t.printStackTrace();
                log.debug("Exception at handleRequestIsOfferAvailableMessage " + t.getMessage());
            }
        } else {
            log.debug("We have stopped already. We ignore that handleOfferAvailabilityRequest call.");
        }
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // RepublishOffers, refreshOffers
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void republishOffers() {
        int size = openOffers.size();
        final ArrayList<OpenOffer> openOffersList = new ArrayList<>(openOffers);
        Log.traceCall("Number of offer for republish: " + size);
        if (!stopped) {
            stopPeriodicRefreshOffersTimer();
            for (int i = 0; i < size; i++) {
                // we delay to avoid reaching throttle limits

                long delay = 700;
                final long minDelay = (i + 1) * delay;
                final long maxDelay = (i + 2) * delay;
                final OpenOffer openOffer = openOffersList.get(i);
                UserThread.runAfterRandomDelay(() -> {
                    if (openOffers.contains(openOffer))
                        republishOffer(openOffer);
                }, minDelay, maxDelay, TimeUnit.MILLISECONDS);
            }
        } else {
            log.debug("We have stopped already. We ignore that republishOffers call.");
        }
    }

    private void republishOffer(OpenOffer openOffer) {
        offerBookService.addOffer(openOffer.getOffer(),
                () -> {
                    if (!stopped) {
                        log.debug("Successful added offer to P2P network");
                        // Refresh means we send only the dat needed to refresh the TTL (hash, signature and sequence nr.)
                        if (periodicRefreshOffersTimer == null)
                            startPeriodicRefreshOffersTimer();
                    } else {
                        log.debug("We have stopped already. We ignore that offerBookService.republishOffers.onSuccess call.");
                    }
                },
                errorMessage -> {
                    if (!stopped) {
                        log.error("Add offer to P2P network failed. " + errorMessage);
                        stopRetryRepublishOffersTimer();
                        retryRepublishOffersTimer = UserThread.runAfter(OpenOfferManager.this::republishOffers,
                                RETRY_REPUBLISH_DELAY_SEC);
                    } else {
                        log.debug("We have stopped already. We ignore that offerBookService.republishOffers.onFault call.");
                    }
                });
        openOffer.setStorage(openOffersStorage);
    }

    private void startPeriodicRepublishOffersTimer() {
        Log.traceCall();
        stopped = false;
        if (periodicRepublishOffersTimer == null)
            periodicRepublishOffersTimer = UserThread.runPeriodically(() -> {
                        if (!stopped) {
                            republishOffers();
                        } else {
                            log.debug("We have stopped already. We ignore that periodicRepublishOffersTimer.run call.");
                        }
                    },
                    REPUBLISH_INTERVAL_MS,
                    TimeUnit.MILLISECONDS);
        else
            log.trace("periodicRepublishOffersTimer already stated");
    }

    private void startPeriodicRefreshOffersTimer() {
        Log.traceCall();
        stopped = false;
        // refresh sufficiently before offer would expire
        if (periodicRefreshOffersTimer == null)
            periodicRefreshOffersTimer = UserThread.runPeriodically(() -> {
                        if (!stopped) {
                            int size = openOffers.size();
                            Log.traceCall("Number of offer for refresh: " + size);

                            //we clone our list as openOffers might change during our delayed call
                            final ArrayList<OpenOffer> openOffersList = new ArrayList<>(openOffers);
                            for (int i = 0; i < size; i++) {
                                // we delay to avoid reaching throttle limits
                                // roughly 4 offers per second

                                long delay = 300;
                                final long minDelay = (i + 1) * delay;
                                final long maxDelay = (i + 2) * delay;
                                final OpenOffer openOffer = openOffersList.get(i);
                                UserThread.runAfterRandomDelay(() -> {
                                    // we need to check if in the meantime the offer has been removed
                                    if (openOffers.contains(openOffer))
                                        refreshOffer(openOffer);
                                }, minDelay, maxDelay, TimeUnit.MILLISECONDS);
                            }
                        } else {
                            log.debug("We have stopped already. We ignore that periodicRefreshOffersTimer.run call.");
                        }
                    },
                    REFRESH_INTERVAL_MS,
                    TimeUnit.MILLISECONDS);
        else
            log.trace("periodicRefreshOffersTimer already stated");
    }

    private void refreshOffer(OpenOffer openOffer) {
        offerBookService.refreshTTL(openOffer.getOffer(),
                () -> log.debug("Successful refreshed TTL for offer"),
                errorMessage -> log.warn(errorMessage));
    }

    private void restart() {
        log.debug("Restart after connection loss");
        if (retryRepublishOffersTimer == null)
            retryRepublishOffersTimer = UserThread.runAfter(() -> {
                stopped = false;
                stopRetryRepublishOffersTimer();
                republishOffers();
            }, RETRY_REPUBLISH_DELAY_SEC);

        startPeriodicRepublishOffersTimer();
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

    private void stopRetryRepublishOffersTimer() {
        if (retryRepublishOffersTimer != null) {
            retryRepublishOffersTimer.stop();
            retryRepublishOffersTimer = null;
        }
    }
}
