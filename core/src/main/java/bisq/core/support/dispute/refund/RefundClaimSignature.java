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
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public License
 * for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Bisq. If not, see <http://www.gnu.org/licenses/>.
 */

package bisq.core.support.dispute.refund;

import bisq.core.crypto.LowRSigningKey;
import bisq.core.support.SupportType;
import bisq.core.support.dispute.Dispute;
import bisq.core.trade.model.bisq_v1.Contract;

import bisq.common.crypto.Hash;
import bisq.common.crypto.PubKeyRing;
import bisq.common.encoding.canonical.Canonical;
import bisq.common.encoding.canonical.CanonicalEncoder;
import bisq.common.encoding.canonical.CanonicalSchema;
import bisq.common.util.Hex;

import org.bitcoinj.core.ECKey;

import org.bouncycastle.crypto.params.KeyParameter;

import java.security.SignatureException;

import java.util.Arrays;

import javax.annotation.Nullable;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Proves that the refund claimant controls one of the escrow private keys committed by deposit output 0.
 */
public final class RefundClaimSignature {
    static final String DOMAIN = "bisq-refund-claim-v1";
    private static final int MAX_SIGNATURE_LENGTH = 200;

    public enum Claimant {
        BUYER,
        SELLER
    }

    private RefundClaimSignature() {
    }

    public static String sign(Dispute dispute, ECKey keyPair, @Nullable KeyParameter aesKey) {
        return sign(dispute, keyPair, aesKey, checkRefundDispute(dispute).getRefundClaimOpeningDate());
    }

    static String sign(Dispute dispute, ECKey keyPair, @Nullable KeyParameter aesKey, long claimOpeningDate) {
        Dispute checkedDispute = checkRefundDispute(dispute);
        Claimant claimant = getDeclaredClaimant(checkedDispute);
        validateDeclaredOpener(checkedDispute, claimant);

        byte[] claimantPubKey = getClaimantMultiSigPubKey(checkedDispute.getContract(), claimant);
        checkNotNull(keyPair, "Escrow key pair not found for refund claim");
        checkArgument(Arrays.equals(keyPair.getPubKey(), claimantPubKey),
                "Wallet escrow key does not match the refund claimant key");

        ECKey signingKey = checkNotNull(LowRSigningKey.from(keyPair),
                "Refund claim signing key must not be null");
        String message = getMessage(checkedDispute, claimant, claimOpeningDate);
        String signature;
        if (aesKey != null) {
            signature = signingKey.signMessage(message, aesKey);
        } else {
            signature = signingKey.signMessage(message);
        }

        verify(checkedDispute, claimant, signature, claimOpeningDate);
        return signature;
    }

    public static byte[] getOpenerMultiSigPubKey(Dispute dispute) {
        Dispute checkedDispute = checkRefundDispute(dispute);
        Claimant claimant = getDeclaredClaimant(checkedDispute);
        validateDeclaredOpener(checkedDispute, claimant);
        return getClaimantMultiSigPubKey(checkedDispute.getContract(), claimant);
    }

    /**
     * Intake verification is role-specific: the other escrow key cannot authenticate the declared opener.
     */
    public static void verifyDisputeOpener(Dispute dispute) {
        Dispute checkedDispute = checkRefundDispute(dispute);
        checkArgument(checkedDispute.getRefundClaimOpeningDate() == checkedDispute.getOpeningDate().getTime(),
                "Refund claim opening date must match the dispute opening date");
        Claimant claimant = getDeclaredClaimant(checkedDispute);
        validateDeclaredOpener(checkedDispute, claimant);
        verify(checkedDispute, claimant, getSignature(checkedDispute));
    }

    /**
     * Close-time verification cannot rely on a dispute row's opener flags because the agent-created peer row reverses
     * them. The signed role and the corresponding escrow key identify the original claimant instead.
     */
    public static Claimant verifyClaimant(Dispute dispute) {
        Dispute checkedDispute = checkRefundDispute(dispute);
        String signature = getSignature(checkedDispute);
        for (Claimant claimant : Claimant.values()) {
            try {
                verify(checkedDispute, claimant, signature);
                return claimant;
            } catch (IllegalArgumentException ignored) {
                // Try the other contract-bound escrow key before rejecting the proof.
            }
        }
        throw new IllegalArgumentException(
                "Refund claim signature does not match either contract escrow key");
    }

    /**
     * Two dispute rows can contribute claimant proofs only when every claim-bound field except role and opening date
     * is identical. Each candidate's own signature still authenticates its role and opening date.
     */
    public static boolean hasSameClaimSubject(Dispute first, Dispute second) {
        return Arrays.equals(createSubject(checkRefundDispute(first)).encodeCanonical(),
                createSubject(checkRefundDispute(second)).encodeCanonical());
    }

    public static String getClaimSubjectHash(Dispute dispute) {
        return Hex.encode(Hash.getSha256Hash(createSubject(checkRefundDispute(dispute)).encodeCanonical()));
    }

    static String getMessage(Dispute dispute, Claimant claimant) {
        return getMessage(dispute, claimant, checkRefundDispute(dispute).getRefundClaimOpeningDate());
    }

    private static String getMessage(Dispute dispute, Claimant claimant, long claimOpeningDate) {
        Dispute checkedDispute = checkRefundDispute(dispute);
        checkArgument(claimOpeningDate > 0, "Refund claim opening date must be positive");
        RefundClaim claim = new RefundClaim(createSubject(checkedDispute),
                claimant == Claimant.BUYER,
                claimOpeningDate);
        return Hex.encode(claim.encodeCanonical());
    }

    private static void verify(Dispute dispute, Claimant claimant, String signature) {
        verify(dispute, claimant, signature, dispute.getRefundClaimOpeningDate());
    }

    private static void verify(Dispute dispute, Claimant claimant, String signature, long claimOpeningDate) {
        byte[] claimantPubKey = getClaimantMultiSigPubKey(dispute.getContract(), claimant);
        try {
            ECKey.fromPublicOnly(claimantPubKey).verifyMessage(getMessage(dispute, claimant, claimOpeningDate), signature);
        } catch (SignatureException | RuntimeException exception) {
            throw new IllegalArgumentException(
                    "Refund claim signature does not match the declared " + claimant.name().toLowerCase() +
                            " escrow key",
                    exception);
        }
    }

    private static String getSignature(Dispute dispute) {
        String signature = dispute.getRefundClaimSignature();
        checkArgument(signature != null, "Refund dispute must carry an escrow-key claim signature");
        checkArgument(!signature.isBlank(), "Refund claim signature must not be blank");
        checkArgument(signature.length() <= MAX_SIGNATURE_LENGTH, "Refund claim signature is too large");
        return signature;
    }

    private static Dispute checkRefundDispute(Dispute dispute) {
        checkArgument(dispute != null, "dispute must not be null");
        checkArgument(dispute.getSupportType() == SupportType.REFUND,
                "Escrow-key claim signatures apply only to refund disputes");
        checkArgument(dispute.getContract() != null, "Refund dispute contract must not be null");
        return dispute;
    }

    private static Claimant getDeclaredClaimant(Dispute dispute) {
        return dispute.isDisputeOpenerIsBuyer() ? Claimant.BUYER : Claimant.SELLER;
    }

    private static void validateDeclaredOpener(Dispute dispute, Claimant claimant) {
        Contract contract = dispute.getContract();
        PubKeyRing expectedPubKeyRing = claimant == Claimant.BUYER ?
                contract.getBuyerPubKeyRing() :
                contract.getSellerPubKeyRing();
        checkArgument(expectedPubKeyRing != null,
                "Refund contract must contain the claimant identity key ring");
        checkArgument(expectedPubKeyRing.equals(dispute.getTraderPubKeyRing()),
                "Refund claimant identity does not match the declared role");

        boolean expectedIsMaker = claimant == Claimant.BUYER ?
                contract.isBuyerMakerAndSellerTaker() :
                !contract.isBuyerMakerAndSellerTaker();
        checkArgument(dispute.isDisputeOpenerIsMaker() == expectedIsMaker,
                "Refund claimant maker role does not match the contract");
    }

    private static byte[] getClaimantMultiSigPubKey(Contract contract, Claimant claimant) {
        byte[] pubKey = claimant == Claimant.BUYER ?
                contract.getBuyerMultiSigPubKey() :
                contract.getSellerMultiSigPubKey();
        checkArgument(pubKey != null && pubKey.length > 0,
                "Refund claimant escrow public key must not be empty");
        return pubKey;
    }

    private static RefundClaimSubject createSubject(Dispute dispute) {
        Contract contract = dispute.getContract();
        return new RefundClaimSubject(
                DOMAIN,
                requireValue(dispute.getTradeId(), "Refund trade ID must not be null"),
                requireBytes(dispute.getContractHash(), "Refund contract hash must not be empty"),
                hashPubKeyRing(contract.getMakerPubKeyRing(), "maker"),
                hashPubKeyRing(contract.getTakerPubKeyRing(), "taker"),
                requireBytes(contract.getMakerMultiSigPubKey(), "Maker escrow public key must not be empty"),
                requireBytes(contract.getTakerMultiSigPubKey(), "Taker escrow public key must not be empty"),
                requireValue(dispute.getDepositTxId(), "Refund deposit transaction ID must not be null"),
                requireValue(dispute.getDelayedPayoutTxId(),
                        "Refund delayed payout transaction ID must not be null"),
                hashPubKeyRing(dispute.getAgentPubKeyRing(), "refund agent"),
                dispute.getBurningManSelectionHeight(),
                dispute.getTradeTxFee(),
                dispute.getDonationAddressOfDelayedPayoutTx());
    }

    private static byte[] hashPubKeyRing(PubKeyRing pubKeyRing, String label) {
        checkArgument(pubKeyRing != null, "%s pubKeyRing must not be null", label);
        return Hash.getSha256Hash(pubKeyRing.encodeCanonical());
    }

    private static String requireValue(String value, String message) {
        checkArgument(value != null, message);
        return value;
    }

    private static byte[] requireBytes(byte[] value, String message) {
        checkArgument(value != null && value.length > 0, message);
        return value;
    }

    private record RefundClaimSubject(String domain,
                                      String tradeId,
                                      byte[] contractHash,
                                      byte[] makerPubKeyRingHash,
                                      byte[] takerPubKeyRingHash,
                                      byte[] makerMultiSigPubKey,
                                      byte[] takerMultiSigPubKey,
                                      String depositTxId,
                                      String delayedPayoutTxId,
                                      byte[] refundAgentPubKeyRingHash,
                                      int burningManSelectionHeight,
                                      long tradeTxFee,
                                      String donationAddress) implements Canonical {
        private static final CanonicalSchema<RefundClaimSubject> SCHEMA =
                CanonicalSchema.<RefundClaimSubject>newBuilder()
                        .string(1, RefundClaimSubject::domain)
                        .string(2, RefundClaimSubject::tradeId)
                        .bytes(3, RefundClaimSubject::contractHash)
                        .bytes(4, RefundClaimSubject::makerPubKeyRingHash)
                        .bytes(5, RefundClaimSubject::takerPubKeyRingHash)
                        .bytes(6, RefundClaimSubject::makerMultiSigPubKey)
                        .bytes(7, RefundClaimSubject::takerMultiSigPubKey)
                        .string(8, RefundClaimSubject::depositTxId)
                        .string(9, RefundClaimSubject::delayedPayoutTxId)
                        .bytes(10, RefundClaimSubject::refundAgentPubKeyRingHash)
                        .int32(11, RefundClaimSubject::burningManSelectionHeight)
                        .int64(12, RefundClaimSubject::tradeTxFee)
                        .string(13, RefundClaimSubject::donationAddress)
                        .build();

        @Override
        public byte[] encodeCanonical(CanonicalEncoder canonicalEncoder) {
            return canonicalEncoder.encode(this, SCHEMA);
        }
    }

    private record RefundClaim(RefundClaimSubject subject,
                               boolean claimantIsBuyer,
                               long openingDate) implements Canonical {
        private static final CanonicalSchema<RefundClaim> SCHEMA = CanonicalSchema.<RefundClaim>newBuilder()
                .compose(1, RefundClaim::subject, RefundClaimSubject.SCHEMA)
                .bool(2, RefundClaim::claimantIsBuyer)
                .int64(3, RefundClaim::openingDate)
                .build();

        @Override
        public byte[] encodeCanonical(CanonicalEncoder canonicalEncoder) {
            return canonicalEncoder.encode(this, SCHEMA);
        }
    }
}
