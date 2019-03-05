/*
 * This file is part of Bisq.
 *
 * Bisq is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * Bisq is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Bisq. If not, see <http://www.gnu.org/licenses/>.
 */

package bisq.core.offer;

import bisq.core.arbitration.ArbitratorManager;
import bisq.core.btc.wallet.BsqWalletService;
import bisq.core.btc.wallet.BtcWalletService;
import bisq.core.btc.wallet.TradeWalletService;
import bisq.core.exceptions.TradePriceOutOfToleranceException;
import bisq.core.offer.availability.ArbitratorSelection;
import bisq.core.offer.messages.OfferAvailabilityRequest;
import bisq.core.offer.messages.OfferAvailabilityResponse;
import bisq.core.offer.placeoffer.PlaceOfferModel;
import bisq.core.offer.placeoffer.PlaceOfferProtocol;
import bisq.core.provider.price.PriceFeedService;
import bisq.core.trade.TradableList;
import bisq.core.trade.closed.ClosedTradableManager;
import bisq.core.trade.handlers.TransactionResultHandler;
import bisq.core.trade.statistics.TradeStatisticsManager;
import bisq.core.user.Preferences;
import bisq.core.user.User;
import bisq.core.util.Validator;

import bisq.network.p2p.AckMessage;
import bisq.network.p2p.AckMessageSourceType;
import bisq.network.p2p.BootstrapListener;
import bisq.network.p2p.DecryptedDirectMessageListener;
import bisq.network.p2p.DecryptedMessageWithPubKey;
import bisq.network.p2p.NodeAddress;
import bisq.network.p2p.P2PService;
import bisq.network.p2p.SendDirectMessageListener;
import bisq.network.p2p.peers.PeerManager;

import bisq.common.Timer;
import bisq.common.UserThread;
import bisq.common.crypto.KeyRing;
import bisq.common.crypto.PubKeyRing;
import bisq.common.handlers.ErrorMessageHandler;
import bisq.common.handlers.ResultHandler;
import bisq.common.proto.network.NetworkEnvelope;
import bisq.common.proto.persistable.PersistedDataHost;
import bisq.common.proto.persistable.PersistenceProtoResolver;
import bisq.common.storage.Storage;

import org.bitcoinj.core.Coin;

import javax.inject.Inject;
import javax.inject.Named;

import javafx.collections.ObservableList;

import java.io.File;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.jetbrains.annotations.NotNull;

import javax.annotation.Nullable;

import static com.google.common.base.Preconditions.checkNotNull;

public class OpenOfferManager implements PeerManager.Listener, DecryptedDirectMessageListener, PersistedDataHost {
    private static final Logger log = LoggerFactory.getLogger(OpenOfferManager.class);

    private static final long RETRY_REPUBLISH_DELAY_SEC = 10;
    private static final long REPUBLISH_AGAIN_AT_STARTUP_DELAY_SEC = 30;
    private static final long REPUBLISH_INTERVAL_MS = TimeUnit.MINUTES.toMillis(40);
    private static final long REFRESH_INTERVAL_MS = TimeUnit.MINUTES.toMillis(6);

    private final KeyRing keyRing;
    private final User user;
    private final P2PService p2PService;
    private final BtcWalletService btcWalletService;
    private final TradeWalletService tradeWalletService;
    private final BsqWalletService bsqWalletService;
    private final OfferBookService offerBookService;
    private final ClosedTradableManager closedTradableManager;
    private final PriceFeedService priceFeedService;
    private final Preferences preferences;
    private final TradeStatisticsManager tradeStatisticsManager;
    private final ArbitratorManager arbitratorManager;
    private final Storage<TradableList<OpenOffer>> openOfferTradableListStorage;
    private final Map<String, OpenOffer> offersToBeEdited = new HashMap<>();
    private boolean stopped;
    private Timer periodicRepublishOffersTimer, periodicRefreshOffersTimer, retryRepublishOffersTimer;
    private TradableList<OpenOffer> openOffers;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor, Initialization
    ///////////////////////////////////////////////////////////////////////////////////////////

    @SuppressWarnings("WeakerAccess")
    @Inject
    public OpenOfferManager(KeyRing keyRing,
                            User user,
                            P2PService p2PService,
                            BtcWalletService btcWalletService,
                            TradeWalletService tradeWalletService,
                            BsqWalletService bsqWalletService,
                            OfferBookService offerBookService,
                            ClosedTradableManager closedTradableManager,
                            PriceFeedService priceFeedService,
                            Preferences preferences,
                            PersistenceProtoResolver persistenceProtoResolver,
                            TradeStatisticsManager tradeStatisticsManager,
                            ArbitratorManager arbitratorManager,
                            @Named(Storage.STORAGE_DIR) File storageDir) {
        this.keyRing = keyRing;
        this.user = user;
        this.p2PService = p2PService;
        this.btcWalletService = btcWalletService;
        this.tradeWalletService = tradeWalletService;
        this.bsqWalletService = bsqWalletService;
        this.offerBookService = offerBookService;
        this.closedTradableManager = closedTradableManager;
        this.priceFeedService = priceFeedService;
        this.preferences = preferences;
        this.tradeStatisticsManager = tradeStatisticsManager;
        this.arbitratorManager = arbitratorManager;

        openOfferTradableListStorage = new Storage<>(storageDir, persistenceProtoResolver);

        // In case the app did get killed the shutDown from the modules is not called, so we use a shutdown hook
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            UserThread.execute(OpenOfferManager.this::shutDown);
        }, "OpenOfferManager.ShutDownHook"));
    }

    @Override
    public void readPersisted() {
        openOffers = new TradableList<>(openOfferTradableListStorage, "OpenOffers");
        openOffers.forEach(e -> e.getOffer().setPriceFeedService(priceFeedService));
    }

    public void onAllServicesInitialized() {
        p2PService.addDecryptedDirectMessageListener(this);

        if (p2PService.isBootstrapped()) {
            onBootstrapComplete();
        } else {
            p2PService.addP2PServiceListener(new BootstrapListener() {
                @Override
                public void onUpdatedDataReceived() {
                    onBootstrapComplete();
                }
            });
        }

        cleanUpAddressEntries();
    }

    private void cleanUpAddressEntries() {
        Set<String> openOffersIdSet = openOffers.getList().stream().map(OpenOffer::getId).collect(Collectors.toSet());
        btcWalletService.getAddressEntriesForOpenOffer().stream()
                .filter(e -> !openOffersIdSet.contains(e.getOfferId()))
                .forEach(e -> {
                    log.warn("We found an outdated addressEntry for openOffer {} (openOffers does not contain that " +
                                    "offer), offers.size={}",
                            e.getOfferId(), openOffers.size());
                    btcWalletService.resetAddressEntriesForOpenOffer(e.getOfferId());
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

        final int size = openOffers != null ? openOffers.size() : 0;
        if (offerBookService.isBootstrapped() && size > 0) {
            openOffers.forEach(openOffer -> offerBookService.removeOfferAtShutDown(openOffer.getOffer().getOfferPayload()));
            if (completeHandler != null)
                UserThread.runAfter(completeHandler::run, size * 200 + 500, TimeUnit.MILLISECONDS);
        } else {
            if (completeHandler != null)
                completeHandler.run();
        }
    }

    public void removeAllOpenOffers(@Nullable Runnable completeHandler) {
        removeOpenOffers(getObservableList(), completeHandler);
    }

    public void removeOpenOffers(List<OpenOffer> openOffers, @Nullable Runnable completeHandler) {
        final int size = openOffers.size();
        // Copy list as we remove in the loop
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
    public void onDirectMessage(DecryptedMessageWithPubKey decryptedMessageWithPubKey, NodeAddress peerNodeAddress) {
        // Handler for incoming offer availability requests
        // We get an encrypted message but don't do the signature check as we don't know the peer yet.
        // A basic sig check is in done also at decryption time
        NetworkEnvelope networkEnvelope = decryptedMessageWithPubKey.getNetworkEnvelope();
        if (networkEnvelope instanceof OfferAvailabilityRequest) {
            handleOfferAvailabilityRequest((OfferAvailabilityRequest) networkEnvelope, peerNodeAddress);
        } else if (networkEnvelope instanceof AckMessage) {
            AckMessage ackMessage = (AckMessage) networkEnvelope;
            if (ackMessage.getSourceType() == AckMessageSourceType.OFFER_MESSAGE) {
                if (ackMessage.isSuccess()) {
                    log.info("Received AckMessage for {} with offerId {} and uid {}",
                            ackMessage.getSourceMsgClassName(), ackMessage.getSourceId(), ackMessage.getSourceUid());
                } else {
                    log.warn("Received AckMessage with error state for {} with offerId {} and errorMessage={}",
                            ackMessage.getSourceMsgClassName(), ackMessage.getSourceId(), ackMessage.getErrorMessage());
                }
            }
        }
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // BootstrapListener delegate
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void onBootstrapComplete() {
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

    public void placeOffer(Offer offer,
                           Coin reservedFundsForOffer,
                           boolean useSavingsWallet,
                           TransactionResultHandler resultHandler,
                           ErrorMessageHandler errorMessageHandler) {
        PlaceOfferModel model = new PlaceOfferModel(offer,
                reservedFundsForOffer,
                useSavingsWallet,
                btcWalletService,
                tradeWalletService,
                bsqWalletService,
                offerBookService,
                arbitratorManager,
                tradeStatisticsManager,
                user);
        PlaceOfferProtocol placeOfferProtocol = new PlaceOfferProtocol(
                model,
                transaction -> {
                    OpenOffer openOffer = new OpenOffer(offer, openOfferTradableListStorage);
                    openOffers.add(openOffer);
                    openOfferTradableListStorage.queueUpForSave();
                    resultHandler.handleResult(transaction);
                    if (!stopped) {
                        startPeriodicRepublishOffersTimer();
                        startPeriodicRefreshOffersTimer();
                    } else {
                        log.debug("We have stopped already. We ignore that placeOfferProtocol.placeOffer.onResult call.");
                    }
                },
                errorMessageHandler::handleErrorMessage
        );
        placeOfferProtocol.placeOffer();
    }

    // Remove from offerbook
    public void removeOffer(Offer offer, ResultHandler resultHandler, ErrorMessageHandler errorMessageHandler) {
        Optional<OpenOffer> openOfferOptional = getOpenOfferById(offer.getId());
        if (openOfferOptional.isPresent()) {
            removeOpenOffer(openOfferOptional.get(), resultHandler, errorMessageHandler);
        } else {
            log.warn("Offer was not found in our list of open offers. We still try to remove it from the offerbook.");
            errorMessageHandler.handleErrorMessage("Offer was not found in our list of open offers. " +
                    "We still try to remove it from the offerbook.");
            offerBookService.removeOffer(offer.getOfferPayload(),
                    () -> offer.setState(Offer.State.REMOVED),
                    null);
        }
    }

    public void activateOpenOffer(OpenOffer openOffer, ResultHandler resultHandler, ErrorMessageHandler errorMessageHandler) {
        if (!offersToBeEdited.containsKey(openOffer.getId())) {
            Offer offer = openOffer.getOffer();
            openOffer.setStorage(openOfferTradableListStorage);
            offerBookService.activateOffer(offer,
                    () -> {
                        openOffer.setState(OpenOffer.State.AVAILABLE);
                        log.debug("activateOpenOffer, offerId={}", offer.getId());
                        resultHandler.handleResult();
                    },
                    errorMessageHandler);
        } else {
            errorMessageHandler.handleErrorMessage("You can't activate an offer that is currently edited.");
        }
    }

    public void deactivateOpenOffer(OpenOffer openOffer, ResultHandler resultHandler, ErrorMessageHandler errorMessageHandler) {
        Offer offer = openOffer.getOffer();
        openOffer.setStorage(openOfferTradableListStorage);
        offerBookService.deactivateOffer(offer.getOfferPayload(),
                () -> {
                    openOffer.setState(OpenOffer.State.DEACTIVATED);
                    log.debug("deactivateOpenOffer, offerId={}", offer.getId());
                    resultHandler.handleResult();
                },
                errorMessageHandler);
    }

    public void removeOpenOffer(OpenOffer openOffer, ResultHandler resultHandler, ErrorMessageHandler errorMessageHandler) {
        if (!offersToBeEdited.containsKey(openOffer.getId())) {
            Offer offer = openOffer.getOffer();
            if (openOffer.isDeactivated()) {
                openOffer.setStorage(openOfferTradableListStorage);
                onRemoved(openOffer, resultHandler, offer);
            } else {
                offerBookService.removeOffer(offer.getOfferPayload(),
                        () -> onRemoved(openOffer, resultHandler, offer),
                        errorMessageHandler);
            }
        } else {
            errorMessageHandler.handleErrorMessage("You can't remove an offer that is currently edited.");
        }
    }

    public void editOpenOfferStart(OpenOffer openOffer, ResultHandler resultHandler, ErrorMessageHandler errorMessageHandler) {
        if (offersToBeEdited.containsKey(openOffer.getId())) {
            log.warn("editOpenOfferStart called for an offer which is already in edit mode.");
            resultHandler.handleResult();
            return;
        }

        offersToBeEdited.put(openOffer.getId(), openOffer);

        if (openOffer.isDeactivated()) {
            resultHandler.handleResult();
        } else {
            deactivateOpenOffer(openOffer,
                    () -> resultHandler.handleResult(),
                    errorMessage -> {
                        offersToBeEdited.remove(openOffer.getId());
                        errorMessageHandler.handleErrorMessage(errorMessage);
                    });
        }
    }

    public void editOpenOfferPublish(Offer editedOffer, OpenOffer.State originalState, ResultHandler resultHandler, ErrorMessageHandler errorMessageHandler) {
        Optional<OpenOffer> openOfferOptional = getOpenOfferById(editedOffer.getId());

        if (openOfferOptional.isPresent()) {
            final OpenOffer openOffer = openOfferOptional.get();

            openOffer.setStorage(openOfferTradableListStorage);

            openOffer.getOffer().setState(Offer.State.REMOVED);
            openOffer.setState(OpenOffer.State.CANCELED);
            openOffers.remove(openOffer);

            final OpenOffer editedOpenOffer = new OpenOffer(editedOffer, openOfferTradableListStorage);
            editedOpenOffer.setState(originalState);

            openOffers.add(editedOpenOffer);

            if (!editedOpenOffer.isDeactivated())
                republishOffer(editedOpenOffer);

            offersToBeEdited.remove(openOffer.getId());

            resultHandler.handleResult();
        } else {
            errorMessageHandler.handleErrorMessage("There is no offer with this id existing to be published.");
        }
    }

    public void editOpenOfferCancel(OpenOffer openOffer, OpenOffer.State originalState, ResultHandler resultHandler, ErrorMessageHandler errorMessageHandler) {
        if (offersToBeEdited.containsKey(openOffer.getId())) {
            offersToBeEdited.remove(openOffer.getId());
            if (originalState.equals(OpenOffer.State.AVAILABLE)) {
                activateOpenOffer(openOffer, () -> {
                    resultHandler.handleResult();
                }, errorMessageHandler);
            } else {
                resultHandler.handleResult();
            }
        } else {
            errorMessageHandler.handleErrorMessage("Editing of offer can't be canceled as it is not edited.");
        }
    }

    private void onRemoved(@NotNull OpenOffer openOffer, ResultHandler resultHandler, Offer offer) {
        offer.setState(Offer.State.REMOVED);
        openOffer.setState(OpenOffer.State.CANCELED);
        openOffers.remove(openOffer);
        closedTradableManager.add(openOffer);
        log.info("onRemoved offerId={}", offer.getId());
        btcWalletService.resetAddressEntriesForOpenOffer(offer.getId());
        resultHandler.handleResult();
    }

    // Close openOffer after deposit published
    public void closeOpenOffer(Offer offer) {
        getOpenOfferById(offer.getId()).ifPresent(openOffer -> {
            openOffers.remove(openOffer);
            openOffer.setState(OpenOffer.State.CLOSED);
            offerBookService.removeOffer(openOffer.getOffer().getOfferPayload(),
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

    public ObservableList<OpenOffer> getObservableList() {
        return openOffers.getList();
    }

    public Optional<OpenOffer> getOpenOfferById(String offerId) {
        return openOffers.stream().filter(e -> e.getId().equals(offerId)).findFirst();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // OfferPayload Availability
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void handleOfferAvailabilityRequest(OfferAvailabilityRequest request, NodeAddress peer) {
        log.info("Received OfferAvailabilityRequest from {} with offerId {} and uid {}",
                peer, request.getOfferId(), request.getUid());

        boolean result = false;
        String errorMessage = null;

        if (!p2PService.isBootstrapped()) {
            errorMessage = "We got a handleOfferAvailabilityRequest but we have not bootstrapped yet.";
            log.info(errorMessage);
            sendAckMessage(request, peer, false, errorMessage);
            return;
        }

        if (stopped) {
            errorMessage = "We have stopped already. We ignore that handleOfferAvailabilityRequest call.";
            log.debug(errorMessage);
            sendAckMessage(request, peer, false, errorMessage);
            return;
        }

        try {
            Validator.nonEmptyStringOf(request.offerId);
            checkNotNull(request.getPubKeyRing());
        } catch (Throwable t) {
            errorMessage = "Message validation failed. Error=" + t.toString() + ", Message=" + request.toString();
            log.warn(errorMessage);
            sendAckMessage(request, peer, false, errorMessage);
            return;
        }

        try {
            Optional<OpenOffer> openOfferOptional = getOpenOfferById(request.offerId);
            AvailabilityResult availabilityResult;
            NodeAddress arbitratorNodeAddress = null;
            if (openOfferOptional.isPresent()) {
                OpenOffer openOffer = openOfferOptional.get();
                if (openOffer.getState() == OpenOffer.State.AVAILABLE) {
                    Offer offer = openOffer.getOffer();
                    if (preferences.getIgnoreTradersList().stream().noneMatch(hostName -> hostName.equals(peer.getHostName()))) {
                        availabilityResult = AvailabilityResult.AVAILABLE;

                        List<NodeAddress> acceptedArbitrators = user.getAcceptedArbitratorAddresses();
                        if (acceptedArbitrators != null && !acceptedArbitrators.isEmpty()) {
                            arbitratorNodeAddress = ArbitratorSelection.getLeastUsedArbitrator(tradeStatisticsManager, arbitratorManager).getNodeAddress();
                            openOffer.setArbitratorNodeAddress(arbitratorNodeAddress);

                            // Check also tradePrice to avoid failures after taker fee is paid caused by a too big difference
                            // in trade price between the peers. Also here poor connectivity might cause market price API connection
                            // losses and therefore an outdated market price.
                            try {
                                offer.checkTradePriceTolerance(request.getTakersTradePrice());
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

            OfferAvailabilityResponse offerAvailabilityResponse = new OfferAvailabilityResponse(request.offerId, availabilityResult, arbitratorNodeAddress);
            log.info("Send {} with offerId {} and uid {} to peer {}",
                    offerAvailabilityResponse.getClass().getSimpleName(), offerAvailabilityResponse.getOfferId(),
                    offerAvailabilityResponse.getUid(), peer);
            p2PService.sendEncryptedDirectMessage(peer,
                    request.getPubKeyRing(),
                    offerAvailabilityResponse,
                    new SendDirectMessageListener() {
                        @Override
                        public void onArrived() {
                            log.info("{} arrived at peer: offerId={}; uid={}",
                                    offerAvailabilityResponse.getClass().getSimpleName(), offerAvailabilityResponse.getOfferId(), offerAvailabilityResponse.getUid());
                        }

                        @Override
                        public void onFault(String errorMessage) {
                            log.error("Sending {} failed: uid={}; peer={}; error={}",
                                    offerAvailabilityResponse.getClass().getSimpleName(), offerAvailabilityResponse.getUid(),
                                    peer, errorMessage);
                        }
                    });
            result = true;
        } catch (Throwable t) {
            errorMessage = "Exception at handleRequestIsOfferAvailableMessage " + t.getMessage();
            log.error(errorMessage);
            t.printStackTrace();
        } finally {
            sendAckMessage(request, peer, result, errorMessage);
        }
    }

    private void sendAckMessage(OfferAvailabilityRequest message, NodeAddress sender, boolean result, String errorMessage) {
        String offerId = message.getOfferId();
        String sourceUid = message.getUid();
        AckMessage ackMessage = new AckMessage(p2PService.getNetworkNode().getNodeAddress(),
                AckMessageSourceType.OFFER_MESSAGE,
                message.getClass().getSimpleName(),
                sourceUid,
                offerId,
                result,
                errorMessage);

        final NodeAddress takersNodeAddress = sender;
        PubKeyRing takersPubKeyRing = message.getPubKeyRing();
        log.info("Send AckMessage for OfferAvailabilityRequest to peer {} with offerId {} and sourceUid {}",
                takersNodeAddress, offerId, ackMessage.getSourceUid());
        p2PService.sendEncryptedDirectMessage(
                takersNodeAddress,
                takersPubKeyRing,
                ackMessage,
                new SendDirectMessageListener() {
                    @Override
                    public void onArrived() {
                        log.info("AckMessage for OfferAvailabilityRequest arrived at takersNodeAddress {}. offerId={}, sourceUid={}",
                                takersNodeAddress, offerId, ackMessage.getSourceUid());
                    }

                    @Override
                    public void onFault(String errorMessage) {
                        log.error("AckMessage for OfferAvailabilityRequest failed. AckMessage={}, takersNodeAddress={}, errorMessage={}",
                                ackMessage, takersNodeAddress, errorMessage);
                    }
                }
        );
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // RepublishOffers, refreshOffers
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void republishOffers() {
        int size = openOffers.size();
        final ArrayList<OpenOffer> openOffersList = new ArrayList<>(openOffers.getList());
        if (!stopped) {
            stopPeriodicRefreshOffersTimer();
            for (int i = 0; i < size; i++) {
                // we delay to avoid reaching throttle limits

                long delay = 700;
                final long minDelay = (i + 1) * delay;
                final long maxDelay = (i + 2) * delay;
                final OpenOffer openOffer = openOffersList.get(i);
                UserThread.runAfterRandomDelay(() -> {
                    if (openOffers.contains(openOffer)) {
                        String id = openOffer.getId();
                        if (id != null && !openOffer.isDeactivated())
                            republishOffer(openOffer);
                    }

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
                        // Refresh means we send only the dat needed to refresh the TTL (hash, signature and sequence no.)
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
        openOffer.setStorage(openOfferTradableListStorage);
    }

    private void startPeriodicRepublishOffersTimer() {
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
        stopped = false;
        // refresh sufficiently before offer would expire
        if (periodicRefreshOffersTimer == null)
            periodicRefreshOffersTimer = UserThread.runPeriodically(() -> {
                        if (!stopped) {
                            int size = openOffers.size();
                            //we clone our list as openOffers might change during our delayed call
                            final ArrayList<OpenOffer> openOffersList = new ArrayList<>(openOffers.getList());
                            for (int i = 0; i < size; i++) {
                                // we delay to avoid reaching throttle limits
                                // roughly 4 offers per second

                                long delay = 300;
                                final long minDelay = (i + 1) * delay;
                                final long maxDelay = (i + 2) * delay;
                                final OpenOffer openOffer = openOffersList.get(i);
                                UserThread.runAfterRandomDelay(() -> {
                                    // we need to check if in the meantime the offer has been removed
                                    if (openOffers.contains(openOffer) && !openOffer.isDeactivated())
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
        offerBookService.refreshTTL(openOffer.getOffer().getOfferPayload(),
                () -> log.debug("Successful refreshed TTL for offer"),
                log::warn);
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
