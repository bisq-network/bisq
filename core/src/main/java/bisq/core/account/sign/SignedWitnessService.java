/*
 * This file is part of Bisq.
 *
 * bisq is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * bisq is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with bisq. If not, see <http://www.gnu.org/licenses/>.
 */

package bisq.core.account.sign;

import bisq.core.account.witness.AccountAgeWitness;
import bisq.core.filter.FilterManager;
import bisq.core.support.dispute.arbitration.arbitrator.ArbitratorManager;
import bisq.core.user.User;

import bisq.network.p2p.BootstrapListener;
import bisq.network.p2p.P2PService;
import bisq.network.p2p.storage.P2PDataStorage;
import bisq.network.p2p.storage.persistence.AppendOnlyDataStoreService;

import bisq.common.UserThread;
import bisq.common.crypto.CryptoException;
import bisq.common.crypto.Hash;
import bisq.common.crypto.KeyRing;
import bisq.common.crypto.Sig;
import bisq.common.util.Utilities;

import org.bitcoinj.core.Coin;
import org.bitcoinj.core.ECKey;
import org.bitcoinj.core.Utils;

import javax.inject.Inject;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Charsets;

import java.security.PublicKey;
import java.security.SignatureException;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.Stack;
import java.util.stream.Collectors;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class SignedWitnessService {
    public static final long SIGNER_AGE_DAYS = 30;
    private static final long SIGNER_AGE = SIGNER_AGE_DAYS * ChronoUnit.DAYS.getDuration().toMillis();
    public static final Coin MINIMUM_TRADE_AMOUNT_FOR_SIGNING = Coin.parseCoin("0.0025");

    private final KeyRing keyRing;
    private final P2PService p2PService;
    private final ArbitratorManager arbitratorManager;
    private final SignedWitnessStorageService signedWitnessStorageService;
    private final User user;
    private final FilterManager filterManager;

    private final Map<P2PDataStorage.ByteArray, SignedWitness> signedWitnessMap = new HashMap<>();

    // This map keeps all SignedWitnesses with the same AccountAgeWitnessHash in a Set.
    // This avoids iterations over the signedWitnessMap for getting the set of such SignedWitnesses.
    private final Map<P2PDataStorage.ByteArray, Set<SignedWitness>> signedWitnessSetByAccountAgeWitnessHash = new HashMap<>();

    // Iterating over all SignedWitnesses and do a byte array comparison is a bit expensive and
    // it is called at filtering the offer book many times, so we use a lookup map for fast
    // access to the set of SignedWitness which match the ownerPubKey.
    private final Map<P2PDataStorage.ByteArray, Set<SignedWitness>> signedWitnessSetByOwnerPubKey = new HashMap<>();

    // The signature verification calls are rather expensive and called at filtering the offer book many times,
    // so we cache the results using the hash as key. The hash is created from the accountAgeWitnessHash and the
    // signature.
    private final Map<P2PDataStorage.ByteArray, Boolean> verifySignatureWithDSAKeyResultCache = new HashMap<>();
    private final Map<P2PDataStorage.ByteArray, Boolean> verifySignatureWithECKeyResultCache = new HashMap<>();


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    public SignedWitnessService(KeyRing keyRing,
                                P2PService p2PService,
                                ArbitratorManager arbitratorManager,
                                SignedWitnessStorageService signedWitnessStorageService,
                                AppendOnlyDataStoreService appendOnlyDataStoreService,
                                User user,
                                FilterManager filterManager) {
        this.keyRing = keyRing;
        this.p2PService = p2PService;
        this.arbitratorManager = arbitratorManager;
        this.signedWitnessStorageService = signedWitnessStorageService;
        this.user = user;
        this.filterManager = filterManager;

        // We need to add that early (before onAllServicesInitialized) as it will be used at startup.
        appendOnlyDataStoreService.addService(signedWitnessStorageService);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Lifecycle
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void onAllServicesInitialized() {
        p2PService.getP2PDataStorage().addAppendOnlyDataStoreListener(payload -> {
            if (payload instanceof SignedWitness)
                addToMap((SignedWitness) payload);
        });

        // At startup the P2PDataStorage initializes earlier, otherwise we get the listener called.
        signedWitnessStorageService.getMap().values().forEach(e -> {
            if (e instanceof SignedWitness)
                addToMap((SignedWitness) e);
        });

        if (p2PService.isBootstrapped()) {
            onBootstrapComplete();
        } else {
            p2PService.addP2PServiceListener(new BootstrapListener() {
                @Override
                public void onUpdatedDataReceived() {
                    onBootstrapComplete();
                }
            });
        }
        // TODO: Enable cleaning of signed witness list when necessary
        // cleanSignedWitnesses();
    }

    private void onBootstrapComplete() {
        if (user.getRegisteredArbitrator() != null) {
            UserThread.runAfter(this::doRepublishAllSignedWitnesses, 60);
        }
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // API
    ///////////////////////////////////////////////////////////////////////////////////////////

    public Collection<SignedWitness> getSignedWitnessMapValues() {
        return signedWitnessMap.values();
    }

    /**
     * List of dates as long when accountAgeWitness was signed
     *
     * Witnesses that were added but are no longer considered signed won't be shown
     */
    public List<Long> getVerifiedWitnessDateList(AccountAgeWitness accountAgeWitness) {
        if (!isSignedAccountAgeWitness(accountAgeWitness)) {
            return new ArrayList<>();
        }
        return getSignedWitnessSet(accountAgeWitness).stream()
                .filter(this::verifySignature)
                .map(SignedWitness::getDate)
                .sorted()
                .collect(Collectors.toList());
    }

    /**
     * List of dates as long when accountAgeWitness was signed
     * Not verifying that signatures are correct
     */
    public List<Long> getWitnessDateList(AccountAgeWitness accountAgeWitness) {
        // We do not validate as it would not make sense to cheat one self...
        return getSignedWitnessSet(accountAgeWitness).stream()
                .map(SignedWitness::getDate)
                .sorted()
                .collect(Collectors.toList());
    }

    public boolean isSignedByArbitrator(AccountAgeWitness accountAgeWitness) {
        return getSignedWitnessSet(accountAgeWitness).stream()
                .map(SignedWitness::isSignedByArbitrator)
                .findAny()
                .orElse(false);
    }

    public boolean isFilteredWitness(AccountAgeWitness accountAgeWitness) {
        return getSignedWitnessSet(accountAgeWitness).stream()
                .map(SignedWitness::getWitnessOwnerPubKey)
                .anyMatch(ownerPubKey -> filterManager.isWitnessSignerPubKeyBanned(Utils.HEX.encode(ownerPubKey)));
    }

    private byte[] ownerPubKey(AccountAgeWitness accountAgeWitness) {
        return getSignedWitnessSet(accountAgeWitness).stream()
                .map(SignedWitness::getWitnessOwnerPubKey)
                .findFirst()
                .orElse(null);
    }

    public String ownerPubKeyAsString(AccountAgeWitness accountAgeWitness) {
        return getSignedWitnessSet(accountAgeWitness).stream()
                .map(signedWitness -> Utils.HEX.encode(signedWitness.getWitnessOwnerPubKey()))
                .findFirst()
                .orElse("");
    }

    @VisibleForTesting
    public Set<SignedWitness> getSignedWitnessSetByOwnerPubKey(byte[] ownerPubKey) {
        return getSignedWitnessMapValues().stream()
                .filter(e -> Arrays.equals(e.getWitnessOwnerPubKey(), ownerPubKey))
                .collect(Collectors.toSet());
    }

    public boolean publishOwnSignedWitness(SignedWitness signedWitness) {
        if (!Arrays.equals(signedWitness.getWitnessOwnerPubKey(), keyRing.getPubKeyRing().getSignaturePubKeyBytes()) ||
                !verifySigner(signedWitness)) {
            return false;
        }

        log.info("Publish own signedWitness {}", signedWitness);
        publishSignedWitness(signedWitness);
        return true;
    }

    // Arbitrators sign with EC key
    public void signAndPublishAccountAgeWitness(Coin tradeAmount,
                                                AccountAgeWitness accountAgeWitness,
                                                ECKey key,
                                                PublicKey peersPubKey) {
        signAndPublishAccountAgeWitness(tradeAmount, accountAgeWitness, key, peersPubKey.getEncoded(), new Date().getTime());
    }

    // Arbitrators sign with EC key
    public String signAndPublishAccountAgeWitness(AccountAgeWitness accountAgeWitness,
                                                  ECKey key,
                                                  byte[] peersPubKey,
                                                  long time) {
        var witnessPubKey = peersPubKey == null ? ownerPubKey(accountAgeWitness) : peersPubKey;
        return signAndPublishAccountAgeWitness(MINIMUM_TRADE_AMOUNT_FOR_SIGNING, accountAgeWitness, key, witnessPubKey, time);
    }

    // Arbitrators sign with EC key
    public String signTraderPubKey(ECKey key,
                                   byte[] peersPubKey,
                                   long childSignTime) {
        var time = childSignTime - SIGNER_AGE - 1;
        var dummyAccountAgeWitness = new AccountAgeWitness(Hash.getRipemd160hash(peersPubKey), time);
        return signAndPublishAccountAgeWitness(MINIMUM_TRADE_AMOUNT_FOR_SIGNING, dummyAccountAgeWitness, key, peersPubKey, time);
    }

    // Arbitrators sign with EC key
    private String signAndPublishAccountAgeWitness(Coin tradeAmount,
                                                   AccountAgeWitness accountAgeWitness,
                                                   ECKey key,
                                                   byte[] peersPubKey,
                                                   long time) {
        if (isSignedAccountAgeWitness(accountAgeWitness)) {
            var err = "Arbitrator trying to sign already signed accountagewitness " + accountAgeWitness.toString();
            log.warn(err);
            return err;
        }
        if (peersPubKey == null) {
            var err = "Trying to sign accountAgeWitness " + accountAgeWitness.toString() + "\nwith owner pubkey=null";
            log.warn(err);
            return err;
        }

        String accountAgeWitnessHashAsHex = Utilities.encodeToHex(accountAgeWitness.getHash());
        String signatureBase64 = key.signMessage(accountAgeWitnessHashAsHex);
        SignedWitness signedWitness = new SignedWitness(SignedWitness.VerificationMethod.ARBITRATOR,
                accountAgeWitness.getHash(),
                signatureBase64.getBytes(Charsets.UTF_8),
                key.getPubKey(),
                peersPubKey,
                time,
                tradeAmount.value);
        publishSignedWitness(signedWitness);
        log.info("Arbitrator signed witness {}", signedWitness.toString());
        return "";
    }

    public void selfSignAndPublishAccountAgeWitness(AccountAgeWitness accountAgeWitness) throws CryptoException {
        log.info("Sign own accountAgeWitness {}", accountAgeWitness);
        signAndPublishAccountAgeWitness(MINIMUM_TRADE_AMOUNT_FOR_SIGNING, accountAgeWitness,
                keyRing.getSignatureKeyPair().getPublic());
    }

    // Any peer can sign with DSA key
    public Optional<SignedWitness> signAndPublishAccountAgeWitness(Coin tradeAmount,
                                                                   AccountAgeWitness accountAgeWitness,
                                                                   PublicKey peersPubKey) throws CryptoException {
        if (isSignedAccountAgeWitness(accountAgeWitness)) {
            log.warn("Trader trying to sign already signed accountagewitness {}", accountAgeWitness.toString());
            return Optional.empty();
        }

        if (!isSufficientTradeAmountForSigning(tradeAmount)) {
            log.warn("Trader tried to sign account with too little trade amount");
            return Optional.empty();
        }

        byte[] signature = Sig.sign(keyRing.getSignatureKeyPair().getPrivate(), accountAgeWitness.getHash());
        SignedWitness signedWitness = new SignedWitness(SignedWitness.VerificationMethod.TRADE,
                accountAgeWitness.getHash(),
                signature,
                keyRing.getSignatureKeyPair().getPublic().getEncoded(),
                peersPubKey.getEncoded(),
                new Date().getTime(),
                tradeAmount.value);
        publishSignedWitness(signedWitness);
        log.info("Trader signed witness {}", signedWitness.toString());
        return Optional.of(signedWitness);
    }

    public boolean verifySignature(SignedWitness signedWitness) {
        if (signedWitness.isSignedByArbitrator()) {
            return verifySignatureWithECKey(signedWitness);
        } else {
            return verifySignatureWithDSAKey(signedWitness);
        }
    }

    private boolean verifySignatureWithECKey(SignedWitness signedWitness) {
        P2PDataStorage.ByteArray hash = new P2PDataStorage.ByteArray(signedWitness.getHash());
        if (verifySignatureWithECKeyResultCache.containsKey(hash)) {
            return verifySignatureWithECKeyResultCache.get(hash);
        }
        try {
            String message = Utilities.encodeToHex(signedWitness.getAccountAgeWitnessHash());
            String signatureBase64 = new String(signedWitness.getSignature(), Charsets.UTF_8);
            ECKey key = ECKey.fromPublicOnly(signedWitness.getSignerPubKey());
            if (arbitratorManager.isPublicKeyInList(Utilities.encodeToHex(key.getPubKey()))) {
                key.verifyMessage(message, signatureBase64);
                verifySignatureWithECKeyResultCache.put(hash, true);
                return true;
            } else {
                log.warn("Provided EC key is not in list of valid arbitrators.");
                verifySignatureWithECKeyResultCache.put(hash, false);
                return false;
            }
        } catch (SignatureException e) {
            log.warn("verifySignature signedWitness failed. signedWitness={}", signedWitness);
            log.warn("Caused by ", e);
            verifySignatureWithECKeyResultCache.put(hash, false);
            return false;
        }
    }

    private boolean verifySignatureWithDSAKey(SignedWitness signedWitness) {
        P2PDataStorage.ByteArray hash = new P2PDataStorage.ByteArray(signedWitness.getHash());
        if (verifySignatureWithDSAKeyResultCache.containsKey(hash)) {
            return verifySignatureWithDSAKeyResultCache.get(hash);
        }
        try {
            PublicKey signaturePubKey = Sig.getPublicKeyFromBytes(signedWitness.getSignerPubKey());
            Sig.verify(signaturePubKey, signedWitness.getAccountAgeWitnessHash(), signedWitness.getSignature());
            verifySignatureWithDSAKeyResultCache.put(hash, true);
            return true;
        } catch (CryptoException e) {
            log.warn("verifySignature signedWitness failed. signedWitness={}", signedWitness);
            log.warn("Caused by ", e);
            verifySignatureWithDSAKeyResultCache.put(hash, false);
            return false;
        }
    }

    public Set<SignedWitness> getSignedWitnessSet(AccountAgeWitness accountAgeWitness) {
        P2PDataStorage.ByteArray key = new P2PDataStorage.ByteArray(accountAgeWitness.getHash());
        return signedWitnessSetByAccountAgeWitnessHash.getOrDefault(key, new HashSet<>());
    }

    // SignedWitness objects signed by arbitrators
    public Set<SignedWitness> getArbitratorsSignedWitnessSet(AccountAgeWitness accountAgeWitness) {
        return getSignedWitnessSet(accountAgeWitness).stream()
                .filter(SignedWitness::isSignedByArbitrator)
                .collect(Collectors.toSet());
    }

    // SignedWitness objects signed by any other peer
    public Set<SignedWitness> getTrustedPeerSignedWitnessSet(AccountAgeWitness accountAgeWitness) {
        return getSignedWitnessSet(accountAgeWitness).stream()
                .filter(e -> !e.isSignedByArbitrator())
                .collect(Collectors.toSet());
    }

    public Set<SignedWitness> getRootSignedWitnessSet(boolean includeSignedByArbitrator) {
        return getSignedWitnessMapValues().stream()
                .filter(witness -> getSignedWitnessSetByOwnerPubKey(witness.getSignerPubKey(), new Stack<>()).isEmpty())
                .filter(witness -> includeSignedByArbitrator ||
                        witness.getVerificationMethod() != SignedWitness.VerificationMethod.ARBITRATOR)
                .collect(Collectors.toSet());
    }

    // Find first (in time) SignedWitness per missing signer
    public Set<SignedWitness> getUnsignedSignerPubKeys() {
        var oldestUnsignedSigners = new HashMap<P2PDataStorage.ByteArray, SignedWitness>();
        getRootSignedWitnessSet(false).forEach(signedWitness ->
                oldestUnsignedSigners.compute(new P2PDataStorage.ByteArray(signedWitness.getSignerPubKey()),
                        (key, oldValue) -> oldValue == null ? signedWitness :
                                oldValue.getDate() > signedWitness.getDate() ? signedWitness : oldValue));
        return new HashSet<>(oldestUnsignedSigners.values());
    }

    // We go one level up by using the signer Key to lookup for SignedWitness objects which contain the signerKey as
    // witnessOwnerPubKey
    private Set<SignedWitness> getSignedWitnessSetByOwnerPubKey(byte[] ownerPubKey,
                                                                Stack<P2PDataStorage.ByteArray> excluded) {
        P2PDataStorage.ByteArray key = new P2PDataStorage.ByteArray(ownerPubKey);
        if (signedWitnessSetByOwnerPubKey.containsKey(key)) {
            return signedWitnessSetByOwnerPubKey.get(key).stream()
                    .filter(e -> !excluded.contains(new P2PDataStorage.ByteArray(e.getSignerPubKey())))
                    .collect(Collectors.toSet());

        } else {
            return new HashSet<>();
        }
    }

    public boolean isSignedAccountAgeWitness(AccountAgeWitness accountAgeWitness) {
        return isSignerAccountAgeWitness(accountAgeWitness, new Date().getTime() + SIGNER_AGE);
    }

    public boolean isSignerAccountAgeWitness(AccountAgeWitness accountAgeWitness) {
        return isSignerAccountAgeWitness(accountAgeWitness, new Date().getTime());
    }

    public boolean isSufficientTradeAmountForSigning(Coin tradeAmount) {
        return !tradeAmount.isLessThan(MINIMUM_TRADE_AMOUNT_FOR_SIGNING);
    }

    private boolean verifySigner(SignedWitness signedWitness) {
        return getSignedWitnessSetByOwnerPubKey(signedWitness.getWitnessOwnerPubKey(), new Stack<>()).stream()
                .anyMatch(w -> isValidSignerWitnessInternal(w, signedWitness.getDate(), new Stack<>()));
    }

    /**
     * Checks whether the accountAgeWitness has a valid signature from a peer/arbitrator and is allowed to sign
     * other accounts.
     *
     * @param accountAgeWitness accountAgeWitness
     * @param time              time of signing
     * @return true if accountAgeWitness is allowed to sign at time, false otherwise.
     */
    private boolean isSignerAccountAgeWitness(AccountAgeWitness accountAgeWitness, long time) {
        Stack<P2PDataStorage.ByteArray> excludedPubKeys = new Stack<>();
        Set<SignedWitness> signedWitnessSet = getSignedWitnessSet(accountAgeWitness);
        for (SignedWitness signedWitness : signedWitnessSet) {
            if (isValidSignerWitnessInternal(signedWitness, time, excludedPubKeys)) {
                return true;
            }
        }
        // If we have not returned in the loops or they have been empty we have not found a valid signer.
        return false;
    }

    /**
     * Helper to isValidAccountAgeWitness(accountAgeWitness)
     *
     * @param signedWitness                the signedWitness to validate
     * @param childSignedWitnessDateMillis the date the child SignedWitness was signed or current time if it is a leaf.
     * @param excludedPubKeys              stack to prevent recursive loops
     * @return true if signedWitness is valid, false otherwise.
     */
    private boolean isValidSignerWitnessInternal(SignedWitness signedWitness,
                                                 long childSignedWitnessDateMillis,
                                                 Stack<P2PDataStorage.ByteArray> excludedPubKeys) {
        if (filterManager.isWitnessSignerPubKeyBanned(Utils.HEX.encode(signedWitness.getWitnessOwnerPubKey()))) {
            return false;
        }
        if (!verifySignature(signedWitness)) {
            return false;
        }
        if (signedWitness.isSignedByArbitrator()) {
            // If signed by an arbitrator we don't have to check anything else.
            return true;
        } else {
            if (!verifyDate(signedWitness, childSignedWitnessDateMillis)) {
                return false;
            }
            if (excludedPubKeys.size() >= 2000) {
                // Prevent DoS attack: an attacker floods the SignedWitness db with a long chain that takes lots of time to verify.
                return false;
            }
            excludedPubKeys.push(new P2PDataStorage.ByteArray(signedWitness.getSignerPubKey()));
            excludedPubKeys.push(new P2PDataStorage.ByteArray(signedWitness.getWitnessOwnerPubKey()));
            // Iterate over signedWitness signers
            Set<SignedWitness> signerSignedWitnessSet = getSignedWitnessSetByOwnerPubKey(signedWitness.getSignerPubKey(), excludedPubKeys);
            for (SignedWitness signerSignedWitness : signerSignedWitnessSet) {
                if (isValidSignerWitnessInternal(signerSignedWitness, signedWitness.getDate(), excludedPubKeys)) {
                    return true;
                }
            }
            excludedPubKeys.pop();
            excludedPubKeys.pop();
        }
        // If we have not returned in the loops or they have been empty we have not found a valid signer.
        return false;
    }

    private boolean verifyDate(SignedWitness signedWitness, long childSignedWitnessDateMillis) {
        long childSignedWitnessDateMinusChargebackPeriodMillis = Instant.ofEpochMilli(
                childSignedWitnessDateMillis).minus(SIGNER_AGE, ChronoUnit.MILLIS).toEpochMilli();
        long signedWitnessDateMillis = signedWitness.getDate();
        return signedWitnessDateMillis <= childSignedWitnessDateMinusChargebackPeriodMillis;
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Private
    ///////////////////////////////////////////////////////////////////////////////////////////

    @VisibleForTesting
    public void addToMap(SignedWitness signedWitness) {
        signedWitnessMap.putIfAbsent(signedWitness.getHashAsByteArray(), signedWitness);

        P2PDataStorage.ByteArray accountAgeWitnessHash = new P2PDataStorage.ByteArray(signedWitness.getAccountAgeWitnessHash());
        signedWitnessSetByAccountAgeWitnessHash.putIfAbsent(accountAgeWitnessHash, new HashSet<>());
        signedWitnessSetByAccountAgeWitnessHash.get(accountAgeWitnessHash).add(signedWitness);

        P2PDataStorage.ByteArray ownerPubKey = new P2PDataStorage.ByteArray(signedWitness.getWitnessOwnerPubKey());
        signedWitnessSetByOwnerPubKey.putIfAbsent(ownerPubKey, new HashSet<>());
        signedWitnessSetByOwnerPubKey.get(ownerPubKey).add(signedWitness);
    }

    private void publishSignedWitness(SignedWitness signedWitness) {
        if (!signedWitnessMap.containsKey(signedWitness.getHashAsByteArray())) {
            log.info("broadcast signed witness {}", signedWitness.toString());
            // We set reBroadcast to true to achieve better resilience.
            p2PService.addPersistableNetworkPayload(signedWitness, true);
            addToMap(signedWitness);
        }
    }

    private void doRepublishAllSignedWitnesses() {
        getSignedWitnessMapValues()
                .forEach(signedWitness -> p2PService.addPersistableNetworkPayload(signedWitness, true));
    }

    @VisibleForTesting
    public void removeSignedWitness(SignedWitness signedWitness) {
        signedWitnessMap.remove(signedWitness.getHashAsByteArray());

        P2PDataStorage.ByteArray accountAgeWitnessHash = new P2PDataStorage.ByteArray(signedWitness.getAccountAgeWitnessHash());
        if (signedWitnessSetByAccountAgeWitnessHash.containsKey(accountAgeWitnessHash)) {
            Set<SignedWitness> set = signedWitnessSetByAccountAgeWitnessHash.get(accountAgeWitnessHash);
            set.remove(signedWitness);
            if (set.isEmpty()) {
                signedWitnessSetByAccountAgeWitnessHash.remove(accountAgeWitnessHash);
            }
        }

        P2PDataStorage.ByteArray ownerPubKey = new P2PDataStorage.ByteArray(signedWitness.getWitnessOwnerPubKey());
        if (signedWitnessSetByOwnerPubKey.containsKey(ownerPubKey)) {
            Set<SignedWitness> set = signedWitnessSetByOwnerPubKey.get(ownerPubKey);
            set.remove(signedWitness);
            if (set.isEmpty()) {
                signedWitnessSetByOwnerPubKey.remove(ownerPubKey);
            }
        }
    }

    // Remove SignedWitnesses that are signed by TRADE that also have an ARBITRATOR signature
    // for the same ownerPubKey and AccountAgeWitnessHash
//    private void cleanSignedWitnesses() {
//        var orphans = getRootSignedWitnessSet(false);
//        var signedWitnessesCopy = new HashSet<>(signedWitnessMap.values());
//        signedWitnessesCopy.forEach(sw -> orphans.forEach(orphan -> {
//            if (sw.getVerificationMethod() == SignedWitness.VerificationMethod.ARBITRATOR &&
//                    Arrays.equals(sw.getWitnessOwnerPubKey(), orphan.getWitnessOwnerPubKey()) &&
//                    Arrays.equals(sw.getAccountAgeWitnessHash(), orphan.getAccountAgeWitnessHash())) {
//                signedWitnessMap.remove(orphan.getHashAsByteArray());
//                log.info("Remove duplicate SignedWitness: {}", orphan.toString());
//            }
//        }));
//    }
}
