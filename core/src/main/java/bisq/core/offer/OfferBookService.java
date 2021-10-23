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

import bisq.core.filter.FilterManager;
import bisq.core.locale.Res;
import bisq.core.provider.price.PriceFeedService;
import bisq.core.util.JsonUtil;

import bisq.network.p2p.BootstrapListener;
import bisq.network.p2p.P2PService;
import bisq.network.p2p.storage.HashMapChangedListener;
import bisq.network.p2p.storage.payload.ProtectedStorageEntry;

import bisq.common.UserThread;
import bisq.common.config.Config;
import bisq.common.file.JsonFileManager;
import bisq.common.handlers.ErrorMessageHandler;
import bisq.common.handlers.ResultHandler;

import javax.inject.Inject;
import javax.inject.Named;

import java.io.File;

import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import lombok.extern.slf4j.Slf4j;

import javax.annotation.Nullable;

/**
 * Handles storage and retrieval of offers.
 * Uses an invalidation flag to only request the full offer map in case there was a change (anyone has added or removed an offer).
 */
@Slf4j
public class OfferBookService {

    public interface OfferBookChangedListener {
        void onAdded(Offer offer);

        void onRemoved(Offer offer);
    }

    private final P2PService p2PService;
    private final PriceFeedService priceFeedService;
    private final List<OfferBookChangedListener> offerBookChangedListeners = new LinkedList<>();
    private final FilterManager filterManager;
    private final JsonFileManager jsonFileManager;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    public OfferBookService(P2PService p2PService,
                            PriceFeedService priceFeedService,
                            FilterManager filterManager,
                            @Named(Config.STORAGE_DIR) File storageDir,
                            @Named(Config.DUMP_STATISTICS) boolean dumpStatistics) {
        this.p2PService = p2PService;
        this.priceFeedService = priceFeedService;
        this.filterManager = filterManager;
        jsonFileManager = new JsonFileManager(storageDir);

        p2PService.addHashSetChangedListener(new HashMapChangedListener() {
            @Override
            public void onAdded(Collection<ProtectedStorageEntry> protectedStorageEntries) {
                protectedStorageEntries.forEach(protectedStorageEntry -> offerBookChangedListeners.forEach(listener -> {
                    if (protectedStorageEntry.getProtectedStoragePayload() instanceof OfferPayloadBase) {
                        OfferPayloadBase offerPayloadBase = (OfferPayloadBase) protectedStorageEntry.getProtectedStoragePayload();
                        Offer offer = new Offer(offerPayloadBase);
                        offer.setPriceFeedService(priceFeedService);
                        listener.onAdded(offer);
                    }
                }));
            }

            @Override
            public void onRemoved(Collection<ProtectedStorageEntry> protectedStorageEntries) {
                protectedStorageEntries.forEach(protectedStorageEntry -> offerBookChangedListeners.forEach(listener -> {
                    if (protectedStorageEntry.getProtectedStoragePayload() instanceof OfferPayloadBase) {
                        OfferPayloadBase offerPayloadBase = (OfferPayloadBase) protectedStorageEntry.getProtectedStoragePayload();
                        Offer offer = new Offer(offerPayloadBase);
                        offer.setPriceFeedService(priceFeedService);
                        listener.onRemoved(offer);
                    }
                }));
            }
        });

        if (dumpStatistics) {
            p2PService.addP2PServiceListener(new BootstrapListener() {
                @Override
                public void onUpdatedDataReceived() {
                    addOfferBookChangedListener(new OfferBookChangedListener() {
                        @Override
                        public void onAdded(Offer offer) {
                            doDumpStatistics();
                        }

                        @Override
                        public void onRemoved(Offer offer) {
                            doDumpStatistics();
                        }
                    });
                    UserThread.runAfter(OfferBookService.this::doDumpStatistics, 1);
                }
            });
        }
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // API
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void addOffer(Offer offer, ResultHandler resultHandler, ErrorMessageHandler errorMessageHandler) {
        if (filterManager.requireUpdateToNewVersionForTrading()) {
            errorMessageHandler.handleErrorMessage(Res.get("popup.warning.mandatoryUpdate.trading"));
            return;
        }

        boolean result = p2PService.addProtectedStorageEntry(offer.getOfferPayloadBase());
        if (result) {
            resultHandler.handleResult();
        } else {
            errorMessageHandler.handleErrorMessage("Add offer failed");
        }
    }

    public void refreshTTL(OfferPayloadBase offerPayloadBase,
                           ResultHandler resultHandler,
                           ErrorMessageHandler errorMessageHandler) {
        if (filterManager.requireUpdateToNewVersionForTrading()) {
            errorMessageHandler.handleErrorMessage(Res.get("popup.warning.mandatoryUpdate.trading"));
            return;
        }

        boolean result = p2PService.refreshTTL(offerPayloadBase);
        if (result) {
            resultHandler.handleResult();
        } else {
            errorMessageHandler.handleErrorMessage("Refresh TTL failed.");
        }
    }

    public void activateOffer(Offer offer,
                              @Nullable ResultHandler resultHandler,
                              @Nullable ErrorMessageHandler errorMessageHandler) {
        addOffer(offer, resultHandler, errorMessageHandler);
    }

    public void deactivateOffer(OfferPayloadBase offerPayloadBase,
                                @Nullable ResultHandler resultHandler,
                                @Nullable ErrorMessageHandler errorMessageHandler) {
        removeOffer(offerPayloadBase, resultHandler, errorMessageHandler);
    }

    public void removeOffer(OfferPayloadBase offerPayloadBase,
                            @Nullable ResultHandler resultHandler,
                            @Nullable ErrorMessageHandler errorMessageHandler) {
        if (p2PService.removeData(offerPayloadBase)) {
            if (resultHandler != null)
                resultHandler.handleResult();
        } else {
            if (errorMessageHandler != null)
                errorMessageHandler.handleErrorMessage("Remove offer failed");
        }
    }

    public List<Offer> getOffers() {
        return p2PService.getDataMap().values().stream()
                .filter(data -> data.getProtectedStoragePayload() instanceof OfferPayloadBase)
                .map(data -> {
                    OfferPayloadBase offerPayloadBase = (OfferPayloadBase) data.getProtectedStoragePayload();
                    Offer offer = new Offer(offerPayloadBase);
                    offer.setPriceFeedService(priceFeedService);
                    return offer;
                })
                .collect(Collectors.toList());
    }

    public void removeOfferAtShutDown(OfferPayloadBase offerPayloadBase) {
        removeOffer(offerPayloadBase, null, null);
    }

    public boolean isBootstrapped() {
        return p2PService.isBootstrapped();
    }

    public void addOfferBookChangedListener(OfferBookChangedListener offerBookChangedListener) {
        offerBookChangedListeners.add(offerBookChangedListener);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Private
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void doDumpStatistics() {
        // We filter the case that it is a MarketBasedPrice but the price is not available
        // That should only be possible if the price feed provider is not available
        final List<OfferForJson> offerForJsonList = getOffers().stream()
                .filter(offer -> !offer.isUseMarketBasedPrice() || priceFeedService.getMarketPrice(offer.getCurrencyCode()) != null)
                .map(offer -> {
                    try {
                        return new OfferForJson(offer.getDirection(),
                                offer.getCurrencyCode(),
                                offer.getMinAmount(),
                                offer.getAmount(),
                                offer.getPrice(),
                                offer.getDate(),
                                offer.getId(),
                                offer.isUseMarketBasedPrice(),
                                offer.getMarketPriceMargin(),
                                offer.getPaymentMethod()
                        );
                    } catch (Throwable t) {
                        // In case an offer was corrupted with null values we ignore it
                        return null;
                    }
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
        jsonFileManager.writeToDiscThreaded(JsonUtil.objectToJson(offerForJsonList), "offers_statistics");
    }
}
