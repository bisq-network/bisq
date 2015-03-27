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

import io.bitsquare.locale.LanguageUtil;
import io.bitsquare.storage.Storage;
import io.bitsquare.util.DSAKeyUtil;

import org.bitcoinj.core.Coin;
import org.bitcoinj.core.Utils;

import com.google.inject.Inject;

import java.io.Serializable;

import java.security.PublicKey;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import javafx.collections.FXCollections;
import javafx.collections.MapChangeListener;
import javafx.collections.ObservableMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ArbitrationRepository implements Serializable {
    // That object is saved to disc. We need to take care of changes to not break deserialization.
    private static final long serialVersionUID = 1L;
    transient private static final Logger log = LoggerFactory.getLogger(ArbitrationRepository.class);

    transient private Storage<ArbitrationRepository> storage;
    transient private ArbitratorService arbitratorService;
    transient private Arbitrator defaultArbitrator;


    // Persisted fields
    private final Map<String, Arbitrator> arbitratorsMap = new HashMap<>();
    transient private final ObservableMap<String, Arbitrator> arbitratorsObservableMap = FXCollections.observableHashMap();

    transient private boolean allArbitratorsSynced;

    @Inject
    public ArbitrationRepository(Storage<ArbitrationRepository> storage,
                                 Storage<Arbitrator> arbitratorStorage,
                                 ArbitratorService arbitratorService) {
        this.storage = storage;
        this.arbitratorService = arbitratorService;

        byte[] walletPubKey = Utils.HEX.decode("03a418bf0cb60a35ce217c7f80a2db08a4f5efbe56a0e7602fbc392dea6b63f840");
        PublicKey p2pSigPubKey = DSAKeyUtil.decodePubKeyHex
                ("308201b83082012c06072a8648ce3804013082011f02818100fd7f53811d75122952df4a9c2eece4e7f611b7523cef4400c31e3f80b6512669455d402251fb593d8d58fabfc5f5ba30f6cb9b556cd7813b801d346ff26660b76b9950a5a49f9fe8047b1022c24fbba9d7feb7c61bf83b57e7c6a8a6150f04fb83f6d3c51ec3023554135a169132f675f3ae2b61d72aeff22203199dd14801c70215009760508f15230bccb292b982a2eb840bf0581cf502818100f7e1a085d69b3ddecbbcab5c36b857b97994afbbfa3aea82f9574c0b3d0782675159578ebad4594fe67107108180b449167123e84c281613b7cf09328cc8a6e13c167a8b547c8d28e0a3ae1e2bb3a675916ea37f0bfa213562f1fb627a01243bcca4f1bea8519089a883dfe15ae59f06928b665e807b552564014c3bfecf492a0381850002818100db47d4cf76e9bfcc0ba1e98c21c19ba45d1440fa2fec732f664dc8fd63e98877e648aac6db8d1035cd640fe5ff2e0030c2f8694ed124e81bd42c5446a1ce5288d5c8b4073d1cd890fe61ee4527f4e3184279f394cb9c2a4e7924cb2e82320a846cc140304eac6d41d4eaebc4d69b92725715497a82890be9f49d348fda20b095");

        this.defaultArbitrator = new Arbitrator(arbitratorStorage,
                "default-524f-46c0-b96e-de5a11d3475d",
                walletPubKey,
                p2pSigPubKey,
                "Mr. Default",
                new Reputation(),
                Arbitrator.ID_TYPE.REAL_LIFE_ID,
                Arrays.asList(LanguageUtil.getDefaultLanguageLocaleAsCode()),
                Coin.parseCoin("0.1"),
                Arrays.asList(Arbitrator.METHOD.TLS_NOTARY),
                Arrays.asList(Arbitrator.ID_VERIFICATION.PASSPORT),
                "https://bitsquare.io",
                "Bla bla...");

        arbitratorsMap.put(defaultArbitrator.getId(), defaultArbitrator);
        ArbitrationRepository persisted = storage.initAndGetPersisted(this);
        if (persisted != null) {
            arbitratorsMap.putAll(persisted.getArbitratorsMap());
        }
        arbitratorsObservableMap.putAll(arbitratorsMap);
        arbitratorsObservableMap.addListener((MapChangeListener<String, Arbitrator>) change -> storage.queueUpForSave());
        allArbitratorsSynced = false;
    }

    // Is called when all services are ready
    public void loadAllArbitrators() {
        log.debug("loadAllArbitrators");
        arbitratorService.loadAllArbitrators((Map<String, Arbitrator> arbitratorsMap) -> {
                    log.debug("Arbitrators successful loaded.");
                    log.debug("arbitratorsMap.size()=" + arbitratorsMap.size());
                    ArbitrationRepository.this.arbitratorsMap.clear();
                    ArbitrationRepository.this.arbitratorsMap.put(defaultArbitrator.getId(), defaultArbitrator);
                    ArbitrationRepository.this.arbitratorsMap.putAll(arbitratorsMap);
                    ArbitrationRepository.this.arbitratorsObservableMap.clear();
                    ArbitrationRepository.this.arbitratorsObservableMap.putAll(ArbitrationRepository.this.arbitratorsMap);
                    allArbitratorsSynced = true;
                },
                (errorMessage -> log.error(errorMessage)));
    }

    public Map<String, Arbitrator> getArbitratorsMap() {
        return arbitratorsMap;
    }

    public ObservableMap<String, Arbitrator> getArbitratorsObservableMap() {
        return arbitratorsObservableMap;
    }

    public boolean areAllArbitratorsSynced() {
        return allArbitratorsSynced;
    }

    public Arbitrator getDefaultArbitrator() {
        return defaultArbitrator;
    }
}
