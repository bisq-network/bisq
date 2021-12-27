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

package bisq.core.offer.bsq_swap;

import bisq.core.btc.wallet.BsqWalletService;
import bisq.core.btc.wallet.BtcWalletService;
import bisq.core.dao.DaoFacade;
import bisq.core.dao.state.DaoStateListener;
import bisq.core.dao.state.model.blockchain.Block;
import bisq.core.filter.Filter;
import bisq.core.filter.FilterManager;
import bisq.core.monetary.Price;
import bisq.core.offer.Offer;
import bisq.core.offer.OfferBookService;
import bisq.core.offer.OfferDirection;
import bisq.core.offer.OfferPayloadBase;
import bisq.core.offer.OfferUtil;
import bisq.core.offer.OpenOffer;
import bisq.core.offer.OpenOfferManager;
import bisq.core.offer.placeoffer.bsq_swap.PlaceBsqSwapOfferModel;
import bisq.core.offer.placeoffer.bsq_swap.PlaceBsqSwapOfferProtocol;
import bisq.core.payment.payload.PaymentMethod;
import bisq.core.provider.fee.FeeService;

import bisq.network.p2p.BootstrapListener;
import bisq.network.p2p.NodeAddress;
import bisq.network.p2p.P2PService;

import bisq.common.UserThread;
import bisq.common.app.Version;
import bisq.common.crypto.ProofOfWorkService;
import bisq.common.crypto.PubKeyRing;
import bisq.common.handlers.ErrorMessageHandler;
import bisq.common.handlers.ResultHandler;

import org.bitcoinj.core.Coin;

import javax.inject.Inject;
import javax.inject.Singleton;

import javafx.beans.value.ChangeListener;

import javafx.collections.ListChangeListener;

import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import lombok.extern.slf4j.Slf4j;

import static com.google.common.base.Preconditions.checkArgument;

@Slf4j
@Singleton
public class OpenBsqSwapOfferService {
    private final OpenOfferManager openOfferManager;
    private final BtcWalletService btcWalletService;
    private final BsqWalletService bsqWalletService;
    private final FeeService feeService;
    private final P2PService p2PService;
    private final DaoFacade daoFacade;
    private final OfferBookService offerBookService;
    private final OfferUtil offerUtil;
    private final FilterManager filterManager;
    private final PubKeyRing pubKeyRing;

    private final Map<String, OpenBsqSwapOffer> openBsqSwapOffersById = new HashMap<>();
    private final ListChangeListener<OpenOffer> offerListChangeListener;
    private final ChangeListener<Filter> filterChangeListener;
    private final DaoStateListener daoStateListener;
    private final BootstrapListener bootstrapListener;

    @Inject
    public OpenBsqSwapOfferService(OpenOfferManager openOfferManager,
                                   BtcWalletService btcWalletService,
                                   BsqWalletService bsqWalletService,
                                   FeeService feeService,
                                   P2PService p2PService,
                                   DaoFacade daoFacade,
                                   OfferBookService offerBookService,
                                   OfferUtil offerUtil,
                                   FilterManager filterManager,
                                   PubKeyRing pubKeyRing) {
        this.openOfferManager = openOfferManager;
        this.btcWalletService = btcWalletService;
        this.bsqWalletService = bsqWalletService;
        this.feeService = feeService;
        this.p2PService = p2PService;
        this.daoFacade = daoFacade;
        this.offerBookService = offerBookService;
        this.offerUtil = offerUtil;
        this.filterManager = filterManager;
        this.pubKeyRing = pubKeyRing;

        offerListChangeListener = c -> {
            c.next();
            if (c.wasAdded()) {
                onOpenOffersAdded(c.getAddedSubList());
            } else if (c.wasRemoved()) {
                onOpenOffersRemoved(c.getRemoved());
            }
        };
        bootstrapListener = new BootstrapListener() {
            @Override
            public void onUpdatedDataReceived() {
                onP2PServiceReady();
                p2PService.removeP2PServiceListener(bootstrapListener);
            }
        };
        daoStateListener = new DaoStateListener() {
            @Override
            public void onParseBlockCompleteAfterBatchProcessing(Block block) {
                // The balance gets updated at the same event handler but we do not know which handler
                // gets called first, so we delay here a bit to be sure the balance is set
                UserThread.runAfter(() -> {
                    onDaoReady();
                    daoFacade.removeBsqStateListener(daoStateListener);
                }, 100, TimeUnit.MILLISECONDS);
            }
        };
        filterChangeListener = (observable, oldValue, newValue) -> {
            if (newValue != null) {
                onProofOfWorkDifficultyChanged();
            }
        };
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // API
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void onAllServicesInitialized() {
        if (p2PService.isBootstrapped()) {
            onP2PServiceReady();
        } else {
            p2PService.addP2PServiceListener(bootstrapListener);
        }
    }

    private void onP2PServiceReady() {
        if (daoFacade.isParseBlockChainComplete()) {
            onDaoReady();
        } else {
            daoFacade.addBsqStateListener(daoStateListener);
        }
    }

    private void onDaoReady() {
        filterManager.filterProperty().addListener(filterChangeListener);
        openOfferManager.getObservableList().addListener(offerListChangeListener);
        onOpenOffersAdded(openOfferManager.getObservableList());
    }

    public void shutDown() {
        openOfferManager.getObservableList().removeListener(offerListChangeListener);
        p2PService.removeP2PServiceListener(bootstrapListener);
        daoFacade.removeBsqStateListener(daoStateListener);
        filterManager.filterProperty().removeListener(filterChangeListener);
    }

    public void requestNewOffer(String offerId,
                                OfferDirection direction,
                                Coin amount,
                                Coin minAmount,
                                Price price,
                                Consumer<Offer> resultHandler) {
        log.info("offerId={}, \n" +
                        "direction={}, \n" +
                        "price={}, \n" +
                        "amount={}, \n" +
                        "minAmount={}, \n",
                offerId,
                direction,
                price.getValue(),
                amount.value,
                minAmount.value);

        NodeAddress makerAddress = Objects.requireNonNull(p2PService.getAddress());
        offerUtil.validateBasicOfferData(PaymentMethod.BSQ_SWAP, "BSQ");

        double difficulty = getPowDifficulty();
        getPowService().mint(offerId, makerAddress.getFullAddress(), difficulty)
                .whenComplete((proofOfWork, throwable) -> {
                    // We got called from a non user thread...
                    UserThread.execute(() -> {
                        if (throwable != null) {
                            log.error(throwable.toString());
                            return;
                        }

                        BsqSwapOfferPayload bsqSwapOfferPayload = new BsqSwapOfferPayload(offerId,
                                new Date().getTime(),
                                makerAddress,
                                pubKeyRing,
                                direction,
                                price.getValue(),
                                amount.getValue(),
                                minAmount.getValue(),
                                proofOfWork,
                                null,
                                Version.VERSION,
                                Version.TRADE_PROTOCOL_VERSION);
                        resultHandler.accept(new Offer(bsqSwapOfferPayload));
                    });
                });
    }

    public void placeBsqSwapOffer(Offer offer,
                                  Runnable resultHandler,
                                  ErrorMessageHandler errorMessageHandler) {
        checkArgument(offer.isBsqSwapOffer());
        PlaceBsqSwapOfferModel model = new PlaceBsqSwapOfferModel(offer, offerBookService);
        PlaceBsqSwapOfferProtocol protocol = new PlaceBsqSwapOfferProtocol(model,
                () -> {
                    OpenOffer openOffer = new OpenOffer(offer);
                    openOfferManager.addOpenBsqSwapOffer(openOffer);
                    resultHandler.run();
                },
                errorMessageHandler
        );
        protocol.placeOffer();
    }

    public void activateOpenOffer(OpenOffer openOffer,
                                  ResultHandler resultHandler,
                                  ErrorMessageHandler errorMessageHandler) {
        if (isProofOfWorkInvalid(openOffer.getOffer())) {
            redoProofOfWorkAndRepublish(openOffer);
            return;
        }

        openOfferManager.activateOpenOffer(openOffer, resultHandler, errorMessageHandler);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Package scope
    ///////////////////////////////////////////////////////////////////////////////////////////

    void requestPersistence() {
        openOfferManager.requestPersistence();
    }

    void enableBsqSwapOffer(OpenOffer openOffer) {
        if (isProofOfWorkInvalid(openOffer.getOffer())) {
            redoProofOfWorkAndRepublish(openOffer);
            return;
        }

        offerBookService.addOffer(openOffer.getOffer(),
                () -> {
                    openOffer.setState(OpenOffer.State.AVAILABLE);
                    openOfferManager.requestPersistence();
                    log.info("enableBsqSwapOffer{}", openOffer.getShortId());
                },
                errorMessage -> log.warn("Failed to enableBsqSwapOffer {}", openOffer.getShortId()));
    }

    void disableBsqSwapOffer(OpenOffer openOffer) {
        OfferPayloadBase offerPayloadBase = openOffer.getOffer().getOfferPayloadBase();
        offerBookService.removeOffer(offerPayloadBase,
                () -> log.info("disableBsqSwapOffer {}", openOffer.getShortId()),
                errorMessage -> log.warn("Failed to disableBsqSwapOffer {}", openOffer.getShortId()));
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Handlers
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void onOpenOffersAdded(List<? extends OpenOffer> list) {
        list.stream()
                .filter(openOffer -> openOffer.getOffer().isBsqSwapOffer())
                .filter(openOffer -> !openOffer.isDeactivated())
                .forEach(openOffer -> {
                    if (isProofOfWorkInvalid(openOffer.getOffer())) {
                        // Avoiding ConcurrentModificationException
                        UserThread.execute(() -> redoProofOfWorkAndRepublish(openOffer));
                    } else {
                        OpenBsqSwapOffer openBsqSwapOffer = new OpenBsqSwapOffer(openOffer,
                                this,
                                feeService,
                                btcWalletService,
                                bsqWalletService);
                        String offerId = openOffer.getId();
                        if (openBsqSwapOffersById.containsKey(offerId)) {
                            openBsqSwapOffersById.get(offerId).removeListeners();
                        }
                        openBsqSwapOffersById.put(offerId, openBsqSwapOffer);
                        openBsqSwapOffer.applyFundingState();
                    }
                });
    }

    private void onOpenOffersRemoved(List<? extends OpenOffer> list) {
        list.stream()
                .filter(openOffer -> openOffer.getOffer().isBsqSwapOffer())
                .map(OpenOffer::getId)
                .forEach(offerId -> {
                    if (openBsqSwapOffersById.containsKey(offerId)) {
                        openBsqSwapOffersById.get(offerId).removeListeners();
                        openBsqSwapOffersById.remove(offerId);
                    }
                });
    }

    private void onProofOfWorkDifficultyChanged() {
        openBsqSwapOffersById.values().stream()
                .filter(openBsqSwapOffer -> !openBsqSwapOffer.isDeactivated())
                .filter(openBsqSwapOffer -> !openBsqSwapOffer.isBsqSwapOfferHasMissingFunds())
                .filter(openBsqSwapOffer -> isProofOfWorkInvalid(openBsqSwapOffer.getOffer()))
                .forEach(openBsqSwapOffer -> {
                    // Avoiding ConcurrentModificationException
                    UserThread.execute(() -> redoProofOfWorkAndRepublish(openBsqSwapOffer.getOpenOffer()));
                });
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Proof of work
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void redoProofOfWorkAndRepublish(OpenOffer openOffer) {
        // This triggers our onOpenOffersRemoved handler so we don't handle removal here
        openOfferManager.removeOpenOffer(openOffer);

        String newOfferId = OfferUtil.getOfferIdWithMutationCounter(openOffer.getId());
        NodeAddress nodeAddress = Objects.requireNonNull(openOffer.getOffer().getMakerNodeAddress());
        double difficulty = getPowDifficulty();
        getPowService().mint(newOfferId, nodeAddress.getFullAddress(), difficulty)
                .whenComplete((proofOfWork, throwable) -> {
                    // We got called from a non user thread...
                    UserThread.execute(() -> {
                        if (throwable != null) {
                            log.error(throwable.toString());
                            return;
                        }
                        // We mutate the offerId with a postfix counting the mutations to get a new unique id.
                        // This helps to avoid issues with getting added/removed at some delayed moment the offer

                        BsqSwapOfferPayload newPayload = BsqSwapOfferPayload.from(openOffer.getBsqSwapOfferPayload(),
                                newOfferId,
                                proofOfWork);
                        Offer newOffer = new Offer(newPayload);
                        newOffer.setState(Offer.State.AVAILABLE);

                        checkArgument(!openOffer.isDeactivated(),
                                "We must not get called at redoProofOrWorkAndRepublish if offer was deactivated");
                        OpenOffer newOpenOffer = new OpenOffer(newOffer, OpenOffer.State.AVAILABLE);
                        if (!newOpenOffer.isDeactivated()) {
                            openOfferManager.maybeRepublishOffer(newOpenOffer);
                        }
                        // This triggers our onOpenOffersAdded handler so we don't handle adding to our list here
                        openOfferManager.addOpenBsqSwapOffer(newOpenOffer);
                    });
                });
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Utils
    ///////////////////////////////////////////////////////////////////////////////////////////

    private boolean isProofOfWorkInvalid(Offer offer) {
        return !filterManager.isProofOfWorkValid(offer);
    }


    private double getPowDifficulty() {
        return filterManager.getFilter() != null ? filterManager.getFilter().getPowDifficulty() : 0.0;
    }

    private ProofOfWorkService getPowService() {
        var service = filterManager.getEnabledPowVersions().stream()
                .flatMap(v -> ProofOfWorkService.forVersion(v).stream())
                .findFirst();
        if (!service.isPresent()) {
            // We cannot exit normally, else we get caught in an infinite loop generating invalid PoWs.
            throw new NoSuchElementException("Could not find a suitable PoW version to use.");
        }
        log.info("Selected PoW version {}, service instance {}", service.get().getVersion(), service.get());
        return service.get();
    }
}
