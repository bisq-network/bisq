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
import bisq.core.locale.Res;
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

import javax.inject.Inject;
import javax.inject.Named;

import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;

import org.bouncycastle.util.encoders.Base64;

import java.security.PublicKey;

import java.nio.charset.StandardCharsets;

import java.math.BigInteger;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

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
    private final List<String> publicKeys;
    private ECKey filterSigningKey;
    private final Set<Filter> invalidFilters = new HashSet<>();


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

        publicKeys = useDevPrivilegeKeys ?
                Collections.singletonList(DevEnv.DEV_PRIVILEGE_PUB_KEY) :
                List.of("0358d47858acdc41910325fce266571540681ef83a0d6fedce312bef9810793a27",
                        "029340c3e7d4bb0f9e651b5f590b434fecb6175aeaa57145c7804ff05d210e534f",
                        "034dc7530bf66ffd9580aa98031ea9a18ac2d269f7c56c0e71eca06105b9ed69f9");

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
                .forEach(this::onFilterAddedFromNetwork);

        p2PService.addHashSetChangedListener(new HashMapChangedListener() {
            @Override
            public void onAdded(Collection<ProtectedStorageEntry> protectedStorageEntries) {
                protectedStorageEntries.stream()
                        .filter(protectedStorageEntry -> protectedStorageEntry.getProtectedStoragePayload() instanceof Filter)
                        .forEach(protectedStorageEntry -> {
                            Filter filter = (Filter) protectedStorageEntry.getProtectedStoragePayload();
                            onFilterAddedFromNetwork(filter);
                        });
            }

            @Override
            public void onRemoved(Collection<ProtectedStorageEntry> protectedStorageEntries) {
                protectedStorageEntries.stream()
                        .filter(protectedStorageEntry -> protectedStorageEntry.getProtectedStoragePayload() instanceof Filter)
                        .forEach(protectedStorageEntry -> {
                            Filter filter = (Filter) protectedStorageEntry.getProtectedStoragePayload();
                            onFilterRemovedFromNetwork(filter);
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

    public void setFilterWarningHandler(Consumer<String> filterWarningHandler) {
        addListener(filter -> {
            if (filter != null && filterWarningHandler != null) {
                if (filter.getSeedNodes() != null && !filter.getSeedNodes().isEmpty()) {
                    log.info(Res.get("popup.warning.nodeBanned", Res.get("popup.warning.seed")));
                    // Let's keep that more silent. Might be used in case a node is unstable and we don't want to confuse users.
                    // filterWarningHandler.accept(Res.get("popup.warning.nodeBanned", Res.get("popup.warning.seed")));
                }

                if (filter.getPriceRelayNodes() != null && !filter.getPriceRelayNodes().isEmpty()) {
                    log.info(Res.get("popup.warning.nodeBanned", Res.get("popup.warning.priceRelay")));
                    // Let's keep that more silent. Might be used in case a node is unstable and we don't want to confuse users.
                    // filterWarningHandler.accept(Res.get("popup.warning.nodeBanned", Res.get("popup.warning.priceRelay")));
                }

                if (requireUpdateToNewVersionForTrading()) {
                    filterWarningHandler.accept(Res.get("popup.warning.mandatoryUpdate.trading"));
                }

                if (requireUpdateToNewVersionForDAO()) {
                    filterWarningHandler.accept(Res.get("popup.warning.mandatoryUpdate.dao"));
                }
                if (filter.isDisableDao()) {
                    filterWarningHandler.accept(Res.get("popup.warning.disable.dao"));
                }
            }
        });
    }

    public boolean isPrivilegedDevPubKeyBanned(String pubKeyAsHex) {
        Filter filter = getFilter();
        if (filter == null) {
            return false;
        }

        return filter.getBannedPrivilegedDevPubKeys().contains(pubKeyAsHex);
    }

    public boolean canAddDevFilter(String privKeyString) {
        if (privKeyString == null || privKeyString.isEmpty()) {
            return false;
        }
        if (!isValidDevPrivilegeKey(privKeyString)) {
            log.warn("Key in invalid");
            return false;
        }

        ECKey ecKeyFromPrivate = toECKey(privKeyString);
        String pubKeyAsHex = getPubKeyAsHex(ecKeyFromPrivate);
        if (isPrivilegedDevPubKeyBanned(pubKeyAsHex)) {
            log.warn("Pub key is banned.");
            return false;
        }
        return true;
    }

    public String getSignerPubKeyAsHex(String privKeyString) {
        ECKey ecKey = toECKey(privKeyString);
        return getPubKeyAsHex(ecKey);
    }

    public void addDevFilter(Filter filterWithoutSig, String privKeyString) {
        setFilterSigningKey(privKeyString);
        String signatureAsBase64 = getSignature(filterWithoutSig);
        Filter filterWithSig = Filter.cloneWithSig(filterWithoutSig, signatureAsBase64);
        user.setDevelopersFilter(filterWithSig);

        p2PService.addProtectedStorageEntry(filterWithSig);

        // Cleanup potential old filters created in the past with same priv key
        invalidFilters.forEach(filter -> {
            removeInvalidFilters(filter, privKeyString);
        });
    }

    public void addToInvalidFilters(Filter filter) {
        invalidFilters.add(filter);
    }

    public void removeInvalidFilters(Filter filter, String privKeyString) {
        log.info("Remove invalid filter {}", filter);
        setFilterSigningKey(privKeyString);
        String signatureAsBase64 = getSignature(Filter.cloneWithoutSig(filter));
        Filter filterWithSig = Filter.cloneWithSig(filter, signatureAsBase64);
        boolean result = p2PService.removeData(filterWithSig);
        if (!result) {
            log.warn("Could not remove filter {}", filter);
        }
    }

    public boolean canRemoveDevFilter(String privKeyString) {
        if (privKeyString == null || privKeyString.isEmpty()) {
            return false;
        }

        Filter developersFilter = getDevFilter();
        if (developersFilter == null) {
            log.warn("There is no persisted dev filter to be removed.");
            return false;
        }

        if (!isValidDevPrivilegeKey(privKeyString)) {
            log.warn("Key in invalid.");
            return false;
        }

        ECKey ecKeyFromPrivate = toECKey(privKeyString);
        String pubKeyAsHex = getPubKeyAsHex(ecKeyFromPrivate);
        if (!developersFilter.getSignerPubKeyAsHex().equals(pubKeyAsHex)) {
            log.warn("pubKeyAsHex derived from private key does not match filterSignerPubKey. " +
                            "filterSignerPubKey={}, pubKeyAsHex derived from private key={}",
                    developersFilter.getSignerPubKeyAsHex(), pubKeyAsHex);
            return false;
        }

        if (isPrivilegedDevPubKeyBanned(pubKeyAsHex)) {
            log.warn("Pub key is banned.");
            return false;
        }

        return true;
    }

    public void removeDevFilter(String privKeyString) {
        setFilterSigningKey(privKeyString);
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
    public Filter getDevFilter() {
        return user.getDevelopersFilter();
    }

    public PublicKey getOwnerPubKey() {
        return keyRing.getSignatureKeyPair().getPublic();
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

    public boolean arePeersPaymentAccountDataBanned(PaymentAccountPayload paymentAccountPayload) {
        return getFilter() != null &&
                getFilter().getBannedPaymentAccounts().stream()
                        .filter(paymentAccountFilter -> paymentAccountFilter.getPaymentMethodId().equals(
                                paymentAccountPayload.getPaymentMethodId()))
                        .anyMatch(paymentAccountFilter -> {
                            try {
                                Method method = paymentAccountPayload.getClass().getMethod(paymentAccountFilter.getGetMethodName());
                                // We invoke getter methods (no args), e.g. getHolderName
                                String valueFromInvoke = (String) method.invoke(paymentAccountPayload);
                                return valueFromInvoke.equalsIgnoreCase(paymentAccountFilter.getValue());
                            } catch (Throwable e) {
                                log.error(e.getMessage());
                                return false;
                            }
                        });
    }

    public boolean isWitnessSignerPubKeyBanned(String witnessSignerPubKeyAsHex) {
        return getFilter() != null &&
                getFilter().getBannedAccountWitnessSignerPubKeys() != null &&
                getFilter().getBannedAccountWitnessSignerPubKeys().stream()
                        .anyMatch(e -> e.equals(witnessSignerPubKeyAsHex));
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Private
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void onFilterAddedFromNetwork(Filter newFilter) {
        Filter currentFilter = getFilter();

        if (!isFilterPublicKeyInList(newFilter)) {
            log.warn("isFilterPublicKeyInList failed. Filter={}", newFilter);
            return;
        }
        if (!isSignatureValid(newFilter)) {
            log.warn("verifySignature failed. Filter={}", newFilter);
            return;
        }

        if (currentFilter != null) {
            if (currentFilter.getCreationDate() > newFilter.getCreationDate()) {
                log.warn("We received a new filter from the network but the creation date is older than the " +
                                "filter we have already. We ignore the new filter.\n" +
                                "New filer={}\n" +
                                "Old filter={}",
                        newFilter, filterProperty.get());

                addToInvalidFilters(newFilter);
                return;
            } else {
                log.warn("We received a new filter from the network and the creation date is newer than the " +
                                "filter we have already. We ignore the old filter.\n" +
                                "New filer={}\n" +
                                "Old filter={}",
                        newFilter, filterProperty.get());
                addToInvalidFilters(currentFilter);
            }

            if (isPrivilegedDevPubKeyBanned(newFilter.getSignerPubKeyAsHex())) {
                log.warn("Pub key of filter is banned. currentFilter={}, newFilter={}", currentFilter, newFilter);
                return;
            }
        }

        // Our new filter is newer so we apply it.
        // We do not require strict guarantees here (e.g. clocks not synced) as only trusted developers have the key
        // for deploying filters and this is only in place to avoid unintended situations of multiple filters
        // from multiple devs or if same dev publishes new filter from different app without the persisted devFilter.
        filterProperty.set(newFilter);

        // Seed nodes are requested at startup before we get the filter so we only apply the banned
        // nodes at the next startup and don't update the list in the P2P network domain.
        // We persist it to the property file which is read before any other initialisation.
        saveBannedNodes(BANNED_SEED_NODES, newFilter.getSeedNodes());
        saveBannedNodes(BANNED_BTC_NODES, newFilter.getBtcNodes());

        // Banned price relay nodes we can apply at runtime
        List<String> priceRelayNodes = newFilter.getPriceRelayNodes();
        saveBannedNodes(BANNED_PRICE_RELAY_NODES, priceRelayNodes);

        //TODO should be moved to client with listening on onFilterAdded
        providersRepository.applyBannedNodes(priceRelayNodes);

        //TODO should be moved to client with listening on onFilterAdded
        if (newFilter.isPreventPublicBtcNetwork() &&
                preferences.getBitcoinNodesOptionOrdinal() == BtcNodes.BitcoinNodesOption.PUBLIC.ordinal()) {
            preferences.setBitcoinNodesOptionOrdinal(BtcNodes.BitcoinNodesOption.PROVIDED.ordinal());
        }

        listeners.forEach(e -> e.onFilterAdded(newFilter));
    }

    private void onFilterRemovedFromNetwork(Filter filter) {
        if (!isFilterPublicKeyInList(filter)) {
            log.warn("isFilterPublicKeyInList failed. Filter={}", filter);
            return;
        }
        if (!isSignatureValid(filter)) {
            log.warn("verifySignature failed. Filter={}", filter);
            return;
        }

        // We don't check for banned filter as we want to remove a banned filter anyway.

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

    private boolean isValidDevPrivilegeKey(String privKeyString) {
        try {
            ECKey filterSigningKey = toECKey(privKeyString);
            String pubKeyAsHex = getPubKeyAsHex(filterSigningKey);
            return isPublicKeyInList(pubKeyAsHex);
        } catch (Throwable t) {
            return false;
        }
    }

    private void setFilterSigningKey(String privKeyString) {
        this.filterSigningKey = toECKey(privKeyString);
    }

    private String getSignature(Filter filterWithoutSig) {
        Sha256Hash hash = getSha256Hash(filterWithoutSig);
        ECKey.ECDSASignature ecdsaSignature = filterSigningKey.sign(hash);
        byte[] encodeToDER = ecdsaSignature.encodeToDER();
        return new String(Base64.encode(encodeToDER), StandardCharsets.UTF_8);
    }

    private boolean isFilterPublicKeyInList(Filter filter) {
        String signerPubKeyAsHex = filter.getSignerPubKeyAsHex();
        if (!isPublicKeyInList(signerPubKeyAsHex)) {
            log.warn("signerPubKeyAsHex from filter is not part of our pub key list. filter={}, publicKeys={}", filter, publicKeys);
            return false;
        }
        return true;
    }

    private boolean isPublicKeyInList(String pubKeyAsHex) {
        boolean isPublicKeyInList = publicKeys.contains(pubKeyAsHex);
        if (!isPublicKeyInList) {
            log.warn("pubKeyAsHex is not part of our pub key list. pubKeyAsHex={}, publicKeys={}", pubKeyAsHex, publicKeys);
        }
        return isPublicKeyInList;
    }

    private boolean isSignatureValid(Filter filter) {
        try {
            Filter filterForSigVerification = Filter.cloneWithoutSig(filter);
            Sha256Hash hash = getSha256Hash(filterForSigVerification);

            checkNotNull(filter.getSignatureAsBase64(), "filter.getSignatureAsBase64() must not be null");
            byte[] sigData = Base64.decode(filter.getSignatureAsBase64());
            ECKey.ECDSASignature ecdsaSignature = ECKey.ECDSASignature.decodeFromDER(sigData);

            String signerPubKeyAsHex = filter.getSignerPubKeyAsHex();
            byte[] decode = HEX.decode(signerPubKeyAsHex);
            ECKey ecPubKey = ECKey.fromPublicOnly(decode);
            return ecPubKey.verify(hash, ecdsaSignature);
        } catch (Throwable e) {
            log.warn("verifySignature failed. filter={}", filter);
            return false;
        }
    }

    private ECKey toECKey(String privKeyString) {
        return ECKey.fromPrivate(new BigInteger(1, HEX.decode(privKeyString)));
    }

    private Sha256Hash getSha256Hash(Filter filter) {
        byte[] filterData = filter.toProtoMessage().toByteArray();
        return Sha256Hash.of(filterData);
    }

    private String getPubKeyAsHex(ECKey ecKey) {
        return HEX.encode(ecKey.getPubKey());
    }
}
