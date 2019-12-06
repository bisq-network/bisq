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
import bisq.core.support.dispute.arbitration.arbitrator.ArbitratorManager;
import bisq.core.user.User;

import bisq.network.p2p.BootstrapListener;
import bisq.network.p2p.P2PService;
import bisq.network.p2p.storage.P2PDataStorage;
import bisq.network.p2p.storage.persistence.AppendOnlyDataStoreService;

import bisq.common.UserThread;
import bisq.common.crypto.CryptoException;
import bisq.common.crypto.KeyRing;
import bisq.common.crypto.Sig;
import bisq.common.util.Utilities;

import org.bitcoinj.core.Coin;
import org.bitcoinj.core.ECKey;

import javax.inject.Inject;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Charsets;

import java.security.PublicKey;
import java.security.SignatureException;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Stack;
import java.util.stream.Collectors;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class SignedWitnessService {
    public static final long SIGNER_AGE_DAYS = 30;
    public static final long SIGNER_AGE = SIGNER_AGE_DAYS * ChronoUnit.DAYS.getDuration().toMillis();
    static final Coin MINIMUM_TRADE_AMOUNT_FOR_SIGNING = Coin.parseCoin("0.0025");

    private final KeyRing keyRing;
    private final P2PService p2PService;
    private final ArbitratorManager arbitratorManager;
    private final User user;

    private final Map<P2PDataStorage.ByteArray, SignedWitness> signedWitnessMap = new HashMap<>();

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    public SignedWitnessService(KeyRing keyRing,
                                P2PService p2PService,
                                ArbitratorManager arbitratorManager,
                                SignedWitnessStorageService signedWitnessStorageService,
                                AppendOnlyDataStoreService appendOnlyDataStoreService,
                                User user) {
        this.keyRing = keyRing;
        this.p2PService = p2PService;
        this.arbitratorManager = arbitratorManager;
        this.user = user;

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
        p2PService.getP2PDataStorage().getAppendOnlyDataStoreMap().values().forEach(e -> {
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
    }

    private void onBootstrapComplete() {
        if (user.getRegisteredArbitrator() != null) {
            UserThread.runAfter(this::doRepublishAllSignedWitnesses, 60);
        }
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // API
    ///////////////////////////////////////////////////////////////////////////////////////////

    /**
     * List of dates as long when accountAgeWitness was signed
     */
    public List<Long> getVerifiedWitnessDateList(AccountAgeWitness accountAgeWitness) {
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

    // Arbitrators sign with EC key
    public void signAccountAgeWitness(Coin tradeAmount,
                                      AccountAgeWitness accountAgeWitness,
                                      ECKey key,
                                      PublicKey peersPubKey) {
        if (isSignedAccountAgeWitness(accountAgeWitness)) {
            log.warn("Arbitrator trying to sign already signed accountagewitness {}", accountAgeWitness.toString());
            return;
        }

        String accountAgeWitnessHashAsHex = Utilities.encodeToHex(accountAgeWitness.getHash());
        String signatureBase64 = key.signMessage(accountAgeWitnessHashAsHex);
        SignedWitness signedWitness = new SignedWitness(SignedWitness.VerificationMethod.ARBITRATOR,
                accountAgeWitness.getHash(),
                signatureBase64.getBytes(Charsets.UTF_8),
                key.getPubKey(),
                peersPubKey.getEncoded(),
                new Date().getTime(),
                tradeAmount.value);
        publishSignedWitness(signedWitness);
        log.info("Arbitrator signed witness {}", signedWitness.toString());
    }

    // Any peer can sign with DSA key
    public void signAccountAgeWitness(Coin tradeAmount,
                                      AccountAgeWitness accountAgeWitness,
                                      PublicKey peersPubKey) throws CryptoException {
        if (isSignedAccountAgeWitness(accountAgeWitness)) {
            log.warn("Trader trying to sign already signed accountagewitness {}", accountAgeWitness.toString());
            return;
        }

        if (!isSufficientTradeAmountForSigning(tradeAmount)) {
            log.warn("Trader tried to sign account with too little trade amount");
            return;
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
    }

    public boolean verifySignature(SignedWitness signedWitness) {
        if (signedWitness.isSignedByArbitrator()) {
            return verifySignatureWithECKey(signedWitness);
        } else {
            return verifySignatureWithDSAKey(signedWitness);
        }
    }

    private boolean verifySignatureWithECKey(SignedWitness signedWitness) {
        try {
            String message = Utilities.encodeToHex(signedWitness.getAccountAgeWitnessHash());
            String signatureBase64 = new String(signedWitness.getSignature(), Charsets.UTF_8);
            ECKey key = ECKey.fromPublicOnly(signedWitness.getSignerPubKey());
            if (arbitratorManager.isPublicKeyInList(Utilities.encodeToHex(key.getPubKey()))) {
                key.verifyMessage(message, signatureBase64);
                return true;
            } else {
                log.warn("Provided EC key is not in list of valid arbitrators.");
                return false;
            }
        } catch (SignatureException e) {
            log.warn("verifySignature signedWitness failed. signedWitness={}", signedWitness);
            log.warn("Caused by ", e);
            return false;
        }
    }

    private boolean verifySignatureWithDSAKey(SignedWitness signedWitness) {
        try {
            PublicKey signaturePubKey = Sig.getPublicKeyFromBytes(signedWitness.getSignerPubKey());
            Sig.verify(signaturePubKey, signedWitness.getAccountAgeWitnessHash(), signedWitness.getSignature());
            return true;
        } catch (CryptoException e) {
            log.warn("verifySignature signedWitness failed. signedWitness={}", signedWitness);
            log.warn("Caused by ", e);
            return false;
        }
    }

    private Set<SignedWitness> getSignedWitnessSet(AccountAgeWitness accountAgeWitness) {
        return signedWitnessMap.values().stream()
                .filter(e -> Arrays.equals(e.getAccountAgeWitnessHash(), accountAgeWitness.getHash()))
                .collect(Collectors.toSet());
    }

    // SignedWitness objects signed by arbitrators
    public Set<SignedWitness> getArbitratorsSignedWitnessSet(AccountAgeWitness accountAgeWitness) {
        return signedWitnessMap.values().stream()
                .filter(SignedWitness::isSignedByArbitrator)
                .filter(e -> Arrays.equals(e.getAccountAgeWitnessHash(), accountAgeWitness.getHash()))
                .collect(Collectors.toSet());
    }

    // SignedWitness objects signed by any other peer
    public Set<SignedWitness> getTrustedPeerSignedWitnessSet(AccountAgeWitness accountAgeWitness) {
        return signedWitnessMap.values().stream()
                .filter(e -> !e.isSignedByArbitrator())
                .filter(e -> Arrays.equals(e.getAccountAgeWitnessHash(), accountAgeWitness.getHash()))
                .collect(Collectors.toSet());
    }

    // We go one level up by using the signer Key to lookup for SignedWitness objects which contain the signerKey as
    // witnessOwnerPubKey
    private Set<SignedWitness> getSignedWitnessSetByOwnerPubKey(byte[] ownerPubKey,
                                                                Stack<P2PDataStorage.ByteArray> excluded) {
        return signedWitnessMap.values().stream()
                .filter(e -> Arrays.equals(e.getWitnessOwnerPubKey(), ownerPubKey))
                .filter(e -> !excluded.contains(new P2PDataStorage.ByteArray(e.getSignerPubKey())))
                .collect(Collectors.toSet());
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
     * @param childSignedWitnessDateMillis the date the child SignedWitness was signed or current time if it is a leave.
     * @param excludedPubKeys              stack to prevent recursive loops
     * @return true if signedWitness is valid, false otherwise.
     */
    private boolean isValidSignerWitnessInternal(SignedWitness signedWitness,
                                                 long childSignedWitnessDateMillis,
                                                 Stack<P2PDataStorage.ByteArray> excludedPubKeys) {
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
    void addToMap(SignedWitness signedWitness) {
        // TODO: Perhaps filter out all but one signedwitness per accountagewitness
        signedWitnessMap.putIfAbsent(signedWitness.getHashAsByteArray(), signedWitness);
    }

    private void publishSignedWitness(SignedWitness signedWitness) {
        if (!signedWitnessMap.containsKey(signedWitness.getHashAsByteArray())) {
            log.info("broadcast signed witness {}", signedWitness.toString());
            p2PService.addPersistableNetworkPayload(signedWitness, false);
        }
    }

    private void doRepublishAllSignedWitnesses() {
        signedWitnessMap.forEach((e, signedWitness) -> p2PService.addPersistableNetworkPayload(signedWitness, true));
    }
}
