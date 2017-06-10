/*
 * This file is part of Bitsquare.
 *
 * Bitsquare is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * Bitsquare is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Bitsquare. If not, see <http://www.gnu.org/licenses/>.
 */

package io.bitsquare.arbitration;

import com.google.inject.Inject;
import io.bitsquare.common.Timer;
import io.bitsquare.common.UserThread;
import io.bitsquare.common.crypto.KeyRing;
import io.bitsquare.common.handlers.ErrorMessageHandler;
import io.bitsquare.common.handlers.ResultHandler;
import io.bitsquare.p2p.BootstrapListener;
import io.bitsquare.p2p.NodeAddress;
import io.bitsquare.p2p.P2PService;
import io.bitsquare.p2p.storage.HashMapChangedListener;
import io.bitsquare.p2p.storage.storageentry.ProtectedStorageEntry;
import io.bitsquare.user.Preferences;
import io.bitsquare.user.User;
import javafx.collections.FXCollections;
import javafx.collections.ObservableMap;
import org.bitcoinj.core.ECKey;
import org.bitcoinj.core.Utils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.math.BigInteger;
import java.security.PublicKey;
import java.security.SignatureException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.bitcoinj.core.Utils.HEX;

public class ArbitratorManager {
    private static final Logger log = LoggerFactory.getLogger(ArbitratorManager.class);

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Static
    ///////////////////////////////////////////////////////////////////////////////////////////

    private static final long REPUBLISH_MILLIS = Arbitrator.TTL / 2;
    private static final long RETRY_REPUBLISH_SEC = 5;
    private static final long REPEATED_REPUBLISH_AT_STARTUP_SEC = 60;

    // Keys for invited arbitrators in bootstrapping phase (before registration is open to anyone and security payment is implemented)
    // For developers we add here 2 test keys so one can setup an arbitrator by adding that test pubKey 
    // to the publicKeys list and use the test PrivKey for arbitrator registration.
    // PrivKey for dev testing: 6ac43ea1df2a290c1c8391736aa42e4339c5cb4f110ff0257a13b63211977b7a
    // Matching pubKey for dev testing: 027a381b5333a56e1cc3d90d3a7d07f26509adf7029ed06fc997c656621f8da1ee
    private static final List<String> publicKeys = new ArrayList<>(Arrays.asList(
            "03697a499d24f497b3c46bf716318231e46c4e6a685a4e122d8e2a2b229fa1f4b8",
            "0365c6af94681dbee69de1851f98d4684063bf5c2d64b1c73ed5d90434f375a054",
            "031c502a60f9dbdb5ae5e438a79819e4e1f417211dd537ac12c9bc23246534c4bd",
            "02c1e5a242387b6d5319ce27246cea6edaaf51c3550591b528d2578a4753c56c2c",
            "025c319faf7067d9299590dd6c97fe7e56cd4dac61205ccee1cd1fc390142390a2",
            "038f6e24c2bfe5d51d0a290f20a9a657c270b94ef2b9c12cd15ca3725fa798fc55",
            "0255256ff7fb615278c4544a9bbd3f5298b903b8a011cd7889be19b6b1c45cbefe",
            "024a3a37289f08c910fbd925ebc72b946f33feaeff451a4738ee82037b4cda2e95",
            "02a88b75e9f0f8afba1467ab26799dcc38fd7a6468fb2795444b425eb43e2c10bd",
            "02349a51512c1c04c67118386f4d27d768c5195a83247c150a4b722d161722ba81",
            "03f718a2e0dc672c7cdec0113e72c3322efc70412bb95870750d25c32cd98de17d",
            "028ff47ee2c56e66313928975c58fa4f1b19a0f81f3a96c4e9c9c3c6768075509e",
            "02b517c0cbc3a49548f448ddf004ed695c5a1c52ec110be1bfd65fa0ca0761c94b",
            "03df837a3a0f3d858e82f3356b71d1285327f101f7c10b404abed2abc1c94e7169",
            "0203a90fb2ab698e524a5286f317a183a84327b8f8c3f7fa4a98fec9e1cefd6b72",
            "023c99cc073b851c892d8c43329ca3beb5d2213ee87111af49884e3ce66cbd5ba5",
            "027a381b5333a56e1cc3d90d3a7d07f26509adf7029ed06fc997c656621f8da1ee"
    ));


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Instance fields
    ///////////////////////////////////////////////////////////////////////////////////////////

    private final KeyRing keyRing;
    private final ArbitratorService arbitratorService;
    private final User user;
    private Preferences preferences;
    private final ObservableMap<NodeAddress, Arbitrator> arbitratorsObservableMap = FXCollections.observableHashMap();
    private final List<Arbitrator> persistedAcceptedArbitrators;
    private Timer republishArbitratorTimer, retryRepublishArbitratorTimer;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    public ArbitratorManager(KeyRing keyRing, ArbitratorService arbitratorService, User user, Preferences preferences) {
        this.keyRing = keyRing;
        this.arbitratorService = arbitratorService;
        this.user = user;
        this.preferences = preferences;

        persistedAcceptedArbitrators = new ArrayList<>(user.getAcceptedArbitrators());
        user.clearAcceptedArbitrators();

        arbitratorService.addHashSetChangedListener(new HashMapChangedListener() {
            @Override
            public void onAdded(ProtectedStorageEntry data) {
                if (data.getStoragePayload() instanceof Arbitrator)
                    updateArbitratorMap();
            }

            @Override
            public void onRemoved(ProtectedStorageEntry data) {
                if (data.getStoragePayload() instanceof Arbitrator)
                    updateArbitratorMap();
            }
        });
    }

    public void shutDown() {
        stopRepublishArbitratorTimer();
        stopRetryRepublishArbitratorTimer();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // API
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void onAllServicesInitialized() {
        if (user.getRegisteredArbitrator() != null) {
            P2PService p2PService = arbitratorService.getP2PService();
            if (p2PService.isBootstrapped())
                isBootstrapped();
            else
                p2PService.addP2PServiceListener(new BootstrapListener() {
                    @Override
                    public void onBootstrapComplete() {
                        isBootstrapped();
                    }
                });
        }

        updateArbitratorMap();
    }

    private void isBootstrapped() {
        if (republishArbitratorTimer == null) {
            republishArbitratorTimer = UserThread.runPeriodically(this::republishArbitrator, REPUBLISH_MILLIS, TimeUnit.MILLISECONDS);
            UserThread.runAfter(this::republishArbitrator, REPEATED_REPUBLISH_AT_STARTUP_SEC);
            republishArbitrator();
        }
    }

    public void updateArbitratorMap() {
        Map<NodeAddress, Arbitrator> map = arbitratorService.getArbitrators();
        arbitratorsObservableMap.clear();
        Map<NodeAddress, Arbitrator> filtered = map.values().stream()
                .filter(e -> isPublicKeyInList(Utils.HEX.encode(e.getRegistrationPubKey()))
                        && verifySignature(e.getPubKeyRing().getSignaturePubKey(), e.getRegistrationPubKey(), e.getRegistrationSignature()))
                .collect(Collectors.toMap(Arbitrator::getArbitratorNodeAddress, Function.identity()));

        arbitratorsObservableMap.putAll(filtered);
        arbitratorsObservableMap.values().stream()
                .filter(arbitrator -> persistedAcceptedArbitrators.contains(arbitrator))
                .forEach(user::addAcceptedArbitrator);

        if (preferences.getAutoSelectArbitrators()) {
            arbitratorsObservableMap.values().stream()
                    .filter(user::hasMatchingLanguage)
                    .forEach(user::addAcceptedArbitrator);
        } else {
            // if we don't have any arbitrator we set all matching
            // we use a delay as we might get our matching arbitrator a bit delayed (first we get one we did not selected
            // then we get our selected one - we don't want to activate the first in that case)
            UserThread.runAfter(() -> {
                if (user.getAcceptedArbitrators().isEmpty()) {
                    arbitratorsObservableMap.values().stream()
                            .filter(user::hasMatchingLanguage)
                            .forEach(user::addAcceptedArbitrator);
                }
            }, 100, TimeUnit.MILLISECONDS);
        }
    }

    public void addArbitrator(Arbitrator arbitrator, ResultHandler resultHandler, ErrorMessageHandler errorMessageHandler) {
        user.setRegisteredArbitrator(arbitrator);
        arbitratorsObservableMap.put(arbitrator.getArbitratorNodeAddress(), arbitrator);
        arbitratorService.addArbitrator(arbitrator,
                () -> {
                    log.debug("Arbitrator successfully saved in P2P network");
                    resultHandler.handleResult();

                    if (arbitratorsObservableMap.size() > 0)
                        UserThread.runAfter(this::updateArbitratorMap, 100, TimeUnit.MILLISECONDS);
                },
                errorMessageHandler::handleErrorMessage);
    }

    public void removeArbitrator(ResultHandler resultHandler, ErrorMessageHandler errorMessageHandler) {
        Arbitrator registeredArbitrator = user.getRegisteredArbitrator();
        if (registeredArbitrator != null) {
            user.setRegisteredArbitrator(null);
            arbitratorsObservableMap.remove(registeredArbitrator.getArbitratorNodeAddress());
            arbitratorService.removeArbitrator(registeredArbitrator,
                    () -> {
                        log.debug("Arbitrator successfully removed from P2P network");
                        resultHandler.handleResult();
                    },
                    errorMessageHandler::handleErrorMessage);
        }
    }

    public ObservableMap<NodeAddress, Arbitrator> getArbitratorsObservableMap() {
        return arbitratorsObservableMap;
    }

    // A private key is handed over to selected arbitrators for registration.
    // An invited arbitrator will sign at registration his storageSignaturePubKey with that private key and attach the signature and pubKey to his data.
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


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Private
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void republishArbitrator() {
        Arbitrator registeredArbitrator = user.getRegisteredArbitrator();
        if (registeredArbitrator != null) {
            addArbitrator(registeredArbitrator,
                    this::updateArbitratorMap,
                    errorMessage -> {
                        if (retryRepublishArbitratorTimer == null)
                            retryRepublishArbitratorTimer = UserThread.runPeriodically(() -> {
                                stopRetryRepublishArbitratorTimer();
                                republishArbitrator();
                            }, RETRY_REPUBLISH_SEC);
                    }
            );
        }
    }

    private boolean verifySignature(PublicKey storageSignaturePubKey, byte[] registrationPubKey, String signature) {
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


    private void stopRetryRepublishArbitratorTimer() {
        if (retryRepublishArbitratorTimer != null) {
            retryRepublishArbitratorTimer.stop();
            retryRepublishArbitratorTimer = null;
        }
    }

    private void stopRepublishArbitratorTimer() {
        if (republishArbitratorTimer != null) {
            republishArbitratorTimer.stop();
            republishArbitratorTimer = null;
        }
    }
}
