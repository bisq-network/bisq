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

package bisq.core.arbitration;

import bisq.core.app.AppOptionKeys;
import bisq.core.filter.FilterManager;
import bisq.core.user.Preferences;
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

import com.google.inject.Inject;
import com.google.inject.name.Named;

import javafx.collections.FXCollections;
import javafx.collections.ObservableMap;

import java.security.PublicKey;
import java.security.SignatureException;

import java.math.BigInteger;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.jetbrains.annotations.NotNull;

import javax.annotation.Nullable;

import static org.bitcoinj.core.Utils.HEX;

public class ArbitratorManager {
    private static final Logger log = LoggerFactory.getLogger(ArbitratorManager.class);

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Static
    ///////////////////////////////////////////////////////////////////////////////////////////

    private static final long REPUBLISH_MILLIS = Arbitrator.TTL / 2;
    private static final long RETRY_REPUBLISH_SEC = 5;
    private static final long REPEATED_REPUBLISH_AT_STARTUP_SEC = 60;

    private final List<String> publicKeys;

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Instance fields
    ///////////////////////////////////////////////////////////////////////////////////////////

    private final KeyRing keyRing;
    private final ArbitratorService arbitratorService;
    private final User user;
    private final Preferences preferences;
    private final FilterManager filterManager;
    private final ObservableMap<NodeAddress, Arbitrator> arbitratorsObservableMap = FXCollections.observableHashMap();
    private List<Arbitrator> persistedAcceptedArbitrators;
    private Timer republishArbitratorTimer, retryRepublishArbitratorTimer;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    public ArbitratorManager(KeyRing keyRing,
                             ArbitratorService arbitratorService,
                             User user,
                             Preferences preferences,
                             FilterManager filterManager,
                             @Named(AppOptionKeys.USE_DEV_PRIVILEGE_KEYS) boolean useDevPrivilegeKeys) {
        this.keyRing = keyRing;
        this.arbitratorService = arbitratorService;
        this.user = user;
        this.preferences = preferences;
        this.filterManager = filterManager;
        publicKeys = useDevPrivilegeKeys ?
                Collections.unmodifiableList(Collections.singletonList(DevEnv.DEV_PRIVILEGE_PUB_KEY)) :
                Collections.unmodifiableList(Arrays.asList(
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
                        "034e2dbeeb30da78750a14f1ca69fe08c916785ddceb14d93f695bfa7805b16fdf",
                        "02762e6d613e69e29ff6bf39c12f9f38d521ad317cc29dd2aaec0750ce6e730310",
                        "03d01e3fb9fa33cb1cc834290563e9dd0af56e6d2ed570996a933141a305eac855",
                        "03089b5a325b2f4d05c0efb2680882883495148d8b47bde0a5fd12ed11883192eb",
                        "02ea15f1259dd2be903a69bd44a8bf83f8b071c7245ec16b1019446bc53a1fbc16",
                        "0305a30db223f9c066be6720c29cee1000f527f8906b258ff29afc23aedc3e3b40",
                        "0339f3a784bb901957cdd0035c66ec5f5773f189ffd0e7a8a6f54e690765f80355",
                        "02a23dc2000669b3b6bca06342da7fed0d3d85178f723295c5525ed5acf07a07f6",
                        "027e630e1010f17e70eefa3f3f8091dba9b8bf4285ffe39a4ca36e832e86d317e1",
                        "0399fd6fa3e9a7e9cc372be01df2b6b0105c2017684ba74f0cee40c1d7f964b827",
                        "02fab3407946c5a37294417ccb0560a1380a88a172ed1aec001f72d3024d32ff47",
                        "02283f539936d45d0fa1443a5fee7823a0b46b224d066de41a385f5b72ccc5986a",
                        "03abccde40a2d821fa35527db4b6c4a4e02a159cee600eebc325350f2383b8a40c",
                        "0209ad1e77f8cbcdacad301ae64d30ce8b96a61bb048810384d0c9991d5cd4eb71",
                        "0372e00c0c3b459977caff6ad55d80c372ed3245411d90fcbd6045184bb87b03e9",
                        "02c81cd2b8975ab00e0299156bb352a4afac3f1b88ff3ec9680ea8992ee706a742",
                        "0288f6e6b406b44ecda924c5f802663883365993c21d891bee70f5135c54ffa90e",
                        "0391428bc51114e9803f68a1560d0db2fcd43d330662ec08be0c34f58dddc46ffb",
                        "03ce4d6a6ea7791577bca143f76d1b1f57b0770a6f5d41276b4798feb937520564"
                ));
    }

    public void shutDown() {
        stopRepublishArbitratorTimer();
        stopRetryRepublishArbitratorTimer();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // API
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void onAllServicesInitialized() {
        arbitratorService.addHashSetChangedListener(new HashMapChangedListener() {
            @Override
            public void onAdded(ProtectedStorageEntry data) {
                if (data.getProtectedStoragePayload() instanceof Arbitrator)
                    updateArbitratorMap();
            }

            @Override
            public void onRemoved(ProtectedStorageEntry data) {
                if (data.getProtectedStoragePayload() instanceof Arbitrator) {
                    updateArbitratorMap();
                    final Arbitrator arbitrator = (Arbitrator) data.getProtectedStoragePayload();
                    user.removeAcceptedArbitrator(arbitrator);
                    user.removeAcceptedMediator(getMediator(arbitrator));
                }
            }
        });

        persistedAcceptedArbitrators = new ArrayList<>(user.getAcceptedArbitrators());
        user.clearAcceptedArbitrators();

        // TODO we mirror arbitrator data for mediator as long we have not impl. it in the UI
        user.clearAcceptedMediators();

        if (user.getRegisteredArbitrator() != null) {
            P2PService p2PService = arbitratorService.getP2PService();
            if (p2PService.isBootstrapped())
                startRepublishArbitrator();
            else
                p2PService.addP2PServiceListener(new BootstrapListener() {
                    @Override
                    public void onUpdatedDataReceived() {
                        startRepublishArbitrator();
                    }
                });
        }

        filterManager.filterProperty().addListener((observable, oldValue, newValue) -> updateArbitratorMap());

        updateArbitratorMap();
    }

    private void startRepublishArbitrator() {
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
                .filter(e -> {
                    final String pubKeyAsHex = Utils.HEX.encode(e.getRegistrationPubKey());
                    final boolean isInPublicKeyInList = isPublicKeyInList(pubKeyAsHex);
                    if (!isInPublicKeyInList) {
                        if (DevEnv.DEV_PRIVILEGE_PUB_KEY.equals(pubKeyAsHex))
                            log.info("We got the DEV_PRIVILEGE_PUB_KEY in our list of publicKeys. RegistrationPubKey={}, nodeAddress={}",
                                    Utilities.bytesAsHexString(e.getRegistrationPubKey()),
                                    e.getNodeAddress().getFullAddress());
                        else
                            log.warn("We got an arbitrator which is not in our list of publicKeys. RegistrationPubKey={}, nodeAddress={}",
                                    Utilities.bytesAsHexString(e.getRegistrationPubKey()),
                                    e.getNodeAddress().getFullAddress());
                    }
                    final boolean isSigValid = verifySignature(e.getPubKeyRing().getSignaturePubKey(),
                            e.getRegistrationPubKey(),
                            e.getRegistrationSignature());
                    if (!isSigValid)
                        log.warn("Sig check for arbitrator failed. Arbitrator=", e.toString());

                    return isInPublicKeyInList && isSigValid;
                })
                .collect(Collectors.toMap(Arbitrator::getNodeAddress, Function.identity()));

        arbitratorsObservableMap.putAll(filtered);
        arbitratorsObservableMap.values().stream()
                .filter(persistedAcceptedArbitrators::contains)
                .forEach(a -> {
                    user.addAcceptedArbitrator(a);
                    user.addAcceptedMediator(getMediator(a)
                    );
                });

        // We keep the domain with storing the arbitrators in user as it might be still useful for mediators
        arbitratorsObservableMap.values().forEach(a -> {
            user.addAcceptedArbitrator(a);
            user.addAcceptedMediator(getMediator(a)
            );
        });

        log.info("Available arbitrators: {}", arbitratorsObservableMap.keySet());
    }

    // TODO we mirror arbitrator data for mediator as long we have not impl. it in the UI
    @NotNull
    public static Mediator getMediator(Arbitrator arbitrator) {
        return new Mediator(arbitrator.getNodeAddress(),
                arbitrator.getPubKeyRing(),
                arbitrator.getLanguageCodes(),
                arbitrator.getRegistrationDate(),
                arbitrator.getRegistrationPubKey(),
                arbitrator.getRegistrationSignature(),
                arbitrator.getEmailAddress(),
                null,
                arbitrator.getExtraDataMap());
    }

    public void addArbitrator(Arbitrator arbitrator, ResultHandler resultHandler, ErrorMessageHandler errorMessageHandler) {
        user.setRegisteredArbitrator(arbitrator);
        arbitratorsObservableMap.put(arbitrator.getNodeAddress(), arbitrator);
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
            arbitratorsObservableMap.remove(registeredArbitrator.getNodeAddress());
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

    public boolean isArbitratorAvailableForLanguage(String languageCode) {
        return arbitratorsObservableMap.values().stream().anyMatch(arbitrator ->
                arbitrator.getLanguageCodes().stream().anyMatch(lc -> lc.equals(languageCode)));
    }

    public List<String> getArbitratorLanguages(List<NodeAddress> nodeAddresses) {
        return arbitratorsObservableMap.values().stream()
                .filter(arbitrator -> nodeAddresses.stream().anyMatch(nodeAddress -> nodeAddress.equals(arbitrator.getNodeAddress())))
                .flatMap(arbitrator -> arbitrator.getLanguageCodes().stream())
                .distinct()
                .collect(Collectors.toList());
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

    public Optional<Arbitrator> getArbitratorByNodeAddress(NodeAddress nodeAddress) {
        return arbitratorsObservableMap.containsKey(nodeAddress) ?
                Optional.of(arbitratorsObservableMap.get(nodeAddress)) :
                Optional.empty();
    }
}
