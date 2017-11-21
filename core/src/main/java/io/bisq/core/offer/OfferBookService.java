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

package io.bisq.core.offer;

import com.google.inject.name.Named;
import io.bisq.common.UserThread;
import io.bisq.common.handlers.ErrorMessageHandler;
import io.bisq.common.handlers.ResultHandler;
import io.bisq.common.storage.JsonFileManager;
import io.bisq.common.storage.Storage;
import io.bisq.common.util.Utilities;
import io.bisq.core.app.AppOptionKeys;
import io.bisq.core.provider.price.PriceFeedService;
import io.bisq.network.p2p.BootstrapListener;
import io.bisq.network.p2p.P2PService;
import io.bisq.network.p2p.storage.HashMapChangedListener;
import io.bisq.network.p2p.storage.payload.ProtectedStorageEntry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import javax.inject.Inject;
import java.io.File;
import java.util.LinkedList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Handles storage and retrieval of offers.
 * Uses an invalidation flag to only request the full offer map in case there was a change (anyone has added or removed an offer).
 */
public class OfferBookService {
    private static final Logger log = LoggerFactory.getLogger(OfferBookService.class);

    public interface OfferBookChangedListener {
        void onAdded(Offer offer);

        void onRemoved(Offer offer);
    }

    private final P2PService p2PService;
    private final PriceFeedService priceFeedService;
    private final List<OfferBookChangedListener> offerBookChangedListeners = new LinkedList<>();
    private final JsonFileManager jsonFileManager;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    public OfferBookService(P2PService p2PService,
                            PriceFeedService priceFeedService,
                            @Named(Storage.STORAGE_DIR) File storageDir,
                            @Named(AppOptionKeys.DUMP_STATISTICS) boolean dumpStatistics) {
        this.p2PService = p2PService;
        this.priceFeedService = priceFeedService;
        jsonFileManager = new JsonFileManager(storageDir);

        p2PService.addHashSetChangedListener(new HashMapChangedListener() {
            @Override
            public void onAdded(ProtectedStorageEntry data) {
                offerBookChangedListeners.stream().forEach(listener -> {
                    if (data.getProtectedStoragePayload() instanceof OfferPayload) {
                        OfferPayload offerPayload = (OfferPayload) data.getProtectedStoragePayload();
                        Offer offer = new Offer(offerPayload);
                        offer.setPriceFeedService(priceFeedService);
                        listener.onAdded(offer);
                    }
                });
            }

            @Override
            public void onRemoved(ProtectedStorageEntry data) {
                offerBookChangedListeners.stream().forEach(listener -> {
                    if (data.getProtectedStoragePayload() instanceof OfferPayload) {
                        OfferPayload offerPayload = (OfferPayload) data.getProtectedStoragePayload();
                        Offer offer = new Offer(offerPayload);
                        offer.setPriceFeedService(priceFeedService);
                        listener.onRemoved(offer);
                    }
                });
            }
        });

        if (dumpStatistics) {
            p2PService.addP2PServiceListener(new BootstrapListener() {
                @Override
                public void onBootstrapComplete() {
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
        boolean result = p2PService.addProtectedStorageEntry(offer.getOfferPayload(), true);
        if (result) {
            log.trace("Add offer to network was successful. OfferPayload ID = " + offer.getId());
            resultHandler.handleResult();
        } else {
            errorMessageHandler.handleErrorMessage("Add offer failed");
        }
    }

    public void refreshTTL(OfferPayload offerPayload, ResultHandler resultHandler, ErrorMessageHandler errorMessageHandler) {
        boolean result = p2PService.refreshTTL(offerPayload, true);
        if (result) {
            log.trace("Refresh TTL was successful. OfferPayload ID = " + offerPayload.getId());
            resultHandler.handleResult();
        } else {
            errorMessageHandler.handleErrorMessage("Refresh TTL failed.");
        }
    }

    public void removeOffer(OfferPayload offerPayload, @Nullable ResultHandler resultHandler, @Nullable ErrorMessageHandler errorMessageHandler) {
        if (p2PService.removeData(offerPayload, true)) {
            log.trace("Remove offer from network was successful. OfferPayload ID = " + offerPayload.getId());
            if (resultHandler != null)
                resultHandler.handleResult();
        } else {
            if (errorMessageHandler != null)
                errorMessageHandler.handleErrorMessage("Remove offer failed");
        }
    }

    public List<Offer> getOffers() {
        return p2PService.getDataMap().values().stream()
                .filter(data -> data.getProtectedStoragePayload() instanceof OfferPayload)
                .map(data -> {
                    OfferPayload offerPayload = (OfferPayload) data.getProtectedStoragePayload();
                    Offer offer = new Offer(offerPayload);
                    offer.setPriceFeedService(priceFeedService);
                    return offer;
                })
                .collect(Collectors.toList());
    }

    public void removeOfferAtShutDown(OfferPayload offerPayload) {
        log.debug("removeOfferAtShutDown " + offerPayload);
        removeOffer(offerPayload, null, null);
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
                                offer.getPaymentMethod(),
                                offer.getOfferFeePaymentTxId()
                        );
                    } catch (Throwable t) {
                        // In case a offer was corrupted with null values we ignore it
                        return null;
                    }
                })
                .filter(e -> e != null)
                .collect(Collectors.toList());
        jsonFileManager.writeToDisc(Utilities.objectToJson(offerForJsonList), "offers_statistics");
    }
}
