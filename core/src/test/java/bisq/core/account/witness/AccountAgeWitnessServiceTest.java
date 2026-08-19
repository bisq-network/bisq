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

package bisq.core.account.witness;

import bisq.core.account.sign.SignedWitness;
import bisq.core.account.sign.SignedWitnessService;
import bisq.core.crypto.LowRSigningKey;
import bisq.core.filter.FilterPolicyService;
import bisq.core.locale.CountryUtil;
import bisq.core.offer.bisq_v1.OfferPayload;
import bisq.core.payment.ChargeBackRisk;
import bisq.core.payment.payload.PaymentAccountPayload;
import bisq.core.payment.payload.PaymentMethod;
import bisq.core.payment.payload.SepaAccountPayload;
import bisq.core.support.SupportType;
import bisq.core.support.dispute.Dispute;
import bisq.core.support.dispute.DisputeResult;
import bisq.core.support.dispute.arbitration.TraderDataItem;
import bisq.core.support.dispute.arbitration.arbitrator.ArbitratorManager;
import bisq.core.trade.model.bisq_v1.Contract;
import bisq.core.trade.model.bisq_v1.Trade;
import bisq.core.trade.protocol.bisq_v1.model.ProcessModel;

import bisq.network.p2p.P2PService;
import bisq.network.p2p.storage.persistence.AppendOnlyDataStoreService;

import bisq.common.crypto.CryptoException;
import bisq.common.crypto.Hash;
import bisq.common.crypto.KeyRing;
import bisq.common.crypto.KeyStorage;
import bisq.common.crypto.PubKeyRing;
import bisq.common.crypto.Sig;
import bisq.common.util.Utilities;

import org.bitcoinj.core.Coin;
import org.bitcoinj.core.ECKey;

import java.security.KeyPair;
import java.security.PublicKey;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import java.nio.charset.StandardCharsets;

import java.io.File;
import java.io.IOException;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import static bisq.core.payment.payload.PaymentMethod.getPaymentMethod;
import static bisq.core.support.dispute.DisputeResult.PayoutSuggestion.UNKNOWN;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;


// Restricted default Java security policy on Travis does not allow long keys, so test fails.
// Using Utilities.removeCryptographyRestrictions(); did not work.
//@Ignore
public class AccountAgeWitnessServiceTest {
    private static final long NOW = Instant.parse("2026-08-14T12:00:00Z").toEpochMilli();

    private final Clock clock = Clock.fixed(Instant.ofEpochMilli(NOW), ZoneOffset.UTC);
    private PublicKey publicKey;
    private KeyPair keypair;
    private KeyRing keyRing;
    private SignedWitnessService signedWitnessService;
    private AccountAgeWitnessService service;
    private ChargeBackRisk chargeBackRisk;
    private FilterPolicyService filterPolicyService;
    private File dir1;
    private File dir2;
    private File dir3;

    @BeforeEach
    public void setup() throws IOException {
        KeyRing keyRing = mock(KeyRing.class);
        setupService(keyRing);
        keypair = Sig.generateKeyPair();
        publicKey = keypair.getPublic();
        // Setup temp storage dir
        dir1 = makeDir("temp_tests1");
        dir2 = makeDir("temp_tests1");
        dir3 = makeDir("temp_tests1");
    }

    private void setupService(KeyRing keyRing) {
        this.keyRing = keyRing;
        chargeBackRisk = mock(ChargeBackRisk.class);
        AppendOnlyDataStoreService dataStoreService = mock(AppendOnlyDataStoreService.class);
        P2PService p2pService = mock(P2PService.class);
        ArbitratorManager arbitratorManager = mock(ArbitratorManager.class);
        when(arbitratorManager.isPublicKeyInList(any())).thenReturn(true);
        AppendOnlyDataStoreService appendOnlyDataStoreService = mock(AppendOnlyDataStoreService.class);
        filterPolicyService = mock(FilterPolicyService.class);
        signedWitnessService = new SignedWitnessService(keyRing, p2pService, arbitratorManager, null,
                appendOnlyDataStoreService, filterPolicyService, false, clock);
        service = new AccountAgeWitnessService(keyRing, null, null, signedWitnessService, chargeBackRisk, null, dataStoreService, clock, null, filterPolicyService);
    }

    private File makeDir(String name) throws IOException {
        var dir = File.createTempFile(name, "");
        dir.delete();
        dir.mkdir();
        return dir;
    }

    @Test
    public void accountAgeOwnershipProofResolvesTheStoredWitness() throws Exception {
        byte[] accountInput = new byte[]{1, 2, 3};
        AccountAgeWitnessOwnershipProof proof = createAccountAgeOwnershipProof(accountInput);
        AccountAgeWitness witness = new AccountAgeWitness(proof.getWitnessHash(), 1234L);
        service.addToMap(witness);

        assertEquals(witness, service.verifyAccountAgeWitnessOwnership(proof));
    }

    @Test
    public void accountAgeOwnershipProofRejectsTheProvenOwnerKeyWhenBanned() throws Exception {
        byte[] accountInput = new byte[]{1, 2, 3};
        AccountAgeWitnessOwnershipProof proof = createAccountAgeOwnershipProof(accountInput);
        service.addToMap(new AccountAgeWitness(proof.getWitnessHash(), 1234L));
        when(filterPolicyService.isWitnessSignerPubKeyBanned(
                Utilities.bytesAsHexString(proof.getOwnerPublicKey()))).thenReturn(true);

        assertThrows(IllegalArgumentException.class,
                () -> service.verifyAccountAgeWitnessOwnership(proof));
    }

    private AccountAgeWitnessOwnershipProof createAccountAgeOwnershipProof(byte[] accountInput) throws Exception {
        byte[] ownerPublicKey = Sig.getPublicKeyBytes(publicKey);
        byte[] witnessHash = Hash.getSha256Ripemd160hash(
                Utilities.concatenateByteArrays(accountInput, ownerPublicKey));
        byte[] signature = Sig.sign(keypair.getPrivate(),
                AccountAgeWitnessOwnershipProof.getSignatureMessage(
                        AccountAgeWitnessOwnershipProof.VERSION,
                        "12".repeat(20),
                        witnessHash,
                        accountInput,
                        ownerPublicKey));
        return new AccountAgeWitnessOwnershipProof(
                AccountAgeWitnessOwnershipProof.VERSION,
                "12".repeat(20),
                witnessHash,
                accountInput,
                ownerPublicKey,
                signature);
    }

    @AfterEach
    public void tearDown() {
        // Do teardown stuff
    }

    @Test
    public void testIsTradeDateAfterReleaseDate() {
        Date ageWitnessReleaseDate = new GregorianCalendar(2017, Calendar.OCTOBER, 23).getTime();
        Date tradeDate = new GregorianCalendar(2017, Calendar.NOVEMBER, 1).getTime();
        assertTrue(service.isDateAfterReleaseDate(tradeDate.getTime(), ageWitnessReleaseDate, errorMessage -> {
        }));
        tradeDate = new GregorianCalendar(2017, Calendar.OCTOBER, 23).getTime();
        assertTrue(service.isDateAfterReleaseDate(tradeDate.getTime(), ageWitnessReleaseDate, errorMessage -> {
        }));
        tradeDate = new GregorianCalendar(2017, Calendar.OCTOBER, 22, 0, 0, 1).getTime();
        assertTrue(service.isDateAfterReleaseDate(tradeDate.getTime(), ageWitnessReleaseDate, errorMessage -> {
        }));
        tradeDate = new GregorianCalendar(2017, Calendar.OCTOBER, 22).getTime();
        assertFalse(service.isDateAfterReleaseDate(tradeDate.getTime(), ageWitnessReleaseDate, errorMessage -> {
        }));
        tradeDate = new GregorianCalendar(2017, Calendar.OCTOBER, 21).getTime();
        assertFalse(service.isDateAfterReleaseDate(tradeDate.getTime(), ageWitnessReleaseDate, errorMessage -> {
        }));
    }

    @Test
    public void verifyPeersCurrentDateAcceptsDatesInsideTheTolerance() {
        assertTrue(service.verifyPeersCurrentDate(new Date(NOW), errorMessage -> {
        }));
        assertTrue(service.verifyPeersCurrentDate(new Date(NOW - TimeUnit.DAYS.toMillis(1)), errorMessage -> {
        }));
        assertTrue(service.verifyPeersCurrentDate(new Date(NOW + TimeUnit.DAYS.toMillis(1)), errorMessage -> {
        }));
    }

    @Test
    public void verifyPeersCurrentDateRejectsDatesOutsideTheTolerance() {
        assertFalse(service.verifyPeersCurrentDate(new Date(NOW - TimeUnit.DAYS.toMillis(1) - 1), errorMessage -> {
        }));
        assertFalse(service.verifyPeersCurrentDate(new Date(NOW + TimeUnit.DAYS.toMillis(1) + 1), errorMessage -> {
        }));
        // A difference based check would overflow with those values and accept them
        assertFalse(service.verifyPeersCurrentDate(new Date(Long.MIN_VALUE), errorMessage -> {
        }));
        assertFalse(service.verifyPeersCurrentDate(new Date(NOW + Long.MIN_VALUE), errorMessage -> {
        }));
        assertFalse(service.verifyPeersCurrentDate(new Date(Long.MAX_VALUE), errorMessage -> {
        }));
    }

    @Disabled
    @Test
    public void testVerifySignatureOfNonce() throws CryptoException {
        byte[] nonce = new byte[]{0x01};
        byte[] signature = Sig.sign(keypair.getPrivate(), nonce);
        assertTrue(service.verifySignature(publicKey, nonce, signature, errorMessage -> {
        }));
        assertFalse(service.verifySignature(publicKey, nonce, new byte[]{0x02}, errorMessage -> {
        }));
        assertFalse(service.verifySignature(publicKey, new byte[]{0x03}, signature, errorMessage -> {
        }));
        assertFalse(service.verifySignature(publicKey, new byte[]{0x02}, new byte[]{0x04}, errorMessage -> {
        }));
    }

    @Test
    public void testArbitratorSignWitness() {
        KeyRing buyerKeyRing = new KeyRing(new KeyStorage(dir1));
        KeyRing sellerKeyRing = new KeyRing(new KeyStorage(dir2));

        // Setup dispute for arbitrator to sign both sides
        List<Dispute> disputes = new ArrayList<>();
        PubKeyRing buyerPubKeyRing = buyerKeyRing.getPubKeyRing();
        PubKeyRing sellerPubKeyRing = sellerKeyRing.getPubKeyRing();
        PaymentAccountPayload buyerPaymentAccountPayload = new SepaAccountPayload(PaymentMethod.SEPA_ID, "1", CountryUtil.getAllSepaCountries());
        PaymentAccountPayload sellerPaymentAccountPayload = new SepaAccountPayload(PaymentMethod.SEPA_ID, "2", CountryUtil.getAllSepaCountries());
        AccountAgeWitness buyerAccountAgeWitness = service.getNewWitness(buyerPaymentAccountPayload, buyerPubKeyRing);
        service.addToMap(buyerAccountAgeWitness);
        AccountAgeWitness sellerAccountAgeWitness = service.getNewWitness(sellerPaymentAccountPayload, sellerPubKeyRing);
        service.addToMap(sellerAccountAgeWitness);
        long now = new Date().getTime() + 1000;
        Contract contract = mock(Contract.class);
        disputes.add(new Dispute(new Date().getTime(),
                "trade1",
                0,
                true,
                true,
                buyerPubKeyRing,
                now - 1,
                now - 1,
                contract,
                null,
                null,
                null,
                null,
                null,
                "contractAsJson",
                null,
                null,
                null,
                true,
                SupportType.ARBITRATION));
        disputes.get(0).setIsClosed();
        disputes.get(0).getDisputeResultProperty().set(new DisputeResult(
                "trade1",
                1,
                DisputeResult.Winner.BUYER,
                DisputeResult.Reason.OTHER.ordinal(),
                true,
                true,
                true,
                "summary",
                null,
                null,
                100000,
                0,
                null,
                now - 1,
                false, "", UNKNOWN));

        // Filtermanager says nothing is filtered
        when(filterPolicyService.isNodeAddressBanned(any())).thenReturn(false);
        when(filterPolicyService.isCurrencyBanned(any())).thenReturn(false);
        when(filterPolicyService.isPaymentMethodBanned(any())).thenReturn(false);
        when(filterPolicyService.arePeersPaymentAccountDataBanned(any())).thenReturn(false);
        when(filterPolicyService.isWitnessSignerPubKeyBanned(any())).thenReturn(false);

        when(chargeBackRisk.hasChargebackRisk(any(), any())).thenReturn(true);

        when(contract.getPaymentMethodId()).thenReturn(PaymentMethod.SEPA_ID);
        when(contract.getTradeAmount()).thenReturn(Coin.parseCoin("0.01"));
        when(contract.getBuyerPubKeyRing()).thenReturn(buyerPubKeyRing);
        when(contract.getSellerPubKeyRing()).thenReturn(sellerPubKeyRing);
        when(contract.getBuyerPaymentAccountPayload()).thenReturn(buyerPaymentAccountPayload);
        when(contract.getSellerPaymentAccountPayload()).thenReturn(sellerPaymentAccountPayload);
        when(contract.getOfferPayload()).thenReturn(mock(OfferPayload.class));
        List<TraderDataItem> items = service.getTraderPaymentAccounts(now, getPaymentMethod(PaymentMethod.SEPA_ID), disputes);
        assertEquals(2, items.size());

        // Setup a mocked arbitrator key
        ECKey arbitratorKey = mock(LowRSigningKey.class);
        when(arbitratorKey.signMessage(any())).thenReturn("1");
        when(arbitratorKey.signMessage(any())).thenReturn("2");
        when(arbitratorKey.getPubKey()).thenReturn("1".getBytes());

        // Arbitrator signs both trader accounts
        items.forEach(item -> service.arbitratorSignAccountAgeWitness(
                item.getTradeAmount(),
                item.getAccountAgeWitness(),
                arbitratorKey,
                item.getPeersPubKey()));

        // Check that both accountAgeWitnesses are signed
        SignedWitness foundBuyerSignedWitness = signedWitnessService.getSignedWitnessSetByOwnerPubKey(
                        buyerPubKeyRing.getSignaturePubKeyBytes()).stream()
                .findFirst()
                .orElse(null);
        assert foundBuyerSignedWitness != null;
        assertEquals(Utilities.bytesAsHexString(foundBuyerSignedWitness.getAccountAgeWitnessHash()),
                Utilities.bytesAsHexString(buyerAccountAgeWitness.getHash()));
        SignedWitness foundSellerSignedWitness = signedWitnessService.getSignedWitnessSetByOwnerPubKey(
                        sellerPubKeyRing.getSignaturePubKeyBytes()).stream()
                .findFirst()
                .orElse(null);
        assert foundSellerSignedWitness != null;
        assertEquals(Utilities.bytesAsHexString(foundSellerSignedWitness.getAccountAgeWitnessHash()),
                Utilities.bytesAsHexString(sellerAccountAgeWitness.getHash()));
    }

    // Create a tree of signed witnesses Arb -(SWA)-> aew1 -(SW1)-> aew2 -(SW2)-> aew3
    // Delete SWA signature, none of the account age witnesses are considered signed
    // Sign a dummy AccountAgeWitness using the signerPubkey from SW1; aew2 and aew3 are not considered signed. The
    // lost SignedWitness isn't possible to recover so aew1 is still not signed, but it's pubkey is a signer.
    @Test
    public void testArbitratorSignDummyWitness() throws CryptoException {
        ECKey arbitratorKey = new ECKey();
        // Init 2 user accounts
        var user1KeyRing = new KeyRing(new KeyStorage(dir1));
        var user2KeyRing = new KeyRing(new KeyStorage(dir2));
        var user3KeyRing = new KeyRing(new KeyStorage(dir3));
        var pubKeyRing1 = user1KeyRing.getPubKeyRing();
        var pubKeyRing2 = user2KeyRing.getPubKeyRing();
        var pubKeyRing3 = user3KeyRing.getPubKeyRing();
        var account1 = new SepaAccountPayload(PaymentMethod.SEPA_ID, "1", CountryUtil.getAllSepaCountries());
        var account2 = new SepaAccountPayload(PaymentMethod.SEPA_ID, "2", CountryUtil.getAllSepaCountries());
        var account3 = new SepaAccountPayload(PaymentMethod.SEPA_ID, "3", CountryUtil.getAllSepaCountries());
        var aew1 = service.getNewWitness(account1, pubKeyRing1);
        var aew2 = service.getNewWitness(account2, pubKeyRing2);
        var aew3 = service.getNewWitness(account3, pubKeyRing3);
        // Backdate witness1 70 days
        aew1 = new AccountAgeWitness(aew1.getHash(), new Date().getTime() - TimeUnit.DAYS.toMillis(70));
        aew2 = new AccountAgeWitness(aew2.getHash(), new Date().getTime() - TimeUnit.DAYS.toMillis(35));
        aew3 = new AccountAgeWitness(aew3.getHash(), new Date().getTime() - TimeUnit.DAYS.toMillis(1));
        service.addToMap(aew1);
        service.addToMap(aew2);
        service.addToMap(aew3);

        // Test as user1. It's still possible to sign as arbitrator since the ECKey is passed as an argument.
        setupService(user1KeyRing);

        // Arbitrator signs user1
        service.arbitratorSignAccountAgeWitness(aew1, arbitratorKey, pubKeyRing1.getSignaturePubKeyBytes(),
                aew1.getDate());
        // user1 signs user2
        signAccountAgeWitness(aew2, pubKeyRing2.getSignaturePubKey(), aew2.getDate(), user1KeyRing);
        // user2 signs user3
        signAccountAgeWitness(aew3, pubKeyRing3.getSignaturePubKey(), aew3.getDate(), user2KeyRing);
        signedWitnessService.signAndPublishAccountAgeWitness(SignedWitnessService.MINIMUM_TRADE_AMOUNT_FOR_SIGNING, aew2,
                pubKeyRing2.getSignaturePubKey());
        assertTrue(service.accountIsSigner(aew1));
        assertTrue(service.accountIsSigner(aew2));
        assertFalse(service.accountIsSigner(aew3));
        assertTrue(signedWitnessService.isSignedAccountAgeWitness(aew3));

        // Remove SignedWitness signed by arbitrator
        @SuppressWarnings("OptionalGetWithoutIsPresent")
        var signedWitnessArb = signedWitnessService.getSignedWitnessMapValues().stream()
                .filter(sw -> sw.getVerificationMethod() == SignedWitness.VerificationMethod.ARBITRATOR)
                .findAny()
                .get();
        signedWitnessService.removeSignedWitness(signedWitnessArb);
        assertEquals(signedWitnessService.getSignedWitnessMapValues().size(), 2);

        // Check that no account age witness is a signer
        assertFalse(service.accountIsSigner(aew1));
        assertFalse(service.accountIsSigner(aew2));
        assertFalse(service.accountIsSigner(aew3));
        assertFalse(signedWitnessService.isSignedAccountAgeWitness(aew2));

        // Sign dummy AccountAgeWitness using signer key from SW_1
        assertEquals(signedWitnessService.getRootSignedWitnessSet(false).size(), 1);

        // TODO: move this to accountagewitnessservice
        @SuppressWarnings("OptionalGetWithoutIsPresent")
        var orphanedSignedWitness = signedWitnessService.getRootSignedWitnessSet(false).stream().findAny().get();
        var dummyAccountAgeWitnessHash = Hash.getRipemd160hash(orphanedSignedWitness.getSignerPubKey());
        var dummyAEW = new AccountAgeWitness(dummyAccountAgeWitnessHash,
                orphanedSignedWitness.getDate() -
                        (TimeUnit.DAYS.toMillis(SignedWitnessService.SIGNER_AGE_DAYS + 1)));
        service.arbitratorSignAccountAgeWitness(
                dummyAEW, arbitratorKey, orphanedSignedWitness.getSignerPubKey(), dummyAEW.getDate());

        assertFalse(service.accountIsSigner(aew1));
        assertTrue(service.accountIsSigner(aew2));
        assertFalse(service.accountIsSigner(aew3));
        assertTrue(signedWitnessService.isSignedAccountAgeWitness(aew2));
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // publishOwnSignedWitness: the SignedWitness we received in the PayoutTxPublishedMessage
    ///////////////////////////////////////////////////////////////////////////////////////////

    // We are the buyer and the seller signed the account we used in that trade. These tests cover that the
    // expected values are taken from the trade. The admission rules themselves are covered by
    // SignedWitnessServiceTest.
    @Test
    public void publishOwnSignedWitnessTakesTheExpectedValuesFromTheTrade() throws CryptoException {
        KeyRing buyerKeyRing = new KeyRing(new KeyStorage(dir1));
        KeyRing sellerKeyRing = new KeyRing(new KeyStorage(dir2));
        setupService(buyerKeyRing);
        Trade trade = setupTrade(buyerKeyRing, sellerKeyRing);

        AccountAgeWitness myWitness = service.getMyWitness(
                trade.getProcessModel().getPaymentAccountPayload(trade));
        SignedWitness signedWitness = new SignedWitness(SignedWitness.VerificationMethod.TRADE,
                myWitness.getHash(),
                Sig.sign(sellerKeyRing.getSignatureKeyPair().getPrivate(), myWitness.getHash()),
                sellerKeyRing.getPubKeyRing().getSignaturePubKeyBytes(),
                buyerKeyRing.getPubKeyRing().getSignaturePubKeyBytes(),
                NOW - 1000,
                trade.getAmount().value);

        assertTrue(service.publishOwnSignedWitness(signedWitness, trade));
        assertTrue(signedWitnessService.getSignedWitnessMapValues().contains(signedWitness));
    }

    @Test
    public void publishOwnSignedWitnessRejectsATradeWhereWeAreNotTheBuyer() throws CryptoException {
        KeyRing buyerKeyRing = new KeyRing(new KeyStorage(dir1));
        KeyRing sellerKeyRing = new KeyRing(new KeyStorage(dir2));
        setupService(sellerKeyRing);
        Trade trade = setupTrade(buyerKeyRing, sellerKeyRing);

        AccountAgeWitness buyerWitness = service.getNewWitness(
                trade.getProcessModel().getPaymentAccountPayload(trade), buyerKeyRing.getPubKeyRing());
        SignedWitness signedWitness = new SignedWitness(SignedWitness.VerificationMethod.TRADE,
                buyerWitness.getHash(),
                Sig.sign(sellerKeyRing.getSignatureKeyPair().getPrivate(), buyerWitness.getHash()),
                sellerKeyRing.getPubKeyRing().getSignaturePubKeyBytes(),
                buyerKeyRing.getPubKeyRing().getSignaturePubKeyBytes(),
                NOW - 1000,
                trade.getAmount().value);

        assertFalse(service.publishOwnSignedWitness(signedWitness, trade));
        assertFalse(signedWitnessService.getSignedWitnessMapValues().contains(signedWitness));
    }

    private Trade setupTrade(KeyRing buyerKeyRing, KeyRing sellerKeyRing) {
        PaymentAccountPayload buyerPaymentAccountPayload =
                new SepaAccountPayload(PaymentMethod.SEPA_ID, "1", CountryUtil.getAllSepaCountries());
        // The seller is signed by an arbitrator, so the seller is a valid signer.
        ECKey arbitratorKey = LowRSigningKey.from(new ECKey());
        byte[] sellerAccountHash = Hash.getSha256Ripemd160hash("seller".getBytes(StandardCharsets.UTF_8));
        signedWitnessService.addToMap(new SignedWitness(SignedWitness.VerificationMethod.ARBITRATOR,
                sellerAccountHash,
                arbitratorKey.signMessage(Utilities.encodeToHex(sellerAccountHash)).getBytes(StandardCharsets.UTF_8),
                arbitratorKey.getPubKey(),
                sellerKeyRing.getPubKeyRing().getSignaturePubKeyBytes(),
                NOW - TimeUnit.DAYS.toMillis(90),
                SignedWitnessService.MINIMUM_TRADE_AMOUNT_FOR_SIGNING.value));

        Contract contract = mock(Contract.class);
        when(contract.getBuyerPubKeyRing()).thenReturn(buyerKeyRing.getPubKeyRing());
        when(contract.getSellerPubKeyRing()).thenReturn(sellerKeyRing.getPubKeyRing());
        ProcessModel processModel = mock(ProcessModel.class);
        Trade trade = mock(Trade.class);
        when(trade.getContract()).thenReturn(contract);
        when(trade.getProcessModel()).thenReturn(processModel);
        when(processModel.getPaymentAccountPayload(trade)).thenReturn(buyerPaymentAccountPayload);
        when(trade.getAmount()).thenReturn(SignedWitnessService.MINIMUM_TRADE_AMOUNT_FOR_SIGNING);
        when(trade.getDate()).thenReturn(new Date(NOW - TimeUnit.DAYS.toMillis(2)));
        when(trade.getId()).thenReturn("trade1");
        return trade;
    }

    private void signAccountAgeWitness(AccountAgeWitness accountAgeWitness,
                                       PublicKey witnessOwnerPubKey,
                                       long time,
                                       KeyRing signerKeyRing) throws CryptoException {
        byte[] signature = Sig.sign(signerKeyRing.getSignatureKeyPair().getPrivate(), accountAgeWitness.getHash());
        SignedWitness signedWitness = new SignedWitness(SignedWitness.VerificationMethod.TRADE,
                accountAgeWitness.getHash(),
                signature,
                signerKeyRing.getSignatureKeyPair().getPublic().getEncoded(),
                witnessOwnerPubKey.getEncoded(),
                time,
                SignedWitnessService.MINIMUM_TRADE_AMOUNT_FOR_SIGNING.value);
        signedWitnessService.addToMap(signedWitness);
    }

}
