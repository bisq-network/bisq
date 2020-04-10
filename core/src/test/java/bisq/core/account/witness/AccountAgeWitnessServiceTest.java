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
import bisq.core.filter.FilterManager;
import bisq.core.locale.CountryUtil;
import bisq.core.offer.OfferPayload;
import bisq.core.payment.ChargeBackRisk;
import bisq.core.payment.payload.PaymentAccountPayload;
import bisq.core.payment.payload.PaymentMethod;
import bisq.core.payment.payload.SepaAccountPayload;
import bisq.core.support.SupportType;
import bisq.core.support.dispute.Dispute;
import bisq.core.support.dispute.DisputeResult;
import bisq.core.support.dispute.arbitration.TraderDataItem;
import bisq.core.support.dispute.arbitration.arbitrator.ArbitratorManager;
import bisq.core.trade.Contract;

import bisq.network.p2p.P2PService;
import bisq.network.p2p.storage.persistence.AppendOnlyDataStoreService;

import bisq.common.crypto.CryptoException;
import bisq.common.crypto.KeyRing;
import bisq.common.crypto.KeyStorage;
import bisq.common.crypto.PubKeyRing;
import bisq.common.crypto.Sig;
import bisq.common.util.Utilities;

import org.bitcoinj.core.Coin;
import org.bitcoinj.core.ECKey;

import java.security.KeyPair;
import java.security.PublicKey;

import java.io.File;
import java.io.IOException;

import java.util.ArrayList;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.List;

import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;


// Restricted default Java security policy on Travis does not allow long keys, so test fails.
// Using Utilities.removeCryptographyRestrictions(); did not work.
//@Ignore
public class AccountAgeWitnessServiceTest {
    private PublicKey publicKey;
    private KeyPair keypair;
    private SignedWitnessService signedWitnessService;
    private AccountAgeWitnessService service;
    private ChargeBackRisk chargeBackRisk;
    private FilterManager filterManager;

    @Before
    public void setup() {
        chargeBackRisk = mock(ChargeBackRisk.class);
        AppendOnlyDataStoreService dataStoreService = mock(AppendOnlyDataStoreService.class);
        KeyRing keyRing = mock(KeyRing.class);
        P2PService p2pService = mock(P2PService.class);
        ArbitratorManager arbitratorManager = mock(ArbitratorManager.class);
        when(arbitratorManager.isPublicKeyInList(any())).thenReturn(true);
        AppendOnlyDataStoreService appendOnlyDataStoreService = mock(AppendOnlyDataStoreService.class);
        filterManager = mock(FilterManager.class);
        signedWitnessService = new SignedWitnessService(keyRing, p2pService, arbitratorManager, null, appendOnlyDataStoreService, null, filterManager);
        service = new AccountAgeWitnessService(null, null, null, signedWitnessService, chargeBackRisk, null, dataStoreService, filterManager);
        keypair = Sig.generateKeyPair();
        publicKey = keypair.getPublic();
    }

    @After
    public void tearDown() {
        // Do teardown stuff
    }

    @Ignore
    @Test
    public void testIsTradeDateAfterReleaseDate() throws CryptoException {
        Date ageWitnessReleaseDate = new GregorianCalendar(2017, 9, 23).getTime();
        Date tradeDate = new GregorianCalendar(2017, 10, 1).getTime();
        assertTrue(service.isDateAfterReleaseDate(tradeDate.getTime(), ageWitnessReleaseDate, errorMessage -> {
        }));
        tradeDate = new GregorianCalendar(2017, 9, 23).getTime();
        assertTrue(service.isDateAfterReleaseDate(tradeDate.getTime(), ageWitnessReleaseDate, errorMessage -> {
        }));
        tradeDate = new GregorianCalendar(2017, 9, 22, 0, 0, 1).getTime();
        assertTrue(service.isDateAfterReleaseDate(tradeDate.getTime(), ageWitnessReleaseDate, errorMessage -> {
        }));
        tradeDate = new GregorianCalendar(2017, 9, 22).getTime();
        assertFalse(service.isDateAfterReleaseDate(tradeDate.getTime(), ageWitnessReleaseDate, errorMessage -> {
        }));
        tradeDate = new GregorianCalendar(2017, 9, 21).getTime();
        assertFalse(service.isDateAfterReleaseDate(tradeDate.getTime(), ageWitnessReleaseDate, errorMessage -> {
        }));
    }

    @Ignore
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
    public void testArbitratorSignWitness() throws IOException {
        // Setup temp storage dir
        File dir1 = File.createTempFile("temp_tests1", "");
        dir1.delete();
        dir1.mkdir();
        File dir2 = File.createTempFile("temp_tests1", "");
        dir2.delete();
        dir2.mkdir();

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
        disputes.add(new Dispute(
                "trade1",
                0,
                true,
                true,
                buyerPubKeyRing,
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
        disputes.get(0).getIsClosedProperty().set(true);
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
                false));

        // Filtermanager says nothing is filtered
        when(filterManager.isNodeAddressBanned(any())).thenReturn(false);
        when(filterManager.isCurrencyBanned(any())).thenReturn(false);
        when(filterManager.isPaymentMethodBanned(any())).thenReturn(false);
        when(filterManager.isPeersPaymentAccountDataAreBanned(any(), any())).thenReturn(false);
        when(filterManager.isSignerPubKeyBanned(any())).thenReturn(false);

        when(chargeBackRisk.hasChargebackRisk(any(), any())).thenReturn(true);

        when(contract.getPaymentMethodId()).thenReturn(PaymentMethod.SEPA_ID);
        when(contract.getTradeAmount()).thenReturn(Coin.parseCoin("0.01"));
        when(contract.getBuyerPubKeyRing()).thenReturn(buyerPubKeyRing);
        when(contract.getSellerPubKeyRing()).thenReturn(sellerPubKeyRing);
        when(contract.getBuyerPaymentAccountPayload()).thenReturn(buyerPaymentAccountPayload);
        when(contract.getSellerPaymentAccountPayload()).thenReturn(sellerPaymentAccountPayload);
        when(contract.getOfferPayload()).thenReturn(mock(OfferPayload.class));
        List<TraderDataItem> items = service.getTraderPaymentAccounts(now, PaymentMethod.SEPA, disputes);
        assertEquals(items.size(), 2);

        // Setup a mocked arbitrator key
        ECKey arbitratorKey = mock(ECKey.class);
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
        assertEquals(Utilities.bytesAsHexString(foundBuyerSignedWitness.getAccountAgeWitnessHash()),
                Utilities.bytesAsHexString(buyerAccountAgeWitness.getHash()));
        SignedWitness foundSellerSignedWitness = signedWitnessService.getSignedWitnessSetByOwnerPubKey(
                sellerPubKeyRing.getSignaturePubKeyBytes()).stream()
                .findFirst()
                .orElse(null);
        assertEquals(Utilities.bytesAsHexString(foundSellerSignedWitness.getAccountAgeWitnessHash()),
                Utilities.bytesAsHexString(sellerAccountAgeWitness.getHash()));
    }
}
