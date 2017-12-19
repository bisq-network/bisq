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

package io.bisq.core.filter;

import com.google.inject.Inject;
import com.google.inject.name.Named;
import io.bisq.common.UserThread;
import io.bisq.common.app.DevEnv;
import io.bisq.common.crypto.KeyRing;
import io.bisq.core.app.AppOptionKeys;
import io.bisq.core.app.BisqEnvironment;
import io.bisq.core.btc.BitcoinNodes;
import io.bisq.core.payment.payload.PaymentAccountPayload;
import io.bisq.core.payment.payload.PaymentMethod;
import io.bisq.core.provider.ProvidersRepository;
import io.bisq.core.user.Preferences;
import io.bisq.core.user.User;
import io.bisq.generated.protobuffer.PB;
import io.bisq.network.p2p.NodeAddress;
import io.bisq.network.p2p.P2PService;
import io.bisq.network.p2p.P2PServiceListener;
import io.bisq.network.p2p.storage.HashMapChangedListener;
import io.bisq.network.p2p.storage.payload.ProtectedStorageEntry;
import io.bisq.network.p2p.storage.payload.ProtectedStoragePayload;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import org.bitcoinj.core.ECKey;
import org.bitcoinj.core.Utils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.lang.reflect.Method;
import java.math.BigInteger;
import java.security.SignatureException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import static org.bitcoinj.core.Utils.HEX;

public class FilterManager {
    private static final Logger log = LoggerFactory.getLogger(FilterManager.class);

    public static final String BANNED_PRICE_RELAY_NODES = "bannedPriceRelayNodes";
    public static final String BANNED_SEED_NODES = "bannedSeedNodes";
    public static final String BANNED_BTC_NODES = "bannedBtcNodes";


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Listener
    ///////////////////////////////////////////////////////////////////////////////////////////

    public interface Listener {
        void onFilterAdded(Filter filter);
    }

    private final P2PService p2PService;
    private final KeyRing keyRing;
    private final User user;
    private final Preferences preferences;
    private final BisqEnvironment bisqEnvironment;
    private final ProvidersRepository providersRepository;
    private boolean ignoreDevMsg;
    private final ObjectProperty<Filter> filterProperty = new SimpleObjectProperty<>();
    private final List<Listener> listeners = new ArrayList<>();

    @SuppressWarnings("ConstantConditions")
    private static final String pubKeyAsHex = DevEnv.USE_DEV_PRIVILEGE_KEYS ?
            DevEnv.DEV_PRIVILEGE_PUB_KEY :
            "022ac7b7766b0aedff82962522c2c14fb8d1961dabef6e5cfd10edc679456a32f1";
    private ECKey filterSigningKey;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor, Initialization
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    public FilterManager(P2PService p2PService,
                         KeyRing keyRing,
                         User user,
                         Preferences preferences,
                         BisqEnvironment bisqEnvironment,
                         ProvidersRepository providersRepository,
                         @Named(AppOptionKeys.IGNORE_DEV_MSG_KEY) boolean ignoreDevMsg) {
        this.p2PService = p2PService;
        this.keyRing = keyRing;
        this.user = user;
        this.preferences = preferences;
        this.bisqEnvironment = bisqEnvironment;
        this.providersRepository = providersRepository;
        this.ignoreDevMsg = ignoreDevMsg;
    }

    public void onAllServicesInitialized() {
        if (!ignoreDevMsg) {

            final List<ProtectedStorageEntry> list = new ArrayList<>(p2PService.getP2PDataStorage().getMap().values());
            list.forEach(e -> {
                final ProtectedStoragePayload protectedStoragePayload = e.getProtectedStoragePayload();
                if (protectedStoragePayload instanceof Filter)
                    addFilter((Filter) protectedStoragePayload);
            });

            p2PService.addHashSetChangedListener(new HashMapChangedListener() {
                @Override
                public void onAdded(ProtectedStorageEntry data) {
                    if (data.getProtectedStoragePayload() instanceof Filter) {
                        Filter filter = (Filter) data.getProtectedStoragePayload();
                        addFilter(filter);
                    }
                }

                @Override
                public void onRemoved(ProtectedStorageEntry data) {
                    if (data.getProtectedStoragePayload() instanceof Filter) {
                        Filter filter = (Filter) data.getProtectedStoragePayload();
                        if (verifySignature(filter))
                            resetFilters();
                    }
                }
            });
        }

        p2PService.addP2PServiceListener(new P2PServiceListener() {
            @Override
            public void onRequestingDataCompleted() {
                // We should have received all data at that point and if the filers was not set we
                // clean up as it might be that we missed the filter remove message if we have not been online.
                UserThread.runAfter(() -> {
                    if (filterProperty.get() == null)
                        resetFilters();
                }, 30);
            }

            @Override
            public void onNoSeedNodeAvailable() {
            }

            @Override
            public void onNoPeersAvailable() {
            }

            @Override
            public void onBootstrapComplete() {
            }

            @Override
            public void onTorNodeReady() {
            }

            @Override
            public void onHiddenServicePublished() {
            }

            @Override
            public void onSetupFailed(Throwable throwable) {
            }

            @Override
            public void onRequestCustomBridges() {
            }
        });
    }

    private void resetFilters() {
        bisqEnvironment.saveBannedBtcNodes(null);
        bisqEnvironment.saveBannedSeedNodes(null);
        bisqEnvironment.saveBannedPriceRelayNodes(null);
        providersRepository.applyBannedNodes(null);
        providersRepository.selectNewRandomBaseUrl();
        filterProperty.set(null);
    }

    private void addFilter(Filter filter) {
        if (verifySignature(filter)) {
            // Seed nodes are requested at startup before we get the filter so we only apply the banned
            // nodes at the next startup and don't update the list in the P2P network domain.
            // We persist it to the property file which is read before any other initialisation.
            bisqEnvironment.saveBannedSeedNodes(filter.getSeedNodes());
            bisqEnvironment.saveBannedBtcNodes(filter.getBtcNodes());

            // Banned price relay nodes we can apply at runtime
            final List<String> priceRelayNodes = filter.getPriceRelayNodes();
            bisqEnvironment.saveBannedPriceRelayNodes(priceRelayNodes);
            providersRepository.applyBannedNodes(priceRelayNodes);
            providersRepository.selectNewRandomBaseUrl();

            filterProperty.set(filter);
            listeners.stream().forEach(e -> e.onFilterAdded(filter));

            if (filter.isPreventPublicBtcNetwork() &&
                    preferences.getBitcoinNodesOptionOrdinal() == BitcoinNodes.BitcoinNodesOption.PUBLIC.ordinal())
                preferences.setBitcoinNodesOptionOrdinal(BitcoinNodes.BitcoinNodesOption.PROVIDED.ordinal());
        }
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // API
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void addListener(Listener listener) {
        listeners.add(listener);
    }

    public ObjectProperty<Filter> filterProperty() {
        return filterProperty;
    }

    @Nullable
    public Filter getFilter() {
        return filterProperty.get();
    }

    public boolean addFilterMessageIfKeyIsValid(Filter filter, String privKeyString) {
        // if there is a previous message we remove that first
        if (user.getDevelopersFilter() != null)
            removeFilterMessageIfKeyIsValid(privKeyString);

        boolean isKeyValid = isKeyValid(privKeyString);
        if (isKeyValid) {
            signAndAddSignatureToFilter(filter);
            user.setDevelopersFilter(filter);

            boolean result = p2PService.addProtectedStorageEntry(filter, true);
            if (result)
                log.trace("Add filter to network was successful. FilterMessage = " + filter);

        }
        return isKeyValid;
    }

    public boolean removeFilterMessageIfKeyIsValid(String privKeyString) {
        if (isKeyValid(privKeyString)) {
            Filter filter = user.getDevelopersFilter();
            if (filter == null) {
                log.warn("Developers filter is null");
            } else if (p2PService.removeData(filter, true)) {
                log.trace("Remove filter from network was successful. FilterMessage = " + filter);
                user.setDevelopersFilter(null);
            } else {
                log.warn("Filter remove failed");
            }
            return true;
        } else {
            return false;
        }
    }

    private boolean isKeyValid(String privKeyString) {
        try {
            filterSigningKey = ECKey.fromPrivate(new BigInteger(1, HEX.decode(privKeyString)));
            return pubKeyAsHex.equals(Utils.HEX.encode(filterSigningKey.getPubKey()));
        } catch (Throwable t) {
            return false;
        }
    }

    private void signAndAddSignatureToFilter(Filter filter) {
        filter.setSigAndPubKey(filterSigningKey.signMessage(getHexFromData(filter)), keyRing.getSignatureKeyPair().getPublic());
    }

    private boolean verifySignature(Filter filter) {
        try {
            ECKey.fromPublicOnly(HEX.decode(pubKeyAsHex)).verifyMessage(getHexFromData(filter), filter.getSignatureAsBase64());
            return true;
        } catch (SignatureException e) {
            log.warn("verifySignature failed");
            return false;
        }
    }

    // We dont use full data from Filter as we are only interested in the filter data not the sig and keys
    private String getHexFromData(Filter filter) {
        PB.Filter.Builder builder = PB.Filter.newBuilder()
                .addAllBannedOfferIds(filter.getBannedOfferIds())
                .addAllBannedNodeAddress(filter.getBannedNodeAddress())
                .addAllBannedPaymentAccounts(filter.getBannedPaymentAccounts().stream()
                        .map(PaymentAccountFilter::toProtoMessage)
                        .collect(Collectors.toList()));

        Optional.ofNullable(filter.getBannedCurrencies()).ifPresent(builder::addAllBannedCurrencies);
        Optional.ofNullable(filter.getBannedPaymentMethods()).ifPresent(builder::addAllBannedPaymentMethods);

        return Utils.HEX.encode(builder.build().toByteArray());
    }

    @Nullable
    public Filter getDevelopersFilter() {
        return user.getDevelopersFilter();
    }

    public boolean isCurrencyBanned(String currencyCode) {
        return getFilter() != null &&
                getFilter().getBannedCurrencies() != null &&
                getFilter().getBannedCurrencies().stream()
                        .filter(e -> e.equals(currencyCode))
                        .findAny()
                        .isPresent();
    }

    public boolean isPaymentMethodBanned(PaymentMethod paymentMethod) {
        return getFilter() != null &&
                getFilter().getBannedPaymentMethods() != null &&
                getFilter().getBannedPaymentMethods().stream()
                        .filter(e -> e.equals(paymentMethod.getId()))
                        .findAny()
                        .isPresent();
    }

    public boolean isOfferIdBanned(String offerId) {
        return getFilter() != null &&
                getFilter().getBannedOfferIds().stream()
                        .filter(e -> e.equals(offerId))
                        .findAny()
                        .isPresent();
    }

    public boolean isNodeAddressBanned(NodeAddress nodeAddress) {
        return getFilter() != null &&
                getFilter().getBannedNodeAddress().stream()
                        .filter(e -> e.equals(nodeAddress.getFullAddress()))
                        .findAny()
                        .isPresent();
    }

    public boolean isPeersPaymentAccountDataAreBanned(PaymentAccountPayload paymentAccountPayload,
                                                      PaymentAccountFilter[] appliedPaymentAccountFilter) {
        return getFilter() != null &&
                getFilter().getBannedPaymentAccounts().stream()
                        .filter(paymentAccountFilter -> {
                            final boolean samePaymentMethodId = paymentAccountFilter.getPaymentMethodId().equals(
                                    paymentAccountPayload.getPaymentMethodId());
                            if (samePaymentMethodId) {
                                try {
                                    Method method = paymentAccountPayload.getClass().getMethod(paymentAccountFilter.getGetMethodName());
                                    String result = (String) method.invoke(paymentAccountPayload);
                                    appliedPaymentAccountFilter[0] = paymentAccountFilter;
                                    return result.equals(paymentAccountFilter.getValue());
                                } catch (Throwable e) {
                                    log.error(e.getMessage());
                                    return false;
                                }
                            } else {
                                return false;
                            }
                        })
                        .findAny()
                        .isPresent();
    }
}
