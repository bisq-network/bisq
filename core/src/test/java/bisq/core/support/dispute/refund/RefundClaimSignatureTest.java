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

import bisq.core.support.SupportType;
import bisq.core.support.dispute.Dispute;
import bisq.core.trade.model.bisq_v1.Contract;

import bisq.common.crypto.Encryption;
import bisq.common.crypto.PubKeyRing;
import bisq.common.crypto.Sig;
import bisq.common.util.Hex;

import com.google.protobuf.ByteString;

import org.bitcoinj.core.ECKey;
import org.bitcoinj.crypto.DeterministicKey;
import org.bitcoinj.crypto.HDKeyDerivation;
import org.bitcoinj.crypto.KeyCrypterScrypt;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Date;
import java.util.function.Consumer;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RefundClaimSignatureTest {
    private static final long OPENING_DATE = 1_700_000_000_000L;
    private static final String DEPOSIT_TX_ID = "ab".repeat(32);
    private static final String DELAYED_PAYOUT_TX_ID = "cd".repeat(32);
    private static final ECKey BUYER_ESCROW_KEY = new ECKey();
    private static final ECKey SELLER_ESCROW_KEY = new ECKey();
    private static final PubKeyRing BUYER_PUB_KEY_RING = pubKeyRing();
    private static final PubKeyRing SELLER_PUB_KEY_RING = pubKeyRing();
    private static final PubKeyRing AGENT_PUB_KEY_RING = pubKeyRing();

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void realSignatureSurvivesDisputeRoundTripForBothRolesAndMakerArrangements(boolean buyerIsMaker)
            throws Exception {
        RefundClaimTestData data = new RefundClaimTestData(AGENT_PUB_KEY_RING, buyerIsMaker);
        for (boolean buyer : new boolean[]{true, false}) {
            Dispute original = data.signed(buyer, OPENING_DATE);
            Dispute restored = RefundClaimTestData.restore(original.toProtoMessage());
            assertEquals(original.getRefundClaimSignature(), restored.getRefundClaimSignature());
            assertDoesNotThrow(() -> RefundClaimSignature.verifyDisputeOpener(restored));
            assertEquals(buyer ? RefundClaimSignature.Claimant.BUYER : RefundClaimSignature.Claimant.SELLER,
                    RefundClaimSignature.verifyClaimant(restored));
        }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("signedFieldChanges")
    void changingAnySignedFieldInvalidatesPersistedProof(String field, Consumer<protobuf.Dispute.Builder> change)
            throws Exception {
        RefundClaimTestData data = new RefundClaimTestData(AGENT_PUB_KEY_RING, true);
        Dispute original = data.signed(true, OPENING_DATE);
        protobuf.Dispute.Builder builder = original.toProtoMessage().toBuilder();
        change.accept(builder);
        Dispute changed = RefundClaimTestData.restore(builder.build());
        assertThrows(IllegalArgumentException.class, () -> RefundClaimSignature.verifyClaimant(changed), field);
    }

    private static Stream<Arguments> signedFieldChanges() {
        return Stream.of(
                changed("trade identifier", b -> b.setTradeId("different")),
                changed("contract hash", b -> b.setContractHash(ByteString.copyFrom(new byte[32]))),
                changed("deposit identifier", b -> b.setDepositTxId("ee".repeat(32))),
                changed("delayed payout identifier", b -> b.setDelayedPayoutTxId("ee".repeat(32))),
                changed("agent identity", b -> b.setAgentPubKeyRing(BUYER_PUB_KEY_RING.toProtoMessage())),
                changed("selection height", b -> b.setBurningManSelectionHeight(2)),
                changed("trade fee", b -> b.setTradeTxFee(1_001)),
                changed("donation address", b -> b.setDonationAddressOfDelayedPayoutTx("changed")),
                changed("signed opening date", b -> b.setRefundClaimOpeningDate(OPENING_DATE + 1)),
                changed("maker identity signature key", b -> b.getContractBuilder().getMakerPubKeyRingBuilder()
                        .setSignaturePubKeyBytes(BUYER_PUB_KEY_RING.toProtoMessage().getSignaturePubKeyBytes())),
                changed("taker identity signature key", b -> b.getContractBuilder().getTakerPubKeyRingBuilder()
                        .setSignaturePubKeyBytes(SELLER_PUB_KEY_RING.toProtoMessage().getSignaturePubKeyBytes())),
                changed("maker encryption key", b -> b.getContractBuilder().getMakerPubKeyRingBuilder()
                        .setEncryptionPubKeyBytes(BUYER_PUB_KEY_RING.toProtoMessage().getEncryptionPubKeyBytes())),
                changed("taker encryption key", b -> b.getContractBuilder().getTakerPubKeyRingBuilder()
                        .setEncryptionPubKeyBytes(SELLER_PUB_KEY_RING.toProtoMessage().getEncryptionPubKeyBytes())),
                changed("maker escrow key", b -> b.getContractBuilder()
                        .setMakerMultiSigPubKey(ByteString.copyFrom(BUYER_ESCROW_KEY.getPubKey()))),
                changed("taker escrow key", b -> b.getContractBuilder()
                        .setTakerMultiSigPubKey(ByteString.copyFrom(SELLER_ESCROW_KEY.getPubKey()))));
    }

    private static Arguments changed(String name, Consumer<protobuf.Dispute.Builder> change) {
        return Arguments.of(name, change);
    }

    @Test
    void encryptedEscrowKeyRequiresCorrectDecryptionKey() {
        Fixture fixture = fixture(BUYER_ESCROW_KEY, SELLER_ESCROW_KEY, true, OPENING_DATE);
        KeyCrypterScrypt crypter = new KeyCrypterScrypt();
        var aesKey = crypter.deriveKey("test password");
        ECKey encrypted = BUYER_ESCROW_KEY.encrypt(crypter, aesKey);
        fixture.signature().set(RefundClaimSignature.sign(fixture.dispute(), encrypted, aesKey));
        assertDoesNotThrow(() -> RefundClaimSignature.verifyDisputeOpener(fixture.dispute()));
        assertThrows(RuntimeException.class, () -> RefundClaimSignature.sign(fixture.dispute(), encrypted, null));
        assertThrows(RuntimeException.class, () -> RefundClaimSignature.sign(fixture.dispute(), encrypted,
                crypter.deriveKey("wrong password")));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "not-base64", "AA=="})
    void malformedSignaturesFailBothAdmissionAndClaimantResolution(String signature) {
        Fixture fixture = fixture(BUYER_ESCROW_KEY, SELLER_ESCROW_KEY, true, OPENING_DATE);
        fixture.signature().set(signature);
        assertThrows(IllegalArgumentException.class, () -> RefundClaimSignature.verifyDisputeOpener(fixture.dispute()));
        assertThrows(IllegalArgumentException.class, () -> RefundClaimSignature.verifyClaimant(fixture.dispute()));
    }

    @Test
    void oversizedSignatureAndMissingSignedDateAreRejected() {
        Fixture fixture = fixture(BUYER_ESCROW_KEY, SELLER_ESCROW_KEY, true, OPENING_DATE);
        fixture.signature().set("A".repeat(201));
        assertThrows(IllegalArgumentException.class, () -> RefundClaimSignature.verifyClaimant(fixture.dispute()));
        fixture.sign(RefundClaimSignature.Claimant.BUYER, BUYER_ESCROW_KEY);
        when(fixture.dispute().getRefundClaimOpeningDate()).thenReturn(0L);
        assertThrows(IllegalArgumentException.class, () -> RefundClaimSignature.verifyClaimant(fixture.dispute()));
    }

    @Test
    void declaredIdentityAndMakerRoleMustMatchContract() throws Exception {
        RefundClaimTestData data = new RefundClaimTestData(AGENT_PUB_KEY_RING, true);
        Dispute signed = data.signed(true, OPENING_DATE);
        for (protobuf.Dispute changed : new protobuf.Dispute[]{
                signed.toProtoMessage().toBuilder().setDisputeOpenerIsMaker(false).build(),
                signed.toProtoMessage().toBuilder().setDisputeOpenerIsBuyer(false).build(),
                signed.toProtoMessage().toBuilder().setTraderPubKeyRing(data.seller.toProtoMessage()).build()}) {
            assertThrows(IllegalArgumentException.class,
                    () -> RefundClaimSignature.verifyDisputeOpener(RefundClaimTestData.restore(changed)));
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void canonicalMessageMatchesVersionOneVector(boolean buyer) throws Exception {
        Fixture fixture = fixture(
                ECKey.fromPublicOnly(Hex.decode("0279be667ef9dcbbac55a06295ce870b07029bfcdb2dce28d959f2815b16f81798")),
                ECKey.fromPublicOnly(Hex.decode("02c6047f9441ed7d6d3045406e95c07cd85c778e4b8cef3ca7abac09b95c709ee5")),
                buyer, OPENING_DATE);
        // Controlled canonical key-ring bytes isolate this signed-schema vector from key generation.
        for (int index = 1; index <= 3; index++) {
            PubKeyRing ring = mock(PubKeyRing.class);
            when(ring.encodeCanonical()).thenReturn(new byte[]{10, 1, (byte) index, 18, 1, (byte) (index + 3)});
            if (index == 1) {
                when(fixture.dispute().getContract().getMakerPubKeyRing()).thenReturn(ring);
            } else if (index == 2) {
                when(fixture.dispute().getContract().getTakerPubKeyRing()).thenReturn(ring);
            } else {
                when(fixture.dispute().getAgentPubKeyRing()).thenReturn(ring);
            }
        }
        when(fixture.dispute().getDonationAddressOfDelayedPayoutTx()).thenReturn(buyer ? "legacy-address" : null);
        String resource = buyer ? "refund-claim-v1-buyer.txt" : "refund-claim-v1-seller.txt";
        try (var stream = getClass().getResourceAsStream(resource)) {
            assertEquals(new String(java.util.Objects.requireNonNull(stream).readAllBytes(),
                            StandardCharsets.UTF_8).trim(),
                    RefundClaimSignature.getMessage(fixture.dispute(),
                            buyer ? RefundClaimSignature.Claimant.BUYER : RefundClaimSignature.Claimant.SELLER));
        }
    }

    @Test
    void buyerClaimVerifiesForDeclaredBuyer() {
        Fixture fixture = fixture(BUYER_ESCROW_KEY, SELLER_ESCROW_KEY, true, OPENING_DATE);
        fixture.sign(RefundClaimSignature.Claimant.BUYER, BUYER_ESCROW_KEY);

        assertDoesNotThrow(() -> RefundClaimSignature.verifyDisputeOpener(fixture.dispute()));
        assertEquals(RefundClaimSignature.Claimant.BUYER,
                RefundClaimSignature.verifyClaimant(fixture.dispute()));
    }

    @Test
    void declaredBuyerRejectsSignatureBySellerEscrowKey() {
        Fixture fixture = fixture(BUYER_ESCROW_KEY, SELLER_ESCROW_KEY, true, OPENING_DATE);
        fixture.sign(RefundClaimSignature.Claimant.SELLER, SELLER_ESCROW_KEY);

        assertThrows(IllegalArgumentException.class,
                () -> RefundClaimSignature.verifyDisputeOpener(fixture.dispute()));
    }

    @Test
    void declaredBuyerRejectsSignatureByUnrelatedKey() {
        Fixture fixture = fixture(BUYER_ESCROW_KEY, SELLER_ESCROW_KEY, true, OPENING_DATE);
        fixture.sign(RefundClaimSignature.Claimant.BUYER, new ECKey());

        assertThrows(IllegalArgumentException.class,
                () -> RefundClaimSignature.verifyDisputeOpener(fixture.dispute()));
    }

    @Test
    void claimRejectsChangedContractHash() {
        Fixture fixture = fixture(BUYER_ESCROW_KEY, SELLER_ESCROW_KEY, true, OPENING_DATE);
        fixture.sign(RefundClaimSignature.Claimant.BUYER, BUYER_ESCROW_KEY);
        byte[] changedContractHash = new byte[32];
        Arrays.fill(changedContractHash, (byte) 2);
        when(fixture.dispute().getContractHash()).thenReturn(changedContractHash);

        assertThrows(IllegalArgumentException.class,
                () -> RefundClaimSignature.verifyDisputeOpener(fixture.dispute()));
    }

    @Test
    void claimRejectsChangedDelayedPayoutTransactionId() {
        Fixture fixture = fixture(BUYER_ESCROW_KEY, SELLER_ESCROW_KEY, true, OPENING_DATE);
        fixture.sign(RefundClaimSignature.Claimant.BUYER, BUYER_ESCROW_KEY);
        when(fixture.dispute().getDelayedPayoutTxId()).thenReturn("ef".repeat(32));

        assertThrows(IllegalArgumentException.class,
                () -> RefundClaimSignature.verifyDisputeOpener(fixture.dispute()));
    }

    @Test
    void claimRejectsMissingSignature() {
        Fixture fixture = fixture(BUYER_ESCROW_KEY, SELLER_ESCROW_KEY, true, OPENING_DATE);

        assertThrows(IllegalArgumentException.class,
                () -> RefundClaimSignature.verifyDisputeOpener(fixture.dispute()));
    }

    @Test
    void disputeOpenerRejectsClaimSignedForAnotherOpeningDate() {
        Fixture fixture = fixture(BUYER_ESCROW_KEY, SELLER_ESCROW_KEY, true, OPENING_DATE);
        fixture.sign(RefundClaimSignature.Claimant.BUYER, BUYER_ESCROW_KEY);
        when(fixture.dispute().getOpeningDate()).thenReturn(new Date(OPENING_DATE + 1));

        assertThrows(IllegalArgumentException.class,
                () -> RefundClaimSignature.verifyDisputeOpener(fixture.dispute()));
    }

    @Test
    void mirroredRowCanIdentifyOriginalClaimantWithoutUsingReversedFlags() {
        Fixture fixture = fixture(BUYER_ESCROW_KEY, SELLER_ESCROW_KEY, true, OPENING_DATE);
        fixture.sign(RefundClaimSignature.Claimant.BUYER, BUYER_ESCROW_KEY);
        when(fixture.dispute().isDisputeOpenerIsBuyer()).thenReturn(false);
        when(fixture.dispute().isDisputeOpenerIsMaker()).thenReturn(false);
        when(fixture.dispute().getTraderPubKeyRing()).thenReturn(SELLER_PUB_KEY_RING);

        assertEquals(RefundClaimSignature.Claimant.BUYER,
                RefundClaimSignature.verifyClaimant(fixture.dispute()));
    }

    @Test
    void claimSubjectIgnoresRoleAndOpeningDateButNotValidationInputs() {
        Fixture buyer = fixture(BUYER_ESCROW_KEY, SELLER_ESCROW_KEY, true, OPENING_DATE);
        Fixture seller = fixture(BUYER_ESCROW_KEY, SELLER_ESCROW_KEY, false, OPENING_DATE + 1);

        assertTrue(RefundClaimSignature.hasSameClaimSubject(buyer.dispute(), seller.dispute()));

        when(seller.dispute().getTradeTxFee()).thenReturn(2_001L);
        assertFalse(RefundClaimSignature.hasSameClaimSubject(buyer.dispute(), seller.dispute()));
    }

    @Test
    void signUsesRoleSpecificEscrowKey() {
        byte[] seed = new byte[32];
        Arrays.fill(seed, (byte) 7);
        DeterministicKey buyerKey = HDKeyDerivation.createMasterPrivateKey(seed);
        Fixture fixture = fixture(buyerKey, SELLER_ESCROW_KEY, true, OPENING_DATE);

        fixture.signature().set(RefundClaimSignature.sign(fixture.dispute(), buyerKey, null));

        assertDoesNotThrow(() -> RefundClaimSignature.verifyDisputeOpener(fixture.dispute()));
    }

    private static Fixture fixture(ECKey buyerEscrowKey,
                                   ECKey sellerEscrowKey,
                                   boolean openerIsBuyer,
                                   long openingDate) {
        Contract contract = mock(Contract.class);
        when(contract.getMakerPubKeyRing()).thenReturn(BUYER_PUB_KEY_RING);
        when(contract.getTakerPubKeyRing()).thenReturn(SELLER_PUB_KEY_RING);
        when(contract.getBuyerPubKeyRing()).thenReturn(BUYER_PUB_KEY_RING);
        when(contract.getSellerPubKeyRing()).thenReturn(SELLER_PUB_KEY_RING);
        when(contract.getMakerMultiSigPubKey()).thenReturn(buyerEscrowKey.getPubKey());
        when(contract.getTakerMultiSigPubKey()).thenReturn(sellerEscrowKey.getPubKey());
        when(contract.getBuyerMultiSigPubKey()).thenReturn(buyerEscrowKey.getPubKey());
        when(contract.getSellerMultiSigPubKey()).thenReturn(sellerEscrowKey.getPubKey());
        when(contract.isBuyerMakerAndSellerTaker()).thenReturn(true);

        Dispute dispute = mock(Dispute.class);
        when(dispute.getSupportType()).thenReturn(SupportType.REFUND);
        when(dispute.getContract()).thenReturn(contract);
        when(dispute.getTradeId()).thenReturn("trade-id");
        when(dispute.getContractHash()).thenReturn(new byte[32]);
        when(dispute.getDepositTxId()).thenReturn(DEPOSIT_TX_ID);
        when(dispute.getDelayedPayoutTxId()).thenReturn(DELAYED_PAYOUT_TX_ID);
        when(dispute.getAgentPubKeyRing()).thenReturn(AGENT_PUB_KEY_RING);
        when(dispute.getBurningManSelectionHeight()).thenReturn(800_000);
        when(dispute.getTradeTxFee()).thenReturn(2_000L);
        when(dispute.getRefundClaimOpeningDate()).thenReturn(openingDate);
        when(dispute.getOpeningDate()).thenReturn(new Date(openingDate));
        when(dispute.isDisputeOpenerIsBuyer()).thenReturn(openerIsBuyer);
        when(dispute.isDisputeOpenerIsMaker()).thenReturn(openerIsBuyer);
        when(dispute.getTraderPubKeyRing()).thenReturn(openerIsBuyer ?
                BUYER_PUB_KEY_RING : SELLER_PUB_KEY_RING);

        AtomicReference<String> signature = new AtomicReference<>();
        when(dispute.getRefundClaimSignature()).thenAnswer(invocation -> signature.get());
        return new Fixture(dispute, signature);
    }

    private static PubKeyRing pubKeyRing() {
        return new PubKeyRing(Sig.generateKeyPair().getPublic(), Encryption.generateKeyPair().getPublic());
    }

    private record Fixture(Dispute dispute, AtomicReference<String> signature) {
        private void sign(RefundClaimSignature.Claimant claimant, ECKey key) {
            signature.set(key.signMessage(RefundClaimSignature.getMessage(dispute, claimant)));
        }
    }
}
