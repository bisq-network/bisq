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
import bisq.core.arbitration.BuyerDataItem;
import bisq.core.arbitration.Dispute;
import bisq.core.arbitration.DisputeManager;
import bisq.core.arbitration.DisputeResult;
import bisq.core.payment.payload.PaymentAccountPayload;
import bisq.core.payment.payload.PaymentMethod;

import bisq.network.p2p.P2PService;
import bisq.network.p2p.storage.P2PDataStorage;
import bisq.network.p2p.storage.persistence.AppendOnlyDataStoreService;

import bisq.common.crypto.CryptoException;
import bisq.common.crypto.KeyRing;
import bisq.common.crypto.PubKeyRing;
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
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.Stack;
import java.util.stream.Collectors;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import org.jetbrains.annotations.Nullable;

@Slf4j
public class SignedWitnessService {
    public static final long CHARGEBACK_SAFETY_DAYS = 30;

    private final KeyRing keyRing;
    private final P2PService p2PService;
    private final AccountAgeWitnessService accountAgeWitnessService;
    private final ArbitratorManager arbitratorManager;
    private final DisputeManager disputeManager;

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
                                AppendOnlyDataStoreService appendOnlyDataStoreService,
                                DisputeManager disputeManager) {
        this.keyRing = keyRing;
        this.p2PService = p2PService;
        this.accountAgeWitnessService = accountAgeWitnessService;
        this.arbitratorManager = arbitratorManager;
        this.disputeManager = disputeManager;

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
                .filter(this::verifySignature)
                .map(SignedWitness::getDate)
                .sorted()
                .collect(Collectors.toList());
    }

    // Arbitrators sign with EC key
    public SignedWitness signAccountAgeWitness(Coin tradeAmount, AccountAgeWitness accountAgeWitness, ECKey key, PublicKey peersPubKey) {
        String accountAgeWitnessHashAsHex = Utilities.encodeToHex(accountAgeWitness.getHash());
        String signatureBase64 = key.signMessage(accountAgeWitnessHashAsHex);
        SignedWitness signedWitness = new SignedWitness(true,
                accountAgeWitness.getHash(),
                signatureBase64.getBytes(Charsets.UTF_8),
                key.getPubKey(),
                peersPubKey.getEncoded(),
                new Date().getTime(),
                tradeAmount.value);
        publishSignedWitness(signedWitness);
        return signedWitness;
    }

    // Any peer can sign with DSA key
    public SignedWitness signAccountAgeWitness(Coin tradeAmount, AccountAgeWitness accountAgeWitness, PublicKey peersPubKey) throws CryptoException {
        byte[] signature = Sig.sign(keyRing.getSignatureKeyPair().getPrivate(), accountAgeWitness.getHash());
        SignedWitness signedWitness = new SignedWitness(false,
                accountAgeWitness.getHash(),
                signature,
                keyRing.getSignatureKeyPair().getPublic().getEncoded(),
                peersPubKey.getEncoded(),
                new Date().getTime(),
                tradeAmount.value);
        publishSignedWitness(signedWitness);
        return signedWitness;
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
            log.warn("verifySignature signedWitness failed. signedWitness={}", signedWitness);
            log.warn("Caused by ", e);
            return false;
        }
    }

    private boolean verifySignatureWithDSAKey(SignedWitness signedWitness) {
        try {
            PublicKey signaturePubKey = Sig.getPublicKeyFromBytes(signedWitness.getSignerPubKey());
            Sig.verify(signaturePubKey, signedWitness.getWitnessHash(), signedWitness.getSignature());
            return true;
        } catch (CryptoException e) {
            log.warn("verifySignature signedWitness failed. signedWitness={}", signedWitness);
            log.warn("Caused by ", e);
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
    public Set<SignedWitness> getSignedWitnessSetByOwnerPubKey(byte[] ownerPubKey, Stack<P2PDataStorage.ByteArray> excluded) {
        return signedWitnessMap.values().stream()
                .filter(e -> Arrays.equals(e.getWitnessOwnerPubKey(), ownerPubKey))
                .filter(e -> !excluded.contains(new P2PDataStorage.ByteArray(e.getSignerPubKey())))
                .collect(Collectors.toSet());
    }

    /**
     * Checks whether the accountAgeWitness has a valid signature from a peer/arbitrator.
     * @param accountAgeWitness
     * @return true if accountAgeWitness is valid, false otherwise.
     */
    public boolean isValidAccountAgeWitness(AccountAgeWitness accountAgeWitness) {
        Stack<P2PDataStorage.ByteArray> excludedPubKeys = new Stack<>();
        long now = new Date().getTime();
        Set<SignedWitness> signedWitnessSet = getSignedWitnessSet(accountAgeWitness);
        for (SignedWitness signedWitness : signedWitnessSet) {
            if (isValidSignedWitnessInternal(signedWitness, now, excludedPubKeys)) {
                return true;
            }
        }
        // If we have not returned in the loops or they have been empty we have not found a valid signer.
        return false;
    }

    /**
     * Helper to isValidAccountAgeWitness(accountAgeWitness)
     * @param signedWitness the signedWitness to validate
     * @param childSignedWitnessDateMillis the date the child SignedWitness was signed or current time if it is a leave.
     * @param excludedPubKeys stack to prevent recursive loops
     * @return true if signedWitness is valid, false otherwise.
     */
    private boolean isValidSignedWitnessInternal(SignedWitness signedWitness, long childSignedWitnessDateMillis, Stack<P2PDataStorage.ByteArray> excludedPubKeys) {
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
                if (isValidSignedWitnessInternal(signerSignedWitness, signedWitness.getDate(), excludedPubKeys)) {
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
        long childSignedWitnessDateMinusChargebackPeriodMillis = Instant.ofEpochMilli(childSignedWitnessDateMillis).minus(CHARGEBACK_SAFETY_DAYS, ChronoUnit.DAYS).toEpochMilli();
        long signedWitnessDateMillis = signedWitness.getDate();
        return signedWitnessDateMillis <= childSignedWitnessDateMinusChargebackPeriodMillis;
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Private
    ///////////////////////////////////////////////////////////////////////////////////////////

    @VisibleForTesting
    void addToMap(SignedWitness signedWitness) {
        signedWitnessMap.putIfAbsent(signedWitness.getHashAsByteArray(), signedWitness);
    }

    private void publishSignedWitness(SignedWitness signedWitness) {
        if (!signedWitnessMap.containsKey(signedWitness.getHashAsByteArray())) {
            p2PService.addPersistableNetworkPayload(signedWitness, false);
        }
    }

    // Arbitrator signing
    public List<BuyerDataItem> getBuyerPaymentAccounts(long safeDate) {
        return disputeManager.getDisputesAsObservableList().stream()
                .filter(this::hasChargebackRisk)
                .filter(this::isBuyerWinner)
                .map(this::getBuyerData)
                .filter(Objects::nonNull)
                .filter(buyerDataItem -> buyerDataItem.getAccountAgeWitness().getDate() < safeDate)
                .distinct()
                .collect(Collectors.toList());
    }

    private boolean hasChargebackRisk(Dispute dispute) {
        return PaymentMethod.hasChargebackRisk(dispute.getContract().getPaymentMethodId());
    }

    private boolean isBuyerWinner(Dispute dispute) {
        return dispute.getDisputeResultProperty().get().getWinner() == DisputeResult.Winner.BUYER;
    }

    @Nullable
    private BuyerDataItem getBuyerData(Dispute dispute) {
        PubKeyRing buyerPubKeyRing = dispute.getContract().getBuyerPubKeyRing();
        PaymentAccountPayload buyerPaymentAccountPaload = dispute.getContract().getBuyerPaymentAccountPayload();
        Optional<AccountAgeWitness> optionalWitness = accountAgeWitnessService
                .findWitness(buyerPaymentAccountPaload, buyerPubKeyRing);
        return optionalWitness.map(witness -> new BuyerDataItem(
                buyerPaymentAccountPaload,
                witness,
                dispute.getContract().getTradeAmount(),
                dispute.getContract().getSellerPubKeyRing().getSignaturePubKey()))
                .orElse(null);
    }

}
