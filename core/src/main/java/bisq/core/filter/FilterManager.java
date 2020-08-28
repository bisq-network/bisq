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

package bisq.core.filter;

import bisq.core.btc.nodes.BtcNodes;
import bisq.core.payment.payload.PaymentAccountPayload;
import bisq.core.payment.payload.PaymentMethod;
import bisq.core.provider.ProvidersRepository;
import bisq.core.user.Preferences;
import bisq.core.user.User;

import bisq.network.p2p.NodeAddress;
import bisq.network.p2p.P2PService;
import bisq.network.p2p.P2PServiceListener;
import bisq.network.p2p.storage.HashMapChangedListener;
import bisq.network.p2p.storage.payload.ProtectedStorageEntry;

import bisq.common.app.DevEnv;
import bisq.common.app.Version;
import bisq.common.config.Config;
import bisq.common.config.ConfigFileEditor;
import bisq.common.crypto.KeyRing;

import org.bitcoinj.core.ECKey;
import org.bitcoinj.core.Sha256Hash;
import org.bitcoinj.core.Utils;

import javax.inject.Inject;
import javax.inject.Named;

import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;

import org.spongycastle.util.encoders.Base64;

import java.security.PublicKey;

import java.nio.charset.StandardCharsets;

import java.math.BigInteger;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import java.lang.reflect.Method;

import lombok.extern.slf4j.Slf4j;

import javax.annotation.Nullable;

import static com.google.common.base.Preconditions.checkNotNull;
import static org.bitcoinj.core.Utils.HEX;

/**
 * We only support one active filter, if we receive multiple we use the one with the more recent creationDate.
 */
@Slf4j
public class FilterManager {
    private static final String BANNED_PRICE_RELAY_NODES = "bannedPriceRelayNodes";
    private static final String BANNED_SEED_NODES = "bannedSeedNodes";
    private static final String BANNED_BTC_NODES = "bannedBtcNodes";


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
    private final ConfigFileEditor configFileEditor;
    private final ProvidersRepository providersRepository;
    private final boolean ignoreDevMsg;
    private final ObjectProperty<Filter> filterProperty = new SimpleObjectProperty<>();
    private final List<Listener> listeners = new CopyOnWriteArrayList<>();
    private final String pubKeyAsHex;

    private ECKey filterSigningKey;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    public FilterManager(P2PService p2PService,
                         KeyRing keyRing,
                         User user,
                         Preferences preferences,
                         Config config,
                         ProvidersRepository providersRepository,
                         @Named(Config.IGNORE_DEV_MSG) boolean ignoreDevMsg,
                         @Named(Config.USE_DEV_PRIVILEGE_KEYS) boolean useDevPrivilegeKeys) {
        this.p2PService = p2PService;
        this.keyRing = keyRing;
        this.user = user;
        this.preferences = preferences;
        this.configFileEditor = new ConfigFileEditor(config.configFile);
        this.providersRepository = providersRepository;
        this.ignoreDevMsg = ignoreDevMsg;

        pubKeyAsHex = useDevPrivilegeKeys ?
                DevEnv.DEV_PRIVILEGE_PUB_KEY :
                "022ac7b7766b0aedff82962522c2c14fb8d1961dabef6e5cfd10edc679456a32f1";
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // API
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void onAllServicesInitialized() {
        if (ignoreDevMsg) {
            return;
        }

        p2PService.getP2PDataStorage().getMap().values().stream()
                .map(ProtectedStorageEntry::getProtectedStoragePayload)
                .filter(protectedStoragePayload -> protectedStoragePayload instanceof Filter)
                .map(protectedStoragePayload -> (Filter) protectedStoragePayload)
                .filter(this::verifySignature)
                .forEach(this::onFilterAddedFromNetwork);

        p2PService.addHashSetChangedListener(new HashMapChangedListener() {
            @Override
            public void onAdded(Collection<ProtectedStorageEntry> protectedStorageEntries) {
                protectedStorageEntries.stream()
                        .filter(protectedStorageEntry -> protectedStorageEntry.getProtectedStoragePayload() instanceof Filter)
                        .forEach(protectedStorageEntry -> {
                            Filter filter = (Filter) protectedStorageEntry.getProtectedStoragePayload();
                            if (verifySignature(filter)) {
                                onFilterAddedFromNetwork(filter);
                            }
                        });
            }

            @Override
            public void onRemoved(Collection<ProtectedStorageEntry> protectedStorageEntries) {
                protectedStorageEntries.stream()
                        .filter(protectedStorageEntry -> protectedStorageEntry.getProtectedStoragePayload() instanceof Filter)
                        .forEach(protectedStorageEntry -> {
                            Filter filter = (Filter) protectedStorageEntry.getProtectedStoragePayload();
                            if (verifySignature(filter)) {
                                onFilterRemovedFromNetwork(filter);
                            }
                        });
            }
        });

        p2PService.addP2PServiceListener(new P2PServiceListener() {
            @Override
            public void onDataReceived() {
            }

            @Override
            public void onNoSeedNodeAvailable() {
            }

            @Override
            public void onNoPeersAvailable() {
            }

            @Override
            public void onUpdatedDataReceived() {
                // We should have received all data at that point and if the filters were not set we
                // clean up the persisted banned nodes in the options file as it might be that we missed the filter
                // remove message if we have not been online.
                if (filterProperty.get() == null) {
                    clearBannedNodes();
                }
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

    public boolean isValidDevPrivilegeKey(String privKeyString) {
        try {
            filterSigningKey = ECKey.fromPrivate(new BigInteger(1, HEX.decode(privKeyString)));
            return pubKeyAsHex.equals(Utils.HEX.encode(filterSigningKey.getPubKey()));
        } catch (Throwable t) {
            return false;
        }
    }

    public void publishFilter(Filter filterWithoutSig) {
        String signatureAsBase64 = getSignature(filterWithoutSig);
        Filter filterWithSig = Filter.cloneWithSig(filterWithoutSig, signatureAsBase64);
        user.setDevelopersFilter(filterWithSig);

        p2PService.addProtectedStorageEntry(filterWithSig);
    }


    public void removeFilter() {
        Filter filterWithSig = user.getDevelopersFilter();
        if (filterWithSig == null) {
            // Should not happen as UI button is deactivated in that case
            return;
        }

        if (p2PService.removeData(filterWithSig)) {
            user.setDevelopersFilter(null);
        } else {
            log.warn("Removing dev filter from network failed");
        }
    }

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

    @Nullable
    public Filter getDevelopersFilter() {
        return user.getDevelopersFilter();
    }

    public boolean isCurrencyBanned(String currencyCode) {
        return getFilter() != null &&
                getFilter().getBannedCurrencies() != null &&
                getFilter().getBannedCurrencies().stream()
                        .anyMatch(e -> e.equals(currencyCode));
    }

    public boolean isPaymentMethodBanned(PaymentMethod paymentMethod) {
        return getFilter() != null &&
                getFilter().getBannedPaymentMethods() != null &&
                getFilter().getBannedPaymentMethods().stream()
                        .anyMatch(e -> e.equals(paymentMethod.getId()));
    }

    public boolean isOfferIdBanned(String offerId) {
        return getFilter() != null &&
                getFilter().getBannedOfferIds().stream()
                        .anyMatch(e -> e.equals(offerId));
    }

    public boolean isNodeAddressBanned(NodeAddress nodeAddress) {
        return getFilter() != null &&
                getFilter().getBannedNodeAddress().stream()
                        .anyMatch(e -> e.equals(nodeAddress.getFullAddress()));
    }

    public boolean requireUpdateToNewVersionForTrading() {
        if (getFilter() == null) {
            return false;
        }

        boolean requireUpdateToNewVersion = false;
        String getDisableTradeBelowVersion = getFilter().getDisableTradeBelowVersion();
        if (getDisableTradeBelowVersion != null && !getDisableTradeBelowVersion.isEmpty()) {
            requireUpdateToNewVersion = Version.isNewVersion(getDisableTradeBelowVersion);
        }

        return requireUpdateToNewVersion;
    }

    public boolean requireUpdateToNewVersionForDAO() {
        if (getFilter() == null) {
            return false;
        }

        boolean requireUpdateToNewVersion = false;
        String disableDaoBelowVersion = getFilter().getDisableDaoBelowVersion();
        if (disableDaoBelowVersion != null && !disableDaoBelowVersion.isEmpty()) {
            requireUpdateToNewVersion = Version.isNewVersion(disableDaoBelowVersion);
        }

        return requireUpdateToNewVersion;
    }

    public boolean arePeersPaymentAccountDataBanned(PaymentAccountPayload paymentAccountPayload,
                                                    PaymentAccountFilter[] appliedPaymentAccountFilter) {
        return getFilter() != null &&
                getFilter().getBannedPaymentAccounts().stream()
                        .anyMatch(paymentAccountFilter -> {
                            final boolean samePaymentMethodId = paymentAccountFilter.getPaymentMethodId().equals(
                                    paymentAccountPayload.getPaymentMethodId());
                            if (samePaymentMethodId) {
                                try {
                                    Method method = paymentAccountPayload.getClass().getMethod(paymentAccountFilter.getGetMethodName());
                                    String result = (String) method.invoke(paymentAccountPayload);
                                    appliedPaymentAccountFilter[0] = paymentAccountFilter;
                                    return result.toLowerCase().equals(paymentAccountFilter.getValue().toLowerCase());
                                } catch (Throwable e) {
                                    log.error(e.getMessage());
                                    return false;
                                }
                            } else {
                                return false;
                            }
                        });
    }

    public boolean isWitnessSignerPubKeyBanned(String witnessSignerPubKeyAsHex) {
        return getFilter() != null &&
                getFilter().getBannedSignerPubKeys() != null &&
                getFilter().getBannedSignerPubKeys().stream()
                        .anyMatch(e -> e.equals(witnessSignerPubKeyAsHex));
    }

    public PublicKey getOwnerPubKey() {
        return keyRing.getSignatureKeyPair().getPublic();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Private
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void onFilterAddedFromNetwork(Filter filter) {
        if (filterProperty.get() != null && filterProperty.get().getCreationDate() > filter.getCreationDate()) {
            log.warn("We received a new filter from the network but the creation date is older than the " +
                            "filter we have already. We ignore the new filter.\n" +
                            "New filer={}\n" +
                            "Old filter={}",
                    filter, filterProperty.get());
            return;
        }

        // Our new filter is newer so we apply it.
        // We do not require strict guarantees here (e.g. clocks not synced) as only trusted developers have the key
        // for deploying filters and this is only in place to avoid unintended situations of multiple filters
        // from multiple devs or if same dev publishes new filter from different app without the persisted devFilter.
        filterProperty.set(filter);

        // Seed nodes are requested at startup before we get the filter so we only apply the banned
        // nodes at the next startup and don't update the list in the P2P network domain.
        // We persist it to the property file which is read before any other initialisation.
        saveBannedNodes(BANNED_SEED_NODES, filter.getSeedNodes());
        saveBannedNodes(BANNED_BTC_NODES, filter.getBtcNodes());

        // Banned price relay nodes we can apply at runtime
        List<String> priceRelayNodes = filter.getPriceRelayNodes();
        saveBannedNodes(BANNED_PRICE_RELAY_NODES, priceRelayNodes);
        providersRepository.applyBannedNodes(priceRelayNodes);

        if (filter.isPreventPublicBtcNetwork() &&
                preferences.getBitcoinNodesOptionOrdinal() == BtcNodes.BitcoinNodesOption.PUBLIC.ordinal()) {
            preferences.setBitcoinNodesOptionOrdinal(BtcNodes.BitcoinNodesOption.PROVIDED.ordinal());
        }

        listeners.forEach(e -> e.onFilterAdded(filter));
    }

    // We clean up potentially banned nodes and set value of filter property to null
    private void onFilterRemovedFromNetwork(Filter filter) {
        if (!filterProperty.get().equals(filter)) {
            return;
        }

        clearBannedNodes();

        if (filter.equals(user.getDevelopersFilter())) {
            user.setDevelopersFilter(null);
        }
        filterProperty.set(null);
    }

    // Clears options files from banned nodes
    private void clearBannedNodes() {
        saveBannedNodes(BANNED_BTC_NODES, null);
        saveBannedNodes(BANNED_SEED_NODES, null);
        saveBannedNodes(BANNED_PRICE_RELAY_NODES, null);

        if (providersRepository.getBannedNodes() != null) {
            providersRepository.applyBannedNodes(null);
        }
    }

    private void saveBannedNodes(String optionName, List<String> bannedNodes) {
        if (bannedNodes != null)
            configFileEditor.setOption(optionName, String.join(",", bannedNodes));
        else
            configFileEditor.clearOption(optionName);
    }

    private String getSignature(Filter filterWithoutSig) {
        Sha256Hash hash = getSha256Hash(filterWithoutSig);
        ECKey.ECDSASignature ecdsaSignature = filterSigningKey.sign(hash);
        byte[] encodeToDER = ecdsaSignature.encodeToDER();
        return new String(Base64.encode(encodeToDER), StandardCharsets.UTF_8);
    }

    private boolean verifySignature(Filter filter) {
        try {
            Filter filterForSigVerification = Filter.cloneWithoutSig(filter);
            Sha256Hash hash = getSha256Hash(filterForSigVerification);

            checkNotNull(filter.getSignatureAsBase64(), "filter.getSignatureAsBase64() must not be null");
            byte[] sigData = Base64.decode(filter.getSignatureAsBase64());
            ECKey.ECDSASignature ecdsaSignature = ECKey.ECDSASignature.decodeFromDER(sigData);

            ECKey ecPubKey = ECKey.fromPublicOnly(HEX.decode(pubKeyAsHex));
            return ecPubKey.verify(hash, ecdsaSignature);
        } catch (Throwable e) {
            log.warn("verifySignature failed. filter={}", filter);
            return false;
        }
    }

    private Sha256Hash getSha256Hash(Filter filter) {
        byte[] filterData = filter.toProtoMessage().toByteArray();
        return Sha256Hash.of(filterData);
    }
}
