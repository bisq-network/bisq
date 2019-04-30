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
import bisq.core.account.witness.AccountAgeWitnessService;
import bisq.core.arbitration.ArbitratorManager;
import bisq.core.payment.payload.PaymentAccountPayload;

import bisq.network.p2p.P2PService;
import bisq.network.p2p.storage.P2PDataStorage;
import bisq.network.p2p.storage.persistence.AppendOnlyDataStoreService;

import bisq.common.crypto.CryptoException;
import bisq.common.crypto.KeyRing;
import bisq.common.crypto.Sig;
import bisq.common.util.Utilities;

import org.bitcoinj.core.Coin;
import org.bitcoinj.core.ECKey;

import javax.inject.Inject;

import com.google.common.base.Charsets;

import java.security.PublicKey;
import java.security.SignatureException;

import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class SignedWitnessService {
    private final KeyRing keyRing;
    private final P2PService p2PService;
    private final AccountAgeWitnessService accountAgeWitnessService;
    private final ArbitratorManager arbitratorManager;
    private final Map<P2PDataStorage.ByteArray, SignedWitness> signedWitnessMap = new HashMap<>();


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    public SignedWitnessService(KeyRing keyRing,
                                P2PService p2PService,
                                AccountAgeWitnessService accountAgeWitnessService,
                                ArbitratorManager arbitratorManager,
                                SignedWitnessStorageService signedWitnessStorageService,
                                AppendOnlyDataStoreService appendOnlyDataStoreService) {
        this.keyRing = keyRing;
        this.p2PService = p2PService;
        this.accountAgeWitnessService = accountAgeWitnessService;
        this.arbitratorManager = arbitratorManager;

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

        // At startup the P2PDataStorage initializes earlier, otherwise we ge the listener called.
        p2PService.getP2PDataStorage().getAppendOnlyDataStoreMap().values().forEach(e -> {
            if (e instanceof SignedWitness)
                addToMap((SignedWitness) e);
        });
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // API
    ///////////////////////////////////////////////////////////////////////////////////////////

    public List<Long> getMyWitnessAgeList(PaymentAccountPayload myPaymentAccountPayload) {
        AccountAgeWitness accountAgeWitness = accountAgeWitnessService.getMyWitness(myPaymentAccountPayload);
        // We do not validate as it would not make sense to cheat one self...
        return getSignedWitnessSet(accountAgeWitness).stream()
                .map(SignedWitness::getDate)
                .sorted()
                .collect(Collectors.toList());
    }


    public List<Long> getVerifiedWitnessAgeList(AccountAgeWitness accountAgeWitness) {
        return signedWitnessMap.values().stream()
                .filter(e -> Arrays.equals(e.getWitnessHash(), accountAgeWitness.getHash()))
                .filter(this::verify)
                .map(SignedWitness::getDate)
                .sorted()
                .collect(Collectors.toList());
    }

    // Arbitrators sign with EC key
    public SignedWitness signAccountAgeWitness(Coin tradeAmount, AccountAgeWitness accountAgeWitness, ECKey key, PublicKey peersPubKey) {
        String accountAgeWitnessHashAsHex = Utilities.encodeToHex(accountAgeWitness.getHash());
        String signatureBase64 = key.signMessage(accountAgeWitnessHashAsHex);
        return new SignedWitness(true,
                accountAgeWitness.getHash(),
                signatureBase64.getBytes(Charsets.UTF_8),
                key.getPubKey(),
                peersPubKey.getEncoded(),
                new Date().getTime(),
                tradeAmount.value);
    }

    // Any peer can sign with DSA key
    public SignedWitness sign(Coin tradeAmount, AccountAgeWitness accountAgeWitness, PublicKey peersPubKey) throws CryptoException {
        byte[] signature = Sig.sign(keyRing.getSignatureKeyPair().getPrivate(), accountAgeWitness.getHash());
        return new SignedWitness(false,
                accountAgeWitness.getHash(),
                signature,
                keyRing.getSignatureKeyPair().getPublic().getEncoded(),
                peersPubKey.getEncoded(),
                new Date().getTime(),
                tradeAmount.value);
    }

    public boolean verify(SignedWitness signedWitness) {
        if (signedWitness.isSignedByArbitrator()) {
            return verifyWithECKey(signedWitness);
        } else {
            return verifyWithDSAKey(signedWitness);
        }
    }

    private boolean verifyWithECKey(SignedWitness signedWitness) {
        try {
            String message = Utilities.encodeToHex(signedWitness.getWitnessHash());
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
            log.warn("verify signedWitness failed. signedWitness={}", signedWitness);
            return false;
        }
    }

    private boolean verifyWithDSAKey(SignedWitness signedWitness) {
        try {
            PublicKey signaturePubKey = Sig.getPublicKeyFromBytes(signedWitness.getSignerPubKey());
            Sig.verify(signaturePubKey, signedWitness.getWitnessHash(), signedWitness.getSignature());
            return true;
        } catch (CryptoException e) {
            log.warn("verify signedWitness failed. signedWitness={}", signedWitness);
            return false;
        }
    }

    public Set<SignedWitness> getSignedWitnessSet(AccountAgeWitness accountAgeWitness) {
        return signedWitnessMap.values().stream()
                .filter(e -> Arrays.equals(e.getWitnessHash(), accountAgeWitness.getHash()))
                .collect(Collectors.toSet());
    }

    // SignedWitness objects signed by arbitrators
    public Set<SignedWitness> getArbitratorsSignedWitnessSet(AccountAgeWitness accountAgeWitness) {
        return signedWitnessMap.values().stream()
                .filter(SignedWitness::isSignedByArbitrator)
                .filter(e -> Arrays.equals(e.getWitnessHash(), accountAgeWitness.getHash()))
                .collect(Collectors.toSet());
    }

    // SignedWitness objects signed by any other peer
    public Set<SignedWitness> getTrustedPeerSignedWitnessSet(AccountAgeWitness accountAgeWitness) {
        return signedWitnessMap.values().stream()
                .filter(e -> !e.isSignedByArbitrator())
                .filter(e -> Arrays.equals(e.getWitnessHash(), accountAgeWitness.getHash()))
                .collect(Collectors.toSet());
    }

    // We go one level up by using the signer Key to lookup for SignedWitness objects which contain the signerKey as
    // witnessOwnerPubKey
    public Set<SignedWitness> getSignedWitnessSetBySignerPubKey(byte[] signerPubKey) {
        return signedWitnessMap.values().stream()
                .filter(e -> Arrays.equals(e.getWitnessOwnerPubKey(), signerPubKey))
                .collect(Collectors.toSet());
    }

    //TODO pass list and remove items once processed to avoid endless loop in case of multiple sigs
    public boolean isValidAccountAgeWitness(AccountAgeWitness accountAgeWitness) {
        Set<SignedWitness> arbitratorsSignedWitnessSet = getArbitratorsSignedWitnessSet(accountAgeWitness);
        if (!arbitratorsSignedWitnessSet.isEmpty()) {
            // Our peer was signed by arbitrator. We only check it at least one is valid and don't need to go further.
            return arbitratorsSignedWitnessSet.stream().anyMatch(this::verify);
        } else {
            Set<SignedWitness> trustedPeerSignedWitnessSet = getTrustedPeerSignedWitnessSet(accountAgeWitness);
            // We have some SignedWitness signed by any trusted peer and need to see if it is valid and has a
            // valid chain back to the arbitrators SignedWitness.
            for (SignedWitness trustedPeerSignedWitness : trustedPeerSignedWitnessSet) {
                if (verify(trustedPeerSignedWitness)) {
                    // The signature is valid. Lets see who has signed it.
                    // Get set of SignedWitness objects signer was witness owner.
                    Set<SignedWitness> signersWitnessSet = getSignedWitnessSetBySignerPubKey(trustedPeerSignedWitness.getSignerPubKey());
                    for (SignedWitness signersWitness : signersWitnessSet) {
                        if (verify(signersWitness)) {
                            Optional<AccountAgeWitness> optionalWitness = accountAgeWitnessService.getWitnessByHash(signersWitness.getWitnessHash());
                            if (optionalWitness.isPresent()) {
                                // Enter recursion
                                boolean isvalid = isValidAccountAgeWitness(optionalWitness.get());
                                if (isvalid)
                                    return true;
                            }
                        }
                    }
                }
            }
            // If we have not returned in the loops or they have been empty we have not found a valid signer.
            return false;
        }
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Private
    ///////////////////////////////////////////////////////////////////////////////////////////


    private void addToMap(SignedWitness signedWitness) {
        signedWitnessMap.putIfAbsent(signedWitness.getHashAsByteArray(), signedWitness);
    }
}
