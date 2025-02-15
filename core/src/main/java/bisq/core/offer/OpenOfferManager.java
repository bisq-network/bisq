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

import bisq.core.api.CoreContext;
import bisq.core.btc.wallet.BsqWalletService;
import bisq.core.btc.wallet.BtcWalletService;
import bisq.core.btc.wallet.TradeWalletService;
import bisq.core.dao.DaoFacade;
import bisq.core.dao.burningman.BtcFeeReceiverService;
import bisq.core.dao.burningman.DelayedPayoutTxReceiverService;
import bisq.core.dao.state.DaoStateListener;
import bisq.core.dao.state.DaoStateService;
import bisq.core.dao.state.model.blockchain.Block;
import bisq.core.exceptions.TradePriceOutOfToleranceException;
import bisq.core.filter.FilterManager;
import bisq.core.locale.Res;
import bisq.core.offer.availability.AvailabilityResult;
import bisq.core.offer.availability.DisputeAgentSelection;
import bisq.core.offer.availability.messages.OfferAvailabilityRequest;
import bisq.core.offer.availability.messages.OfferAvailabilityResponse;
import bisq.core.offer.bisq_v1.CreateOfferService;
import bisq.core.offer.bisq_v1.MarketPriceNotAvailableException;
import bisq.core.offer.bisq_v1.OfferPayload;
import bisq.core.offer.placeoffer.bisq_v1.PlaceOfferModel;
import bisq.core.offer.placeoffer.bisq_v1.PlaceOfferProtocol;
import bisq.core.provider.mempool.FeeValidationStatus;
import bisq.core.provider.price.PriceFeedService;
import bisq.core.support.dispute.arbitration.arbitrator.ArbitratorManager;
import bisq.core.support.dispute.mediation.mediator.MediatorManager;
import bisq.core.support.dispute.refund.refundagent.RefundAgentManager;
import bisq.core.trade.ClosedTradableManager;
import bisq.core.trade.bisq_v1.TransactionResultHandler;
import bisq.core.trade.model.TradableList;
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
import bisq.network.p2p.peers.Broadcaster;
import bisq.network.p2p.peers.PeerManager;
import bisq.network.utils.Utils;

import bisq.common.Timer;
import bisq.common.UserThread;
import bisq.common.app.Capabilities;
import bisq.common.app.Capability;
import bisq.common.app.Version;
import bisq.common.crypto.KeyRing;
import bisq.common.crypto.PubKeyRing;
import bisq.common.handlers.ErrorMessageHandler;
import bisq.common.handlers.ResultHandler;
import bisq.common.persistence.PersistenceManager;
import bisq.common.proto.network.NetworkEnvelope;
import bisq.common.proto.persistable.PersistedDataHost;
import bisq.common.util.Tuple2;

import org.bitcoinj.core.Coin;

import javax.inject.Inject;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import javax.annotation.Nullable;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

@Slf4j
public class OpenOfferManager implements PeerManager.Listener, DecryptedDirectMessageListener, PersistedDataHost, DaoStateListener {

    private static final long RETRY_REPUBLISH_DELAY_SEC = 10;
    private static final long REPUBLISH_AGAIN_AT_STARTUP_DELAY_SEC = 30;
    private static final long REPUBLISH_INTERVAL_MS = TimeUnit.MINUTES.toMillis(40);
    private static final long REFRESH_INTERVAL_MS = TimeUnit.MINUTES.toMillis(6);

    private final CoreContext coreContext;
    private final CreateOfferService createOfferService;
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
    private final MediatorManager mediatorManager;
    private final RefundAgentManager refundAgentManager;
    private final DaoFacade daoFacade;
    private final FilterManager filterManager;
    private final BtcFeeReceiverService btcFeeReceiverService;
    private final DelayedPayoutTxReceiverService delayedPayoutTxReceiverService;
    private final Broadcaster broadcaster;
    private final PersistenceManager<TradableList<OpenOffer>> persistenceManager;
    private final DaoStateService daoStateService;
    private final Map<String, OpenOffer> offersToBeEdited = new HashMap<>();
    private final TradableList<OpenOffer> openOffers = new TradableList<>();
    private boolean stopped;
    private Timer periodicRepublishOffersTimer, periodicRefreshOffersTimer, retryRepublishOffersTimer;
    @Setter
    private Consumer<String> chainNotSyncedHandler;
    @Getter
    private final ObservableList<Tuple2<OpenOffer, String>> invalidOffers = FXCollections.observableArrayList();


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor, Initialization
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    public OpenOfferManager(CoreContext coreContext,
                            CreateOfferService createOfferService,
                            KeyRing keyRing,
                            User user,
                            P2PService p2PService,
                            BtcWalletService btcWalletService,
                            TradeWalletService tradeWalletService,
                            BsqWalletService bsqWalletService,
                            OfferBookService offerBookService,
                            ClosedTradableManager closedTradableManager,
                            PriceFeedService priceFeedService,
                            Preferences preferences,
                            TradeStatisticsManager tradeStatisticsManager,
                            ArbitratorManager arbitratorManager,
                            MediatorManager mediatorManager,
                            RefundAgentManager refundAgentManager,
                            DaoFacade daoFacade,
                            FilterManager filterManager,
                            BtcFeeReceiverService btcFeeReceiverService,
                            DelayedPayoutTxReceiverService delayedPayoutTxReceiverService,
                            Broadcaster broadcaster,
                            PersistenceManager<TradableList<OpenOffer>> persistenceManager,
                            DaoStateService daoStateService) {
        this.coreContext = coreContext;
        this.createOfferService = createOfferService;
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
        this.mediatorManager = mediatorManager;
        this.refundAgentManager = refundAgentManager;
        this.daoFacade = daoFacade;
        this.filterManager = filterManager;
        this.btcFeeReceiverService = btcFeeReceiverService;
        this.delayedPayoutTxReceiverService = delayedPayoutTxReceiverService;
        this.broadcaster = broadcaster;
        this.persistenceManager = persistenceManager;
        this.daoStateService = daoStateService;

        this.persistenceManager.initialize(openOffers, "OpenOffers", PersistenceManager.Source.PRIVATE);
    }

    @Override
    public void readPersisted(Runnable completeHandler) {
        persistenceManager.readPersisted(persisted -> {
                    openOffers.setAll(persisted.getList());
                    openOffers.forEach(openOffer -> openOffer.getOffer().setPriceFeedService(priceFeedService));
                    completeHandler.run();
                },
                completeHandler);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // DaoStateListener implementation
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void onParseBlockCompleteAfterBatchProcessing(Block block) {
        Set<String> invalidOfferIds = invalidOffers.stream().map(e -> e.first.getId()).collect(Collectors.toSet());
        Set<Tuple2<OpenOffer, String>> exceedingOffers = openOffers.stream()
                .filter(openOffer -> !invalidOfferIds.contains(openOffer.getId()))
                .filter(openOffer -> OfferUtil.doesOfferAmountExceedTradeLimit(openOffer.getOffer()))
                .map(openOffer -> {
                    String message = "Your offer with ID `" + openOffer.getOffer().getShortId() + "` has become invalid because the max. allowed trade amount has been changed.\n\n" +
                            "The new trade limit has been activated by DAO voting. See https://github.com/bisq-network/proposals/issues/453 for more details.\n\n" +
                            "You can request a reimbursement from the Bisq DAO for the lost `maker-fee` at: https://github.com/bisq-network/support/issues.\n" +
                            "If you have any questions please reach out to the Bisq community at: https://bisq.network/community.";
                    return new Tuple2<>(openOffer, message);
                })
                .collect(Collectors.toSet());
        invalidOffers.addAll(exceedingOffers);
    }


    public void onAllServicesInitialized() {
        p2PService.addDecryptedDirectMessageListener(this);
        daoStateService.addDaoStateListener(this);

        if (p2PService.isBootstrapped()) {
            onBootstrapComplete();
        } else {
            p2PService.addP2PServiceListener(new BootstrapListener() {
                @Override
                public void onDataReceived() {
                    onBootstrapComplete();
                }
            });
        }

        cleanUpAddressEntries();

        openOffers.stream()
                .forEach(openOffer ->
                        OfferUtil.getInvalidMakerFeeTxErrorMessage(openOffer.getOffer(), btcWalletService)
                                .ifPresent(errorMsg -> invalidOffers.add(new Tuple2<>(openOffer, errorMsg))));
    }

    private void cleanUpAddressEntries() {
        Set<String> openOffersIdSet = openOffers.getList().stream()
                .map(OpenOffer::getId)
                .collect(Collectors.toSet());
        // We reset all AddressEntriesForOpenOffer which do not have a corresponding openOffer
        btcWalletService.getAddressEntriesForOpenOffer().stream()
                .filter(e -> !openOffersIdSet.contains(e.getOfferId()))
                .forEach(e -> {
                    log.warn("We found an outdated addressEntry for openOffer {} (openOffers does not contain that " +
                                    "offer), offers.size={}",
                            e.getOfferId(), openOffers.size());
                    btcWalletService.resetAddressEntriesForOpenOffer(e.getOfferId());
                });
    }

    public void shutDown(@Nullable Runnable completeHandler) {
        stopped = true;
        daoStateService.removeDaoStateListener(this);
        p2PService.getPeerManager().removeListener(this);
        p2PService.removeDecryptedDirectMessageListener(this);

        stopPeriodicRefreshOffersTimer();
        stopPeriodicRepublishOffersTimer();
        stopRetryRepublishOffersTimer();

        // we remove own offers from offerbook when we go offline
        // Normally we use a delay for broadcasting to the peers, but at shut down we want to get it fast out
        int size = openOffers.size();
        log.info("Remove open offers at shutDown. Number of open offers: {}", size);
        if (offerBookService.isBootstrapped() && size > 0) {
            UserThread.execute(() -> openOffers.forEach(
                    openOffer -> offerBookService.removeOfferAtShutDown(openOffer.getOffer().getOfferPayloadBase())
            ));

            // Force broadcaster to send out immediately, otherwise we could have a 2 sec delay until the
            // bundled messages sent out.
            broadcaster.flush();

            if (completeHandler != null) {
                // For typical number of offers we are tolerant with delay to give enough time to broadcast.
                // If number of offers is very high we limit to 3 sec. to not delay other shutdown routines.
                int delay = Math.min(3000, size * 200 + 500);
                UserThread.runAfter(completeHandler, delay, TimeUnit.MILLISECONDS);
            }
        } else {
            if (completeHandler != null)
                completeHandler.run();
        }
    }

    public void removeAllOpenOffers(@Nullable Runnable completeHandler) {
        removeOpenOffers(getObservableList(), completeHandler);
    }

    private void removeOpenOffers(List<OpenOffer> openOffers, @Nullable Runnable completeHandler) {
        int size = openOffers.size();
        // Copy list as we remove in the loop
        List<OpenOffer> openOffersList = new ArrayList<>(openOffers);
        openOffersList.forEach(this::removeOpenOffer);
        if (completeHandler != null)
            UserThread.runAfter(completeHandler, size * 200L + 500, TimeUnit.MILLISECONDS);
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

        maybeUpdatePersistedOffers();

        // Republish means we send the complete offer object
        republishOffers();
        startPeriodicRepublishOffersTimer();

        // Refresh is started once we get a success from republish

        // We republish after a bit as it might be that our connected node still has the offer in the data map
        // but other peers have it already removed because of expired TTL.
        // Those other not directly connected peers would not get the broadcast of the new offer, as the first
        // connected peer (seed node) does not broadcast if it has the data in the map.
        // To update quickly to the whole network we repeat the republishOffers call after a few seconds when we
        // are better connected to the network. There is no guarantee that all peers will receive it but we also
        // have our periodic timer, so after that longer interval the offer should be available to all peers.
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
                           double buyerSecurityDeposit,
                           boolean useSavingsWallet,
                           boolean isSharedMakerFee,
                           long triggerPrice,
                           TransactionResultHandler resultHandler,
                           ErrorMessageHandler errorMessageHandler) {
        checkNotNull(offer.getMakerFee(), "makerFee must not be null");
        checkArgument(!offer.isBsqSwapOffer());

        int numClones = getOpenOffersByMakerFeeTxId(offer.getOfferFeePaymentTxId()).size();
        if (numClones >= 10) {
            errorMessageHandler.handleErrorMessage("Cannot create offer because maximum number of 10 cloned offers with shared maker fee is reached.");
            return;
        }

        Coin reservedFundsForOffer = createOfferService.getReservedFundsForOffer(offer.getDirection(),
                offer.getAmount(),
                buyerSecurityDeposit,
                createOfferService.getSellerSecurityDepositAsDouble(buyerSecurityDeposit));

        PlaceOfferModel model = new PlaceOfferModel(offer,
                reservedFundsForOffer,
                useSavingsWallet,
                isSharedMakerFee,
                btcWalletService,
                tradeWalletService,
                bsqWalletService,
                offerBookService,
                arbitratorManager,
                tradeStatisticsManager,
                daoFacade,
                btcFeeReceiverService,
                user,
                filterManager);
        PlaceOfferProtocol placeOfferProtocol = new PlaceOfferProtocol(
                model,
                transaction -> {
                    OpenOffer openOffer = new OpenOffer(offer, triggerPrice);
                    if (isSharedMakerFee) {
                        if (cannotActivateOffer(offer)) {
                            openOffer.setState(OpenOffer.State.DEACTIVATED);
                        } else {
                            // We did not use the AddToOfferBook task for publishing because we
                            // do not have created the openOffer during the protocol and we need that to determine if the offer can be activated.
                            // So in case we have an activated cloned offer we do the publishing here.
                            model.getOfferBookService().addOffer(model.getOffer(),
                                    () -> model.setOfferAddedToOfferBook(true),
                                    errorMessage -> model.getOffer().setErrorMessage("Could not add offer to offerbook.\n" +
                                            "Please check your network connection and try again."));
                        }
                    }
                    addOpenOfferToList(openOffer);
                    if (!stopped) {
                        startPeriodicRepublishOffersTimer();
                        startPeriodicRefreshOffersTimer();
                    } else {
                        log.debug("We have stopped already. We ignore that placeOfferProtocol.placeOffer.onResult call.");
                    }
                    resultHandler.handleResult(transaction);
                },
                errorMessageHandler
        );
        placeOfferProtocol.placeOffer();
    }

    public void addOpenBsqSwapOffer(OpenOffer openOffer) {
        addOpenOfferToList(openOffer);
        if (!stopped) {
            startPeriodicRepublishOffersTimer();
            startPeriodicRefreshOffersTimer();
        } else {
            log.debug("We have stopped already. We ignore that placeOfferProtocol.placeOffer.onResult call.");
        }
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
            offerBookService.removeOffer(offer.getOfferPayloadBase(),
                    () -> offer.setState(Offer.State.REMOVED),
                    null);
        }
    }

    public void activateOpenOffer(OpenOffer openOffer,
                                  ResultHandler resultHandler,
                                  ErrorMessageHandler errorMessageHandler) {
        if (offersToBeEdited.containsKey(openOffer.getId())) {
            errorMessageHandler.handleErrorMessage(Res.get("offerbook.cannotActivateEditedOffer.warning"));
            return;
        }

        if (cannotActivateOffer(openOffer.getOffer())) {
            errorMessageHandler.handleErrorMessage(Res.get("offerbook.cannotActivate.warning"));
            return;
        }

        // If there is not enough funds for a BsqSwapOffer we do not publish the offer, but still apply the state change.
        // Once the wallet gets funded the offer gets published automatically.
        if (isBsqSwapOfferLackingFunds(openOffer)) {
            openOffer.setState(OpenOffer.State.AVAILABLE);
            requestPersistence();
            resultHandler.handleResult();
            return;
        }

        Offer offer = openOffer.getOffer();
        offerBookService.activateOffer(offer,
                () -> {
                    openOffer.setState(OpenOffer.State.AVAILABLE);
                    openOffer.setFeeValidationStatus(FeeValidationStatus.NOT_CHECKED_YET);
                    requestPersistence();
                    log.debug("activateOpenOffer, offerId={}", offer.getId());
                    resultHandler.handleResult();
                },
                errorMessageHandler);
    }

    public void deactivateOpenOffer(OpenOffer openOffer,
                                    ResultHandler resultHandler,
                                    ErrorMessageHandler errorMessageHandler) {
        Offer offer = openOffer.getOffer();
        offerBookService.deactivateOffer(offer.getOfferPayloadBase(),
                () -> {
                    openOffer.setState(OpenOffer.State.DEACTIVATED);
                    requestPersistence();
                    log.debug("deactivateOpenOffer, offerId={}", offer.getId());
                    resultHandler.handleResult();
                },
                errorMessageHandler);
    }

    public void removeOpenOffer(OpenOffer openOffer) {
        removeOpenOffer(openOffer, () -> {
        }, error -> {
        });
    }

    public void removeOpenOffer(OpenOffer openOffer,
                                ResultHandler resultHandler,
                                ErrorMessageHandler errorMessageHandler) {
        if (!offersToBeEdited.containsKey(openOffer.getId())) {
            Offer offer = openOffer.getOffer();
            if (openOffer.isDeactivated()) {
                onRemoved(openOffer, resultHandler, offer);
            } else {
                offerBookService.removeOffer(offer.getOfferPayloadBase(),
                        () -> onRemoved(openOffer, resultHandler, offer),
                        errorMessageHandler);
            }
        } else {
            errorMessageHandler.handleErrorMessage("You can't remove an offer that is currently edited.");
        }
    }

    public void editOpenOfferStart(OpenOffer openOffer,
                                   ResultHandler resultHandler,
                                   ErrorMessageHandler errorMessageHandler) {
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
                    resultHandler,
                    errorMessage -> {
                        offersToBeEdited.remove(openOffer.getId());
                        errorMessageHandler.handleErrorMessage(errorMessage);
                    });
        }
    }

    public void editOpenOfferPublish(Offer editedOffer,
                                     long triggerPrice,
                                     OpenOffer.State originalState,
                                     ResultHandler resultHandler,
                                     ErrorMessageHandler errorMessageHandler) {
        Optional<OpenOffer> openOfferOptional = getOpenOfferById(editedOffer.getId());

        if (openOfferOptional.isPresent()) {
            OpenOffer openOffer = openOfferOptional.get();

            openOffer.getOffer().setState(Offer.State.REMOVED);
            openOffer.setState(OpenOffer.State.CANCELED);
            removeOpenOfferFromList(openOffer);

            OpenOffer editedOpenOffer = new OpenOffer(editedOffer, triggerPrice);
            editedOpenOffer.setState(originalState);

            addOpenOfferToList(editedOpenOffer);

            if (!editedOpenOffer.isDeactivated())
                maybeRepublishOffer(editedOpenOffer);

            offersToBeEdited.remove(openOffer.getId());
            resultHandler.handleResult();
        } else {
            errorMessageHandler.handleErrorMessage("There is no offer with this id existing to be published.");
        }
    }

    public void editOpenOfferCancel(OpenOffer openOffer,
                                    OpenOffer.State originalState,
                                    ResultHandler resultHandler,
                                    ErrorMessageHandler errorMessageHandler) {
        if (offersToBeEdited.containsKey(openOffer.getId())) {
            offersToBeEdited.remove(openOffer.getId());
            if (originalState.equals(OpenOffer.State.AVAILABLE)) {
                activateOpenOffer(openOffer, resultHandler, errorMessageHandler);
            } else {
                resultHandler.handleResult();
            }
        }
    }

    private void onRemoved(OpenOffer openOffer, ResultHandler resultHandler, Offer offer) {
        offer.setState(Offer.State.REMOVED);
        openOffer.setState(OpenOffer.State.CANCELED);
        removeOpenOfferFromList(openOffer);

        if (!openOffer.getOffer().isBsqSwapOffer()) {
            // In case of an offer which has its maker fee shared with other offers, we do not add the openOffer
            // to history. Only when the last offer with that maker fee txId got removed we add it.
            // Only canceled offers which have lost maker fees are shown in history.
            // For that reason we also do not add BSQ offers.
            if (getOpenOffersByMakerFeeTxId(offer.getOfferFeePaymentTxId()).isEmpty()) {
                closedTradableManager.add(openOffer);

                // We only reset if there are no other offers with the shared maker fee as otherwise the
                // address in the addressEntry would become available while it's still RESERVED_FOR_TRADE
                // for the remaining offers.
                btcWalletService.resetAddressEntriesForOpenOffer(offer.getId());
            }
        }
        log.info("onRemoved offerId={}", offer.getId());
        resultHandler.handleResult();
    }

    // Close openOffer after deposit published
    public void closeOpenOffer(Offer offer) {
        if (offer.isBsqSwapOffer()) {
            getOpenOfferById(offer.getId()).ifPresent(openOffer -> {
                removeOpenOfferFromList(openOffer);
                openOffer.setState(OpenOffer.State.CLOSED);
                offerBookService.removeOffer(openOffer.getOffer().getOfferPayloadBase(),
                        () -> log.trace("Successfully removed offer"),
                        log::error);
            });
        } else {
            getOpenOffersByMakerFeeTxId(offer.getOfferFeePaymentTxId()).forEach(openOffer -> {
                removeOpenOfferFromList(openOffer);

                if (offer.getId().equals(openOffer.getId())) {
                    openOffer.setState(OpenOffer.State.CLOSED);
                } else {
                    // We use CANCELED for the offers which have shared maker fee but have not been taken for the trade.
                    openOffer.setState(OpenOffer.State.CANCELED);
                    // We need to reset now those entries as well
                    btcWalletService.resetAddressEntriesForOpenOffer(openOffer.getId());
                }
                offerBookService.removeOffer(openOffer.getOffer().getOfferPayloadBase(),
                        () -> log.trace("Successfully removed offer"),
                        log::error);
            });
        }
    }

    public void reserveOpenOffer(OpenOffer openOffer) {
        openOffer.setState(OpenOffer.State.RESERVED);
        requestPersistence();
    }

    public boolean cannotActivateOffer(Offer offer) {
        return openOffers.stream()
                .filter(openOffer -> !openOffer.getOffer().isBsqSwapOffer())    // We only handle non-BSQ offers
                .filter(openOffer -> !openOffer.getId().equals(offer.getId()))  // our own offer gets skipped
                .filter(openOffer -> !openOffer.isDeactivated())  // we only check with activated offers
                .anyMatch(openOffer ->
                        // Offers which share our maker fee will get checked if they have the same payment method
                        // and currency.
                        openOffer.getOffer().getOfferFeePaymentTxId() != null &&
                                openOffer.getOffer().getOfferFeePaymentTxId().equals(offer.getOfferFeePaymentTxId()) &&
                                openOffer.getOffer().getPaymentMethodId().equalsIgnoreCase(offer.getPaymentMethodId()) &&
                                openOffer.getOffer().getCounterCurrencyCode().equalsIgnoreCase(offer.getCounterCurrencyCode()) &&
                                openOffer.getOffer().getBaseCurrencyCode().equalsIgnoreCase(offer.getBaseCurrencyCode()));
    }

    public boolean hasOfferSharedMakerFee(OpenOffer openOffer) {
        return getOpenOffersByMakerFeeTxId(openOffer.getOffer().getOfferFeePaymentTxId()).size() > 1;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Getters
    ///////////////////////////////////////////////////////////////////////////////////////////

    public boolean isMyOffer(Offer offer) {
        return offer.isMyOffer(keyRing);
    }

    public ObservableList<OpenOffer> getObservableList() {
        return openOffers.getObservableList();
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

        if (OfferRestrictions.requiresNodeAddressUpdate() && !Utils.isV3Address(peer.getHostName())) {
            errorMessage = "We got a handleOfferAvailabilityRequest from a Tor node v2 address where a Tor node v3 address is required.";
            log.info(errorMessage);
            sendAckMessage(request, peer, false, errorMessage);
            return;
        }

        if (!p2PService.isBootstrapped()) {
            errorMessage = "We got a handleOfferAvailabilityRequest but we have not bootstrapped yet.";
            log.info(errorMessage);
            sendAckMessage(request, peer, false, errorMessage);
            return;
        }

        // Don't allow trade start if BitcoinJ is not fully synced (bisq issue #4764)
        if (!btcWalletService.isChainHeightSyncedWithinTolerance()) {
            errorMessage = "We got a handleOfferAvailabilityRequest but our chain is not synced.";
            log.info(errorMessage);
            sendAckMessage(request, peer, false, errorMessage);
            if (chainNotSyncedHandler != null) {
                chainNotSyncedHandler.accept(Res.get("popup.warning.chainNotSynced"));
            }
            return;
        }

        // Don't allow trade start if DAO is not fully synced
        if (!daoFacade.isDaoStateReadyAndInSync()) {
            errorMessage = "We got a handleOfferAvailabilityRequest but our DAO is not synced.";
            log.info(errorMessage);
            sendAckMessage(request, peer, false, errorMessage);
            if (chainNotSyncedHandler != null) {
                chainNotSyncedHandler.accept(Res.get("popup.warning.daoNeedsResync"));
            }
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
            errorMessage = "Message validation failed. Error=" + t + ", Message=" + request;
            log.warn(errorMessage);
            sendAckMessage(request, peer, false, errorMessage);
            return;
        }

        try {
            Optional<OpenOffer> openOfferOptional = getOpenOfferById(request.offerId);
            AvailabilityResult availabilityResult;
            NodeAddress arbitratorNodeAddress = null;
            NodeAddress mediatorNodeAddress = null;
            NodeAddress refundAgentNodeAddress = null;
            if (openOfferOptional.isPresent()) {
                OpenOffer openOffer = openOfferOptional.get();
                if (!apiUserDeniedByOffer(request)) {
                    if (openOffer.getState() == OpenOffer.State.AVAILABLE) {
                        Offer offer = openOffer.getOffer();
                        if (preferences.getIgnoreTradersList().stream().noneMatch(fullAddress -> fullAddress.equals(peer.getFullAddress()))) {
                            mediatorNodeAddress = DisputeAgentSelection.getRandomMediator(mediatorManager).getNodeAddress();
                            openOffer.setMediatorNodeAddress(mediatorNodeAddress);

                            refundAgentNodeAddress = DisputeAgentSelection.getRandomRefundAgent(refundAgentManager).getNodeAddress();
                            openOffer.setRefundAgentNodeAddress(refundAgentNodeAddress);

                            try {
                                // Check also tradePrice to avoid failures after taker fee is paid caused by a too big difference
                                // in trade price between the peers. Also here poor connectivity might cause market price API connection
                                // losses and therefore an outdated market price.
                                offer.verifyTakersTradePrice(request.getTakersTradePrice());
                                availabilityResult = AvailabilityResult.AVAILABLE;
                            } catch (TradePriceOutOfToleranceException e) {
                                log.warn("Trade price check failed because takers price is outside out tolerance.");
                                availabilityResult = AvailabilityResult.PRICE_OUT_OF_TOLERANCE;
                            } catch (MarketPriceNotAvailableException e) {
                                log.warn(e.getMessage());
                                availabilityResult = AvailabilityResult.MARKET_PRICE_NOT_AVAILABLE;
                            } catch (Throwable e) {
                                log.warn("Trade price check failed. " + e.getMessage());
                                availabilityResult = AvailabilityResult.PRICE_CHECK_FAILED;
                            }
                        } else {
                            availabilityResult = AvailabilityResult.USER_IGNORED;
                        }
                    } else {
                        availabilityResult = AvailabilityResult.OFFER_TAKEN;
                    }
                } else {
                    availabilityResult = AvailabilityResult.MAKER_DENIED_API_USER;
                }
            } else {
                log.warn("handleOfferAvailabilityRequest: openOffer not found.");
                availabilityResult = AvailabilityResult.OFFER_TAKEN;
            }

            if (btcWalletService.isUnconfirmedTransactionsLimitHit() || bsqWalletService.isUnconfirmedTransactionsLimitHit()) {
                errorMessage = Res.get("shared.unconfirmedTransactionsLimitReached");
                log.warn(errorMessage);
                availabilityResult = AvailabilityResult.UNCONF_TX_LIMIT_HIT;
            }

            try {
                int takersBurningManSelectionHeight = request.getBurningManSelectionHeight();
                checkArgument(takersBurningManSelectionHeight > 0, "takersBurningManSelectionHeight must not be 0");

                int makersBurningManSelectionHeight = delayedPayoutTxReceiverService.getBurningManSelectionHeight();
                checkArgument(takersBurningManSelectionHeight == makersBurningManSelectionHeight,
                        "takersBurningManSelectionHeight does no match makersBurningManSelectionHeight. " +
                                "takersBurningManSelectionHeight=" + takersBurningManSelectionHeight + "; makersBurningManSelectionHeight=" + makersBurningManSelectionHeight);
            } catch (Throwable t) {
                errorMessage = "Message validation failed. Error=" + t + ", Message=" + request;
                log.warn(errorMessage);
                availabilityResult = AvailabilityResult.INVALID_SNAPSHOT_HEIGHT;
            }

            OfferAvailabilityResponse offerAvailabilityResponse = new OfferAvailabilityResponse(request.offerId,
                    availabilityResult,
                    arbitratorNodeAddress,
                    mediatorNodeAddress,
                    refundAgentNodeAddress);
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
                                    offerAvailabilityResponse.getClass().getSimpleName(),
                                    offerAvailabilityResponse.getOfferId(),
                                    offerAvailabilityResponse.getUid());
                        }

                        @Override
                        public void onFault(String errorMessage) {
                            log.error("Sending {} failed: uid={}; peer={}; error={}",
                                    offerAvailabilityResponse.getClass().getSimpleName(),
                                    offerAvailabilityResponse.getUid(),
                                    peer,
                                    errorMessage);
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

    private boolean apiUserDeniedByOffer(OfferAvailabilityRequest request) {
        return preferences.isDenyApiTaker() && request.isTakerApiUser();
    }

    private void sendAckMessage(OfferAvailabilityRequest message,
                                NodeAddress sender,
                                boolean result,
                                String errorMessage) {
        String offerId = message.getOfferId();
        String sourceUid = message.getUid();
        AckMessage ackMessage = new AckMessage(p2PService.getNetworkNode().getNodeAddress(),
                AckMessageSourceType.OFFER_MESSAGE,
                message.getClass().getSimpleName(),
                sourceUid,
                offerId,
                result,
                errorMessage);

        NodeAddress takersNodeAddress = sender;
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
    // Update persisted offer if a new capability is required after a software update
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void maybeUpdatePersistedOffers() {
        // We need to clone to avoid ConcurrentModificationException
        ArrayList<OpenOffer> openOffersClone = new ArrayList<>(openOffers.getList());
        openOffersClone.forEach(originalOpenOffer -> {
            Offer originalOffer = originalOpenOffer.getOffer();
            if (originalOffer.isBsqSwapOffer()) {
                // Offer without a fee transaction don't need to be updated, they can be removed and a new
                // offer created without incurring any extra costs
                return;
            }
            OfferPayload original = originalOffer.getOfferPayload().orElseThrow();
            // We added CAPABILITIES with entry for Capability.MEDIATION in v1.1.6 and
            // Capability.REFUND_AGENT in v1.2.0 and want to rewrite a
            // persisted offer after the user has updated to 1.2.0 so their offer will be accepted by the network.

            int tradeProtocolVersion = Version.getTradeProtocolVersion();
            if (original.getProtocolVersion() < tradeProtocolVersion ||
                    !OfferRestrictions.hasOfferMandatoryCapability(originalOffer, Capability.MEDIATION) ||
                    !OfferRestrictions.hasOfferMandatoryCapability(originalOffer, Capability.REFUND_AGENT) ||
                    !original.getOwnerNodeAddress().equals(p2PService.getAddress())) {

                // - Capabilities changed?
                // We rewrite our offer with the additional capabilities entry
                Map<String, String> updatedExtraDataMap = new HashMap<>();
                if (!OfferRestrictions.hasOfferMandatoryCapability(originalOffer, Capability.MEDIATION) ||
                        !OfferRestrictions.hasOfferMandatoryCapability(originalOffer, Capability.REFUND_AGENT)) {
                    Map<String, String> originalExtraDataMap = original.getExtraDataMap();

                    if (originalExtraDataMap != null) {
                        updatedExtraDataMap.putAll(originalExtraDataMap);
                    }

                    // We overwrite any entry with our current capabilities
                    updatedExtraDataMap.put(OfferPayload.CAPABILITIES, Capabilities.app.toStringList());

                    log.info("Converted offer to support new Capability.MEDIATION and Capability.REFUND_AGENT capability. id={}", originalOffer.getId());
                } else {
                    updatedExtraDataMap = original.getExtraDataMap();
                }

                // - Protocol version changed?
                int protocolVersion = original.getProtocolVersion();
                if (protocolVersion < tradeProtocolVersion) {
                    // We update the trade protocol version
                    protocolVersion = tradeProtocolVersion;
                    log.info("Updated the protocol version of offer id={}", originalOffer.getId());
                }

                // - node address changed? (due to a faulty tor dir)
                NodeAddress ownerNodeAddress = original.getOwnerNodeAddress();
                if (!ownerNodeAddress.equals(p2PService.getAddress())) {
                    ownerNodeAddress = p2PService.getAddress();
                    log.info("Updated the owner nodeaddress of offer id={}", originalOffer.getId());
                }

                OfferPayload updatedPayload = new OfferPayload(original.getId(),
                        original.getDate(),
                        ownerNodeAddress,
                        original.getPubKeyRing(),
                        original.getDirection(),
                        original.getPrice(),
                        original.getMarketPriceMargin(),
                        original.isUseMarketBasedPrice(),
                        original.getAmount(),
                        original.getMinAmount(),
                        original.getBaseCurrencyCode(),
                        original.getCounterCurrencyCode(),
                        original.getArbitratorNodeAddresses(),
                        original.getMediatorNodeAddresses(),
                        original.getPaymentMethodId(),
                        original.getMakerPaymentAccountId(),
                        original.getOfferFeePaymentTxId(),
                        original.getCountryCode(),
                        original.getAcceptedCountryCodes(),
                        original.getBankId(),
                        original.getAcceptedBankIds(),
                        original.getVersionNr(),
                        original.getBlockHeightAtOfferCreation(),
                        original.getTxFee(),
                        original.getMakerFee(),
                        original.isCurrencyForMakerFeeBtc(),
                        original.getBuyerSecurityDeposit(),
                        original.getSellerSecurityDeposit(),
                        original.getMaxTradeLimit(),
                        original.getMaxTradePeriod(),
                        original.isUseAutoClose(),
                        original.isUseReOpenAfterAutoClose(),
                        original.getLowerClosePrice(),
                        original.getUpperClosePrice(),
                        original.isPrivateOffer(),
                        original.getHashOfChallenge(),
                        updatedExtraDataMap,
                        protocolVersion);

                // Save states from original data to use for the updated
                Offer.State originalOfferState = originalOffer.getState();
                OpenOffer.State originalOpenOfferState = originalOpenOffer.getState();

                // remove old offer
                originalOffer.setState(Offer.State.REMOVED);
                originalOpenOffer.setState(OpenOffer.State.CANCELED);
                removeOpenOfferFromList(originalOpenOffer);

                // Create new Offer
                Offer updatedOffer = new Offer(updatedPayload);
                updatedOffer.setPriceFeedService(priceFeedService);
                updatedOffer.setState(originalOfferState);

                OpenOffer updatedOpenOffer = new OpenOffer(updatedOffer, originalOpenOffer.getTriggerPrice());
                updatedOpenOffer.setState(originalOpenOfferState);
                addOpenOfferToList(updatedOpenOffer);

                log.info("Updating offer completed. id={}", originalOffer.getId());
            }
        });
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // RepublishOffers, refreshOffers
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void republishOffers() {
        if (stopped) {
            return;
        }
        stopPeriodicRefreshOffersTimer();

        List<OpenOffer> openOffersList = new ArrayList<>(openOffers.getList());
        processListForRepublishOffers(openOffersList);
    }

    private void processListForRepublishOffers(List<OpenOffer> list) {
        if (list.isEmpty()) {
            return;
        }

        OpenOffer openOffer = list.remove(0);
        if (openOffers.contains(openOffer)) {
            maybeRepublishOffer(openOffer, () -> processListForRepublishOffers(list));
        } else {
            // If the offer was removed in the meantime or if its deactivated we skip and call
            // processListForRepublishOffers again with the list where we removed the offer already.
            processListForRepublishOffers(list);
        }
    }

    public void maybeRepublishOffer(OpenOffer openOffer) {
        maybeRepublishOffer(openOffer, null);
    }

    private void maybeRepublishOffer(OpenOffer openOffer, @Nullable Runnable completeHandler) {
        if (preventedFromPublishing(openOffer)) {
            if (completeHandler != null) {
                completeHandler.run();
            }
            return;
        }

        offerBookService.addOffer(openOffer.getOffer(),
                () -> {
                    if (!stopped) {
                        // Refresh means we send only the data needed to refresh the TTL (hash, signature and sequence no.)
                        if (periodicRefreshOffersTimer == null) {
                            startPeriodicRefreshOffersTimer();
                        }
                        if (completeHandler != null) {
                            completeHandler.run();
                        }
                    }
                },
                errorMessage -> {
                    if (!stopped) {
                        log.error("Adding offer to P2P network failed. " + errorMessage);
                        stopRetryRepublishOffersTimer();
                        retryRepublishOffersTimer = UserThread.runAfter(OpenOfferManager.this::republishOffers,
                                RETRY_REPUBLISH_DELAY_SEC);

                        if (completeHandler != null) {
                            completeHandler.run();
                        }
                    }
                });
    }

    private void startPeriodicRepublishOffersTimer() {
        stopped = false;
        if (periodicRepublishOffersTimer == null) {
            periodicRepublishOffersTimer = UserThread.runPeriodically(() -> {
                        if (!stopped) {
                            republishOffers();
                        }
                    },
                    REPUBLISH_INTERVAL_MS,
                    TimeUnit.MILLISECONDS);
        }
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
                                    if (openOffers.contains(openOffer))
                                        maybeRefreshOffer(openOffer);
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

    private void maybeRefreshOffer(OpenOffer openOffer) {
        if (preventedFromPublishing(openOffer)) {
            return;
        }
        offerBookService.refreshTTL(openOffer.getOffer().getOfferPayloadBase(),
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

    public void requestPersistence() {
        persistenceManager.requestPersistence();
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

    private void addOpenOfferToList(OpenOffer openOffer) {
        openOffers.add(openOffer);
        requestPersistence();
    }

    private void removeOpenOfferFromList(OpenOffer openOffer) {
        openOffers.remove(openOffer);
        requestPersistence();
    }

    private boolean isBsqSwapOfferLackingFunds(OpenOffer openOffer) {
        return openOffer.getOffer().isBsqSwapOffer() &&
                openOffer.isBsqSwapOfferHasMissingFunds();
    }

    private boolean preventedFromPublishing(OpenOffer openOffer) {
        return openOffer.isDeactivated() ||
                openOffer.isBsqSwapOfferHasMissingFunds() ||
                cannotActivateOffer(openOffer.getOffer());
    }

    private Set<OpenOffer> getOpenOffersByMakerFeeTxId(String makerFeeTxId) {
        return openOffers.stream()
                .filter(openOffer -> !openOffer.getOffer().isBsqSwapOffer() &&
                        makerFeeTxId != null &&
                        makerFeeTxId.equals(openOffer.getOffer().getOfferFeePaymentTxId()))
                .collect(Collectors.toSet());
    }
}
