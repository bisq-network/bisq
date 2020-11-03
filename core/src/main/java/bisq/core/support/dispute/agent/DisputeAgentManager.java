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
import java.util.Collection;
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
public abstract class DisputeAgentManager<T extends DisputeAgent> {

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Static
    ///////////////////////////////////////////////////////////////////////////////////////////

    protected static final long REPUBLISH_MILLIS = DisputeAgent.TTL / 2;
    protected static final long RETRY_REPUBLISH_SEC = 5;
    protected static final long REPEATED_REPUBLISH_AT_STARTUP_SEC = 60;

    protected final List<String> publicKeys;

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Instance fields
    ///////////////////////////////////////////////////////////////////////////////////////////

    protected final KeyRing keyRing;
    protected final DisputeAgentService<T> disputeAgentService;
    protected final User user;
    protected final FilterManager filterManager;
    protected final ObservableMap<NodeAddress, T> observableMap = FXCollections.observableHashMap();
    protected List<T> persistedAcceptedDisputeAgents;
    protected Timer republishTimer, retryRepublishTimer;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    public DisputeAgentManager(KeyRing keyRing,
                               DisputeAgentService<T> disputeAgentService,
                               User user,
                               FilterManager filterManager,
                               boolean useDevPrivilegeKeys) {
        this.keyRing = keyRing;
        this.disputeAgentService = disputeAgentService;
        this.user = user;
        this.filterManager = filterManager;
        publicKeys = useDevPrivilegeKeys ? Collections.singletonList(DevEnv.DEV_PRIVILEGE_PUB_KEY) : getPubKeyList();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Abstract methods
    ///////////////////////////////////////////////////////////////////////////////////////////

    protected abstract List<String> getPubKeyList();

    protected abstract boolean isExpectedInstance(ProtectedStorageEntry data);

    protected abstract void addAcceptedDisputeAgentToUser(T disputeAgent);

    protected abstract T getRegisteredDisputeAgentFromUser();

    protected abstract void clearAcceptedDisputeAgentsAtUser();

    protected abstract List<T> getAcceptedDisputeAgentsFromUser();

    protected abstract void removeAcceptedDisputeAgentFromUser(ProtectedStorageEntry data);

    protected abstract void setRegisteredDisputeAgentAtUser(T disputeAgent);


    ///////////////////////////////////////////////////////////////////////////////////////////
    // API
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void onAllServicesInitialized() {
        disputeAgentService.addHashSetChangedListener(new HashMapChangedListener() {
            @Override
            public void onAdded(Collection<ProtectedStorageEntry> protectedStorageEntries) {
                protectedStorageEntries.forEach(protectedStorageEntry -> {
                    if (isExpectedInstance(protectedStorageEntry)) {
                        updateMap();
                    }
                });
            }

            @Override
            public void onRemoved(Collection<ProtectedStorageEntry> protectedStorageEntries) {
                protectedStorageEntries.forEach(protectedStorageEntry -> {
                    if (isExpectedInstance(protectedStorageEntry)) {
                        updateMap();
                        removeAcceptedDisputeAgentFromUser(protectedStorageEntry);
                    }
                });
            }
        });

        persistedAcceptedDisputeAgents = new ArrayList<>(getAcceptedDisputeAgentsFromUser());
        clearAcceptedDisputeAgentsAtUser();

        if (getRegisteredDisputeAgentFromUser() != null) {
            P2PService p2PService = disputeAgentService.getP2PService();
            if (p2PService.isBootstrapped())
                startRepublishDisputeAgent();
            else
                p2PService.addP2PServiceListener(new BootstrapListener() {
                    @Override
                    public void onUpdatedDataReceived() {
                        startRepublishDisputeAgent();
                    }
                });
        }

        filterManager.filterProperty().addListener((observable, oldValue, newValue) -> updateMap());

        updateMap();
    }

    public void shutDown() {
        stopRepublishTimer();
        stopRetryRepublishTimer();
    }

    protected void startRepublishDisputeAgent() {
        if (republishTimer == null) {
            republishTimer = UserThread.runPeriodically(this::republish, REPUBLISH_MILLIS, TimeUnit.MILLISECONDS);
            UserThread.runAfter(this::republish, REPEATED_REPUBLISH_AT_STARTUP_SEC);
            republish();
        }
    }

    public void updateMap() {
        Map<NodeAddress, T> map = disputeAgentService.getDisputeAgents();
        observableMap.clear();
        Map<NodeAddress, T> filtered = map.values().stream()
                .filter(e -> {
                    String pubKeyAsHex = Utils.HEX.encode(e.getRegistrationPubKey());
                    boolean isInPublicKeyInList = isPublicKeyInList(pubKeyAsHex);
                    if (!isInPublicKeyInList) {
                        if (DevEnv.DEV_PRIVILEGE_PUB_KEY.equals(pubKeyAsHex))
                            log.info("We got the DEV_PRIVILEGE_PUB_KEY in our list of publicKeys. RegistrationPubKey={}, nodeAddress={}",
                                    Utilities.bytesAsHexString(e.getRegistrationPubKey()),
                                    e.getNodeAddress().getFullAddress());
                        else
                            log.warn("We got an disputeAgent which is not in our list of publicKeys. RegistrationPubKey={}, nodeAddress={}",
                                    Utilities.bytesAsHexString(e.getRegistrationPubKey()),
                                    e.getNodeAddress().getFullAddress());
                    }
                    final boolean isSigValid = verifySignature(e.getPubKeyRing().getSignaturePubKey(),
                            e.getRegistrationPubKey(),
                            e.getRegistrationSignature());
                    if (!isSigValid)
                        log.warn("Sig check for disputeAgent failed. DisputeAgent={}", e.toString());

                    return isInPublicKeyInList && isSigValid;
                })
                .collect(Collectors.toMap(DisputeAgent::getNodeAddress, Function.identity()));

        observableMap.putAll(filtered);
        observableMap.values().forEach(this::addAcceptedDisputeAgentToUser);
    }


    public void addDisputeAgent(T disputeAgent,
                                ResultHandler resultHandler,
                                ErrorMessageHandler errorMessageHandler) {
        setRegisteredDisputeAgentAtUser(disputeAgent);
        observableMap.put(disputeAgent.getNodeAddress(), disputeAgent);
        disputeAgentService.addDisputeAgent(disputeAgent,
                () -> {
                    log.info("DisputeAgent successfully saved in P2P network");
                    resultHandler.handleResult();

                    if (observableMap.size() > 0)
                        UserThread.runAfter(this::updateMap, 100, TimeUnit.MILLISECONDS);
                },
                errorMessageHandler);
    }


    public void removeDisputeAgent(ResultHandler resultHandler, ErrorMessageHandler errorMessageHandler) {
        T registeredDisputeAgent = getRegisteredDisputeAgentFromUser();
        if (registeredDisputeAgent != null) {
            setRegisteredDisputeAgentAtUser(null);
            observableMap.remove(registeredDisputeAgent.getNodeAddress());
            disputeAgentService.removeDisputeAgent(registeredDisputeAgent,
                    () -> {
                        log.debug("DisputeAgent successfully removed from P2P network");
                        resultHandler.handleResult();
                    },
                    errorMessageHandler);
        }
    }

    public ObservableMap<NodeAddress, T> getObservableMap() {
        return observableMap;
    }

    // A protected key is handed over to selected disputeAgents for registration.
    // An invited disputeAgent will sign at registration his storageSignaturePubKey with that protected key and attach the signature and pubKey to his data.
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

    public boolean isAgentAvailableForLanguage(String languageCode) {
        return observableMap.values().stream().anyMatch(agent ->
                agent.getLanguageCodes().stream().anyMatch(lc -> lc.equals(languageCode)));
    }

    public List<String> getDisputeAgentLanguages(List<NodeAddress> nodeAddresses) {
        return observableMap.values().stream()
                .filter(disputeAgent -> nodeAddresses.stream().anyMatch(nodeAddress -> nodeAddress.equals(disputeAgent.getNodeAddress())))
                .flatMap(disputeAgent -> disputeAgent.getLanguageCodes().stream())
                .distinct()
                .collect(Collectors.toList());
    }

    public Optional<T> getDisputeAgentByNodeAddress(NodeAddress nodeAddress) {
        return observableMap.containsKey(nodeAddress) ?
                Optional.of(observableMap.get(nodeAddress)) :
                Optional.empty();
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // protected
    ///////////////////////////////////////////////////////////////////////////////////////////

    protected void republish() {
        T registeredDisputeAgent = getRegisteredDisputeAgentFromUser();
        if (registeredDisputeAgent != null) {
            addDisputeAgent(registeredDisputeAgent,
                    this::updateMap,
                    errorMessage -> {
                        if (retryRepublishTimer == null)
                            retryRepublishTimer = UserThread.runPeriodically(() -> {
                                stopRetryRepublishTimer();
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


    protected void stopRetryRepublishTimer() {
        if (retryRepublishTimer != null) {
            retryRepublishTimer.stop();
            retryRepublishTimer = null;
        }
    }

    protected void stopRepublishTimer() {
        if (republishTimer != null) {
            republishTimer.stop();
            republishTimer = null;
        }
    }
}
