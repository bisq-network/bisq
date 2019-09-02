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

package bisq.core.support.dispute.agent;

import bisq.core.filter.FilterManager;
import bisq.core.user.User;

import bisq.network.p2p.BootstrapListener;
import bisq.network.p2p.NodeAddress;
import bisq.network.p2p.P2PService;
import bisq.network.p2p.storage.HashMapChangedListener;
import bisq.network.p2p.storage.payload.ProtectedStorageEntry;

import bisq.common.Timer;
import bisq.common.UserThread;
import bisq.common.app.DevEnv;
import bisq.common.crypto.KeyRing;
import bisq.common.handlers.ErrorMessageHandler;
import bisq.common.handlers.ResultHandler;
import bisq.common.util.Utilities;

import org.bitcoinj.core.ECKey;
import org.bitcoinj.core.Utils;

import javafx.collections.FXCollections;
import javafx.collections.ObservableMap;

import java.security.PublicKey;
import java.security.SignatureException;

import java.math.BigInteger;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;

import lombok.extern.slf4j.Slf4j;

import javax.annotation.Nullable;

import static org.bitcoinj.core.Utils.HEX;

@Slf4j
public abstract class DisputeResolverManager<T extends DisputeResolver> {

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Static
    ///////////////////////////////////////////////////////////////////////////////////////////

    protected static final long REPUBLISH_MILLIS = DisputeResolver.TTL / 2;
    protected static final long RETRY_REPUBLISH_SEC = 5;
    protected static final long REPEATED_REPUBLISH_AT_STARTUP_SEC = 60;

    protected final List<String> publicKeys;

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Instance fields
    ///////////////////////////////////////////////////////////////////////////////////////////

    protected final KeyRing keyRing;
    protected final DisputeResolverService<T> disputeResolverService;
    protected final User user;
    protected final FilterManager filterManager;
    protected final ObservableMap<NodeAddress, T> observableMap = FXCollections.observableHashMap();
    protected List<T> persistedAcceptedDisputeResolvers;
    protected Timer republishTimer, retryRepublishTimer;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    public DisputeResolverManager(KeyRing keyRing,
                                  DisputeResolverService<T> disputeResolverService,
                                  User user,
                                  FilterManager filterManager,
                                  boolean useDevPrivilegeKeys) {
        this.keyRing = keyRing;
        this.disputeResolverService = disputeResolverService;
        this.user = user;
        this.filterManager = filterManager;
        publicKeys = useDevPrivilegeKeys ? Collections.singletonList(DevEnv.DEV_PRIVILEGE_PUB_KEY) : getPubKeyList();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Abstract methods
    ///////////////////////////////////////////////////////////////////////////////////////////

    protected abstract List<String> getPubKeyList();

    protected abstract boolean isExpectedInstance(ProtectedStorageEntry data);

    protected abstract void addAcceptedDisputeResolverToUser(T disputeResolver);

    protected abstract T getRegisteredDisputeResolverFromUser();

    protected abstract void clearAcceptedDisputeResolversAtUser();

    protected abstract List<T> getAcceptedDisputeResolversFromUser();

    protected abstract void removeAcceptedDisputeResolverFromUser(ProtectedStorageEntry data);

    protected abstract void setRegisteredDisputeResolverAtUser(T disputeResolver);


    ///////////////////////////////////////////////////////////////////////////////////////////
    // API
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void onAllServicesInitialized() {
        disputeResolverService.addHashSetChangedListener(new HashMapChangedListener() {
            @Override
            public void onAdded(ProtectedStorageEntry data) {
                if (isExpectedInstance(data)) {
                    updateMap();
                }
            }

            @Override
            public void onRemoved(ProtectedStorageEntry data) {
                if (isExpectedInstance(data)) {
                    updateMap();
                    removeAcceptedDisputeResolverFromUser(data);
                }
            }
        });

        persistedAcceptedDisputeResolvers = new ArrayList<>(getAcceptedDisputeResolversFromUser());
        clearAcceptedDisputeResolversAtUser();

        if (getRegisteredDisputeResolverFromUser() != null) {
            P2PService p2PService = disputeResolverService.getP2PService();
            if (p2PService.isBootstrapped())
                startRepublishDisputeResolver();
            else
                p2PService.addP2PServiceListener(new BootstrapListener() {
                    @Override
                    public void onUpdatedDataReceived() {
                        startRepublishDisputeResolver();
                    }
                });
        }

        filterManager.filterProperty().addListener((observable, oldValue, newValue) -> updateMap());

        updateMap();
    }

    public void shutDown() {
        stopRepublishDisputeResolverTimer();
        stopRetryRepublishDisputeResolverTimer();
    }

    protected void startRepublishDisputeResolver() {
        if (republishTimer == null) {
            republishTimer = UserThread.runPeriodically(this::republish, REPUBLISH_MILLIS, TimeUnit.MILLISECONDS);
            UserThread.runAfter(this::republish, REPEATED_REPUBLISH_AT_STARTUP_SEC);
            republish();
        }
    }

    public void updateMap() {
        Map<NodeAddress, T> map = disputeResolverService.getDisputeResolvers();
        observableMap.clear();
        Map<NodeAddress, T> filtered = map.values().stream()
                .filter(e -> {
                    final String pubKeyAsHex = Utils.HEX.encode(e.getRegistrationPubKey());
                    final boolean isInPublicKeyInList = isPublicKeyInList(pubKeyAsHex);
                    if (!isInPublicKeyInList) {
                        if (DevEnv.DEV_PRIVILEGE_PUB_KEY.equals(pubKeyAsHex))
                            log.info("We got the DEV_PRIVILEGE_PUB_KEY in our list of publicKeys. RegistrationPubKey={}, nodeAddress={}",
                                    Utilities.bytesAsHexString(e.getRegistrationPubKey()),
                                    e.getNodeAddress().getFullAddress());
                        else
                            log.warn("We got an disputeResolver which is not in our list of publicKeys. RegistrationPubKey={}, nodeAddress={}",
                                    Utilities.bytesAsHexString(e.getRegistrationPubKey()),
                                    e.getNodeAddress().getFullAddress());
                    }
                    final boolean isSigValid = verifySignature(e.getPubKeyRing().getSignaturePubKey(),
                            e.getRegistrationPubKey(),
                            e.getRegistrationSignature());
                    if (!isSigValid)
                        log.warn("Sig check for disputeResolver failed. DisputeResolver={}", e.toString());

                    return isInPublicKeyInList && isSigValid;
                })
                .collect(Collectors.toMap(DisputeResolver::getNodeAddress, Function.identity()));

        observableMap.putAll(filtered);

        //TODO check and test, seems it does not make sense
//        observableMap.values().stream()
//                .filter(persistedAcceptedDisputeResolvers::contains)
//                .forEach(this::addAcceptedDisputeResolverToUser);


        observableMap.values().forEach(this::addAcceptedDisputeResolverToUser);

        log.info("Available disputeResolvers: {}", observableMap.keySet());
    }


    public void addDisputeResolver(T disputeResolver,
                                   ResultHandler resultHandler,
                                   ErrorMessageHandler errorMessageHandler) {
        setRegisteredDisputeResolverAtUser(disputeResolver);
        observableMap.put(disputeResolver.getNodeAddress(), disputeResolver);
        disputeResolverService.addDisputeResolver(disputeResolver,
                () -> {
                    log.info("DisputeResolver successfully saved in P2P network");
                    resultHandler.handleResult();

                    if (observableMap.size() > 0)
                        UserThread.runAfter(this::updateMap, 100, TimeUnit.MILLISECONDS);
                },
                errorMessageHandler);
    }


    public void removeDisputeResolver(ResultHandler resultHandler, ErrorMessageHandler errorMessageHandler) {
        T registeredDisputeResolver = getRegisteredDisputeResolverFromUser();
        if (registeredDisputeResolver != null) {
            setRegisteredDisputeResolverAtUser(null);
            observableMap.remove(registeredDisputeResolver.getNodeAddress());
            disputeResolverService.removeDisputeResolver(registeredDisputeResolver,
                    () -> {
                        log.debug("DisputeResolver successfully removed from P2P network");
                        resultHandler.handleResult();
                    },
                    errorMessageHandler);
        }
    }

    public ObservableMap<NodeAddress, T> getObservableMap() {
        return observableMap;
    }

    // A protected key is handed over to selected disputeResolvers for registration.
    // An invited disputeResolver will sign at registration his storageSignaturePubKey with that protected key and attach the signature and pubKey to his data.
    // Other users will check the signature with the list of public keys hardcoded in the app.
    public String signStorageSignaturePubKey(ECKey key) {
        String keyToSignAsHex = Utils.HEX.encode(keyRing.getPubKeyRing().getSignaturePubKey().getEncoded());
        return key.signMessage(keyToSignAsHex);
    }

    @Nullable
    public ECKey getRegistrationKey(String privKeyBigIntString) {
        try {
            return ECKey.fromPrivate(new BigInteger(1, HEX.decode(privKeyBigIntString)));
        } catch (Throwable t) {
            return null;
        }
    }

    public boolean isPublicKeyInList(String pubKeyAsHex) {
        return publicKeys.contains(pubKeyAsHex);
    }

    public boolean isDisputeResolverAvailableForLanguage(String languageCode) {
        return observableMap.values().stream().anyMatch(disputeResolver ->
                disputeResolver.getLanguageCodes().stream().anyMatch(lc -> lc.equals(languageCode)));
    }

    public List<String> getDisputeResolverLanguages(List<NodeAddress> nodeAddresses) {
        return observableMap.values().stream()
                .filter(disputeResolver -> nodeAddresses.stream().anyMatch(nodeAddress -> nodeAddress.equals(disputeResolver.getNodeAddress())))
                .flatMap(disputeResolver -> disputeResolver.getLanguageCodes().stream())
                .distinct()
                .collect(Collectors.toList());
    }

    public Optional<T> getDisputeResolverByNodeAddress(NodeAddress nodeAddress) {
        return observableMap.containsKey(nodeAddress) ?
                Optional.of(observableMap.get(nodeAddress)) :
                Optional.empty();
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // protected
    ///////////////////////////////////////////////////////////////////////////////////////////

    protected void republish() {
        T registeredDisputeResolver = getRegisteredDisputeResolverFromUser();
        if (registeredDisputeResolver != null) {
            addDisputeResolver(registeredDisputeResolver,
                    this::updateMap,
                    errorMessage -> {
                        if (retryRepublishTimer == null)
                            retryRepublishTimer = UserThread.runPeriodically(() -> {
                                stopRetryRepublishDisputeResolverTimer();
                                republish();
                            }, RETRY_REPUBLISH_SEC);
                    }
            );
        }
    }

    protected boolean verifySignature(PublicKey storageSignaturePubKey, byte[] registrationPubKey, String signature) {
        String keyToSignAsHex = Utils.HEX.encode(storageSignaturePubKey.getEncoded());
        try {
            ECKey key = ECKey.fromPublicOnly(registrationPubKey);
            key.verifyMessage(keyToSignAsHex, signature);
            return true;
        } catch (SignatureException e) {
            log.warn("verifySignature failed");
            return false;
        }
    }


    protected void stopRetryRepublishDisputeResolverTimer() {
        if (retryRepublishTimer != null) {
            retryRepublishTimer.stop();
            retryRepublishTimer = null;
        }
    }

    protected void stopRepublishDisputeResolverTimer() {
        if (republishTimer != null) {
            republishTimer.stop();
            republishTimer = null;
        }
    }
}
