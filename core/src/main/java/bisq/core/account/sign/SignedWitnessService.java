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
import bisq.core.crypto.LowRSigningKey;
import bisq.core.filter.FilterPolicyService;
import bisq.core.offer.OfferRestrictions;
import bisq.core.support.dispute.arbitration.arbitrator.ArbitratorManager;

import bisq.network.p2p.P2PService;
import bisq.network.p2p.storage.P2PDataStorage;
import bisq.network.p2p.storage.persistence.AppendOnlyDataStoreService;

import bisq.common.config.Config;
import bisq.common.crypto.CryptoException;
import bisq.common.crypto.Hash;
import bisq.common.crypto.KeyRing;
import bisq.common.crypto.Sig;
import bisq.common.util.DateUtil;
import bisq.common.util.Utilities;

import org.bitcoinj.core.Coin;
import org.bitcoinj.core.ECKey;
import org.bitcoinj.core.Utils;

import javax.inject.Inject;
import javax.inject.Named;

import com.google.common.annotations.VisibleForTesting;

import java.security.PublicKey;
import java.security.SignatureException;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import java.nio.charset.StandardCharsets;

import java.math.BigInteger;

import java.util.Arrays;
import java.util.Base64;
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
    // The last witness of a mainnet arbitrator is from 2021-01-25, and no code creates one anymore. A witness that the
    // network relays is dated within a day of its receipt, so a copy of an arbitrator's witness that someone relays
    // now cannot be dated earlier. Only seed nodes serve older witnesses, in the initial data.
    @VisibleForTesting
    static final long MAINNET_ARBITRATOR_WITNESS_DATE_LIMIT = Instant.parse("2021-02-01T00:00:00Z").toEpochMilli();
    private static final long SIGNER_AGE = SIGNER_AGE_DAYS * ChronoUnit.DAYS.getDuration().toMillis();
    public static final Coin MINIMUM_TRADE_AMOUNT_FOR_SIGNING = OfferRestrictions.TOLERATED_SMALL_TRADE_AMOUNT.divide(4);
    // Tolerance for clock differences between the peers when we check the date of a SignedWitness we received
    // from the trading peer.
    private static final long SIGN_DATE_TOLERANCE = ChronoUnit.DAYS.getDuration().toMillis();

    private final KeyRing keyRing;
    private final P2PService p2PService;
    @SuppressWarnings("deprecation")
    private final ArbitratorManager arbitratorManager;
    private final SignedWitnessStorageService signedWitnessStorageService;
    private final FilterPolicyService filterPolicyService;
    private final boolean allowMainnetSignedWitnessesWithDevPrivilegeKeys;
    private final Clock clock;

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
                                @SuppressWarnings("deprecation") ArbitratorManager arbitratorManager,
                                SignedWitnessStorageService signedWitnessStorageService,
                                AppendOnlyDataStoreService appendOnlyDataStoreService,
                                FilterPolicyService filterPolicyService,
                                @Named(Config.ALLOW_MAINNET_SIGNED_WITNESSES_WITH_DEV_PRIVILEGE_KEYS)
                                boolean allowMainnetSignedWitnessesWithDevPrivilegeKeys,
                                Clock clock) {
        this.keyRing = keyRing;
        this.p2PService = p2PService;
        this.arbitratorManager = arbitratorManager;
        this.signedWitnessStorageService = signedWitnessStorageService;
        this.filterPolicyService = filterPolicyService;
        this.allowMainnetSignedWitnessesWithDevPrivilegeKeys = allowMainnetSignedWitnessesWithDevPrivilegeKeys;
        this.clock = clock;

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

        // TODO: Enable cleaning of signed witness list when necessary
        // cleanSignedWitnesses();
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
        return getVerifiedWitnessDateList(accountAgeWitness, Optional.empty());
    }

    public List<Long> getVerifiedWitnessDateList(AccountAgeWitness accountAgeWitness,
                                                  byte[] expectedWitnessOwnerPubKey) {
        return getVerifiedWitnessDateList(accountAgeWitness, Optional.of(expectedWitnessOwnerPubKey));
    }

    /**
     * Returns qualifying dates after the caller has independently proven that {@code provenWitnessOwnerPubKey}
     * owns the account-age witness hash. Legacy arbitrator signing swapped the buyer and seller owner keys before
     * commit 09141eba92. A direct arbitrator signature still authenticates the account-age witness hash, so those
     * records do not need the unauthenticated legacy owner field to match. Trade-signed leaves remain owner-bound.
     */
    public List<Long> getVerifiedWitnessDateListForProvenOwner(AccountAgeWitness accountAgeWitness,
                                                                byte[] provenWitnessOwnerPubKey) {
        return getSignedWitnessSet(accountAgeWitness).stream()
                .filter(signedWitness -> isValidForProvenOwner(signedWitness, provenWitnessOwnerPubKey))
                .map(SignedWitness::getDate)
                .sorted()
                .collect(Collectors.toList());
    }

    private List<Long> getVerifiedWitnessDateList(AccountAgeWitness accountAgeWitness,
                                                   Optional<byte[]> expectedWitnessOwnerPubKey) {
        return getSignedWitnessSet(accountAgeWitness).stream()
                .filter(signedWitness -> expectedWitnessOwnerPubKey
                        .map(expected -> Arrays.equals(expected, signedWitness.getWitnessOwnerPubKey()))
                        .orElse(true))
                .filter(signedWitness -> isValidSignerWitnessInternal(
                        signedWitness,
                        new Date().getTime() + SIGNER_AGE,
                        new Stack<>()))
                .map(SignedWitness::getDate)
                .sorted()
                .collect(Collectors.toList());
    }

    private boolean isValidForProvenOwner(SignedWitness signedWitness, byte[] provenWitnessOwnerPubKey) {
        if (Arrays.equals(provenWitnessOwnerPubKey, signedWitness.getWitnessOwnerPubKey())) {
            return isValidSignerWitnessInternal(
                    signedWitness,
                    new Date().getTime() + SIGNER_AGE,
                    new Stack<>());
        }

        return signedWitness.isSignedByArbitrator() && verifySignature(signedWitness);
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
                .filter(SignedWitness::isSignedByArbitrator)
                .anyMatch(this::verifySignature);
    }

    public boolean isFilteredWitness(AccountAgeWitness accountAgeWitness) {
        return getSignedWitnessSet(accountAgeWitness).stream()
                .map(SignedWitness::getWitnessOwnerPubKey)
                .anyMatch(ownerPubKey -> filterPolicyService.isWitnessSignerPubKeyBanned(Utils.HEX.encode(ownerPubKey)));
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

    // The signedWitness was created by the trading peer and received with a trade message, so all its fields
    // are peer controlled. We publish it with the local publication API which does not apply the date tolerance
    // check of the P2P layer, and we add it to our maps and to our persisted append-only store. We therefore
    // build the witness the peer was supposed to create for that trade from our own validated trade data and
    // accept the received one only if it is identical to it. Two fields cannot be derived from the trade data:
    // - The signature. It is the reason why we cannot create the witness ourselves. We verify it below.
    // - The date. The trade message can be delivered late over the mailbox, so the date can be legitimately
    //   much older than the current time and a tolerance check against the current time is not applicable
    //   here. The peer signs when the payout transaction gets published, thus we require instead that the date
    //   is not before the start of the trade and not in the future.
    public boolean publishOwnSignedWitness(SignedWitness signedWitness,
                                           Coin tradeAmount,
                                           AccountAgeWitness myAccountAgeWitness,
                                           byte[] signerPubKey,
                                           byte[] witnessOwnerPubKey,
                                           long tradeStartDate) {
        SignedWitness expected = new SignedWitness(SignedWitness.VerificationMethod.TRADE,
                myAccountAgeWitness.getHash(),
                signedWitness.getSignature(),
                signerPubKey,
                witnessOwnerPubKey,
                signedWitness.getDate(),
                tradeAmount.value);
        if (!expected.equals(signedWitness)) {
            log.warn("Received signedWitness is not the one the peer was supposed to create for that trade. " +
                    "signedWitness={}", signedWitness);
            return false;
        }
        if (!isSufficientTradeAmountForSigning(tradeAmount)) {
            log.warn("Received signedWitness is from a trade with a too low trade amount. signedWitness={}",
                    signedWitness);
            return false;
        }
        if (!isSignDateInTradeRange(signedWitness.getDate(), tradeStartDate)) {
            log.warn("Received signedWitness date is outside allowed bounds (before trade start or in the future). signedWitness={}",
                    signedWitness);
            return false;
        }
        if (!verifySignature(signedWitness)) {
            log.warn("Received signedWitness has an invalid signature. signedWitness={}", signedWitness);
            return false;
        }

        // Does the witness signer hold a witness that makes them a valid signer at the date of this signature?
        long signedWitnessDate = signedWitness.getDate();
        if (!verifySigner(signerPubKey, signedWitnessDate)) {
            log.warn("The signer of the received signedWitness is not a valid signer. signedWitness={}",
                    signedWitness);
            return false;
        }

        log.info("Publish own signedWitness {}", signedWitness);
        publishSignedWitness(signedWitness);
        return true;
    }

    // We compare the bounds directly instead of calculating a difference, so that a peer controlled extreme
    // date value cannot cause a long overflow.
    private boolean isSignDateInTradeRange(long date, long tradeStartDate) {
        return DateUtil.isWithinBounds(date,
                tradeStartDate - SIGN_DATE_TOLERANCE,
                clock.millis() + SIGN_DATE_TOLERANCE);
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
        String signatureBase64 = LowRSigningKey.from(key).signMessage(accountAgeWitnessHashAsHex);
        SignedWitness signedWitness = new SignedWitness(SignedWitness.VerificationMethod.ARBITRATOR,
                accountAgeWitness.getHash(),
                signatureBase64.getBytes(StandardCharsets.UTF_8),
                key.getPubKey(),
                peersPubKey,
                time,
                tradeAmount.value);
        publishSignedWitness(signedWitness);
        log.info("Arbitrator signed witness {}", signedWitness);
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
        log.info("Trader signed witness {}", signedWitness);
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
        // The lists of arbitrators hold the compressed keys, so a key in another encoding is in none of them
        String pubKeyAsHex = Utilities.encodeToHex(signedWitness.getSignerPubKey());
        // Before the cached result, which is keyed by the hash of the witness, and the hash does not cover the date
        if (signedWitness.getDate() >= MAINNET_ARBITRATOR_WITNESS_DATE_LIMIT &&
                arbitratorManager.isLegacyArbitratorPublicKey(pubKeyAsHex)) {
            log.warn("Witness of a mainnet arbitrator dated after arbitration ended. signedWitness={}", signedWitness);
            return false;
        }
        P2PDataStorage.ByteArray hash = new P2PDataStorage.ByteArray(signedWitness.getHash());
        if (verifySignatureWithECKeyResultCache.containsKey(hash)) {
            return verifySignatureWithECKeyResultCache.get(hash);
        }
        try {
            String message = Utilities.encodeToHex(signedWitness.getAccountAgeWitnessHash());
            String signatureBase64 = new String(signedWitness.getSignature(), StandardCharsets.UTF_8);
            ECKey key = ECKey.fromPublicOnly(signedWitness.getSignerPubKey());
            boolean isAcceptedArbitrator = arbitratorManager.isPublicKeyInList(pubKeyAsHex) ||
                    (allowMainnetSignedWitnessesWithDevPrivilegeKeys &&
                            arbitratorManager.isLegacyArbitratorPublicKey(pubKeyAsHex));
            if (isAcceptedArbitrator) {
                // A message signature verifies in several encodings, and each gives the witness another hash. With
                // only the encoding that bitcoinj creates, a copy of an arbitrator's witness with another owner has
                // the hash of the original, so a node that has the original does not store the copy.
                if (!isCanonicalMessageSignature(signedWitness.getSignature(), key)) {
                    log.warn("Signature of the arbitrator is not in its canonical encoding. signedWitness={}",
                            signedWitness);
                    verifySignatureWithECKeyResultCache.put(hash, false);
                    return false;
                }
                key.verifyMessage(message, signatureBase64);
                verifySignatureWithECKeyResultCache.put(hash, true);
                return true;
            } else {
                log.warn("Provided EC key is not in list of valid arbitrators.");
                verifySignatureWithECKeyResultCache.put(hash, false);
                return false;
            }
        } catch (SignatureException | RuntimeException e) {
            // A malformed key or signature makes bitcoinj throw a RuntimeException
            log.warn("verifySignature signedWitness failed. signedWitness={}", signedWitness);
            log.warn("Caused by ", e);
            verifySignatureWithECKeyResultCache.put(hash, false);
            return false;
        }
    }

    // bitcoinj signs a message as 65 bytes in padded Base64: a header with the recovery id and the compressed flag of
    // the key, then r and a low s
    @VisibleForTesting
    static boolean isCanonicalMessageSignature(byte[] signatureBase64, ECKey key) {
        byte[] signature;
        try {
            signature = Base64.getDecoder().decode(signatureBase64);
        } catch (IllegalArgumentException e) {
            return false;
        }
        if (signature.length != 65 || !Arrays.equals(Base64.getEncoder().encode(signature), signatureBase64)) {
            return false;
        }
        int header = signature[0] & 0xFF;
        boolean isCompressed = header >= 31;
        if (header < 27 || header > 34 || isCompressed != key.isCompressed()) {
            return false;
        }
        BigInteger s = new BigInteger(1, Arrays.copyOfRange(signature, 33, 65));
        return s.compareTo(ECKey.HALF_CURVE_ORDER) <= 0;
    }

    // The JDK signs with DSA as a DER SEQUENCE of two INTEGERs r and s, each positive and in its shortest form, and
    // with lengths of one byte
    @VisibleForTesting
    static boolean isCanonicalDerSignature(byte[] signature) {
        if (signature.length < 8 || signature[0] != 0x30 || (signature[1] & 0x80) != 0 ||
                signature[1] != signature.length - 2) {
            return false;
        }
        int offset = 2;
        for (int integer = 0; integer < 2; integer++) {
            if (offset + 2 > signature.length || signature[offset] != 0x02) {
                return false;
            }
            int length = signature[offset + 1] & 0xFF;
            int start = offset + 2;
            if (length == 0 || length > 0x7F || start + length > signature.length) {
                return false;
            }
            boolean isNegative = (signature[start] & 0x80) != 0;
            boolean hasNeedlessZero = signature[start] == 0 && (length == 1 || (signature[start + 1] & 0x80) == 0);
            if (isNegative || hasNeedlessZero) {
                return false;
            }
            offset = start + length;
        }
        return offset == signature.length;
    }

    private boolean verifySignatureWithDSAKey(SignedWitness signedWitness) {
        P2PDataStorage.ByteArray hash = new P2PDataStorage.ByteArray(signedWitness.getHash());
        if (verifySignatureWithDSAKeyResultCache.containsKey(hash)) {
            return verifySignatureWithDSAKeyResultCache.get(hash);
        }
        try {
            // The hash of the witness covers the signature and the signer key, and the JDK verifies a signature and
            // reads a key in more than one encoding, for example a DER INTEGER without its leading zero. With only
            // the encodings that the JDK creates, a copy of a witness with another owner has the hash of the
            // original, so a node that has the original does not store the copy.
            if (!isCanonicalDerSignature(signedWitness.getSignature())) {
                log.warn("Signature of the trade is not in its canonical encoding. signedWitness={}", signedWitness);
                verifySignatureWithDSAKeyResultCache.put(hash, false);
                return false;
            }
            PublicKey signaturePubKey = Sig.getPublicKeyFromBytes(signedWitness.getSignerPubKey());
            if (!Arrays.equals(signaturePubKey.getEncoded(), signedWitness.getSignerPubKey())) {
                log.warn("Signer key of the trade is not in its canonical encoding. signedWitness={}", signedWitness);
                verifySignatureWithDSAKeyResultCache.put(hash, false);
                return false;
            }
            boolean isValid = Sig.verify(signaturePubKey, signedWitness.getAccountAgeWitnessHash(), signedWitness.getSignature());
            if (!isValid) {
                log.warn("verifySignature signedWitness failed. signedWitness={}", signedWitness);
            }
            verifySignatureWithDSAKeyResultCache.put(hash, isValid);
            return isValid;
        } catch (CryptoException | RuntimeException e) {
            // A malformed key is reported as a KeyConversionException, which is a RuntimeException
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

    // We check if the signer was allowed to sign at the time they signed. We look up the witnesses owned by the
    // signer, the same way isValidSignerWitnessInternal walks up the chain. Looking up the witnesses owned by
    // the witness owner instead would ask if the receiver of the signature is a signer, which is not the
    // question here and which would exclude the case that our first account gets signed.
    private boolean verifySigner(byte[] signerPubKey, long signedWitnessDate) {
        return getSignedWitnessSetByOwnerPubKey(signerPubKey, new Stack<>()).stream()
                .anyMatch(w -> isValidSignerWitnessInternal(w, signedWitnessDate, new Stack<>()));
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
        if (filterPolicyService.isWitnessSignerPubKeyBanned(Utils.HEX.encode(signedWitness.getWitnessOwnerPubKey()))) {
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
        if (!hasLookupKeys(signedWitness)) {
            log.warn("We ignore a signed witness with an empty account hash or key. signedWitness={}", signedWitness);
            return;
        }
        signedWitnessMap.putIfAbsent(signedWitness.getHashAsByteArray(), signedWitness);

        P2PDataStorage.ByteArray accountAgeWitnessHash = new P2PDataStorage.ByteArray(signedWitness.getAccountAgeWitnessHash());
        signedWitnessSetByAccountAgeWitnessHash.putIfAbsent(accountAgeWitnessHash, new HashSet<>());
        signedWitnessSetByAccountAgeWitnessHash.get(accountAgeWitnessHash).add(signedWitness);

        P2PDataStorage.ByteArray ownerPubKey = new P2PDataStorage.ByteArray(signedWitness.getWitnessOwnerPubKey());
        signedWitnessSetByOwnerPubKey.putIfAbsent(ownerPubKey, new HashSet<>());
        signedWitnessSetByOwnerPubKey.get(ownerPubKey).add(signedWitness);
    }

    // Anyone can publish a witness, and nothing checks its fields before it is stored. The lookup maps key witnesses by
    // the account hash and the keys, which must not be empty. Such a witness could not be valid anyway.
    private static boolean hasLookupKeys(SignedWitness signedWitness) {
        return signedWitness.getAccountAgeWitnessHash().length > 0 &&
                signedWitness.getSignerPubKey().length > 0 &&
                signedWitness.getWitnessOwnerPubKey().length > 0;
    }

    private void publishSignedWitness(SignedWitness signedWitness) {
        if (!signedWitnessMap.containsKey(signedWitness.getHashAsByteArray())) {
            log.info("broadcast signed witness {}", signedWitness);
            // We set reBroadcast to true to achieve better resilience.
            p2PService.addPersistableNetworkPayload(signedWitness, true);
            addToMap(signedWitness);
        }
    }

    @VisibleForTesting
    public void removeSignedWitness(SignedWitness signedWitness) {
        if (!hasLookupKeys(signedWitness)) {
            return;
        }
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
