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

package bisq.desktop.main.offer.offerbook;

import bisq.desktop.main.offer.offerbook.OfferBookListItem.WitnessAgeData;

import bisq.core.account.sign.SignedWitness;
import bisq.core.account.sign.SignedWitnessService;
import bisq.core.account.witness.AccountAgeWitness;
import bisq.core.account.witness.AccountAgeWitnessService;
import bisq.core.crypto.LowRSigningKey;
import bisq.core.filter.DenyList;
import bisq.core.filter.FilterManager;
import bisq.core.filter.FilterPolicyService;
import bisq.core.locale.CountryUtil;
import bisq.core.locale.Res;
import bisq.core.offer.Offer;
import bisq.core.offer.OfferDirection;
import bisq.core.offer.bisq_v1.OfferPayload;
import bisq.core.offer.bisq_v1.OfferPayloadExtraDataMap;
import bisq.core.payment.payload.PaymentMethod;
import bisq.core.payment.payload.SepaAccountPayload;
import bisq.core.support.dispute.arbitration.arbitrator.ArbitratorManager;

import bisq.network.p2p.NodeAddress;
import bisq.network.p2p.P2PService;
import bisq.network.p2p.storage.persistence.AppendOnlyDataStoreService;

import bisq.common.crypto.Encryption;
import bisq.common.crypto.KeyRing;
import bisq.common.crypto.PubKeyRing;
import bisq.common.crypto.Sig;
import bisq.common.util.Utilities;

import org.bitcoinj.core.Coin;
import org.bitcoinj.core.ECKey;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import java.nio.charset.StandardCharsets;

import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class OfferBookListItemTest {
    private static final long NOW = Instant.parse("2026-11-03T12:00:00Z").toEpochMilli();

    private final Clock clock = Clock.fixed(Instant.ofEpochMilli(NOW), ZoneOffset.UTC);
    private final FilterManager filterManager = mock(FilterManager.class);
    private final ECKey arbitratorKey = LowRSigningKey.from(new ECKey());
    private SignedWitnessService signedWitnessService;
    private AccountAgeWitnessService accountAgeWitnessService;

    @BeforeEach
    public void setup() {
        Res.setup();
        FilterPolicyService filterPolicyService = new FilterPolicyService(DenyList.fromProperties(new Properties()),
                filterManager);
        @SuppressWarnings("deprecation") ArbitratorManager arbitratorManager = mock(ArbitratorManager.class);
        when(arbitratorManager.isPublicKeyInList(any())).thenReturn(true);
        KeyRing keyRing = mock(KeyRing.class);
        signedWitnessService = new SignedWitnessService(keyRing, mock(P2PService.class), arbitratorManager, null,
                mock(AppendOnlyDataStoreService.class), filterPolicyService, false, clock);
        accountAgeWitnessService = new AccountAgeWitnessService(keyRing, null, null, signedWitnessService, null, null,
                mock(AppendOnlyDataStoreService.class), clock, null, filterPolicyService);
    }

    @Test
    public void anAccountSignedByAnArbitratorIsShownAsSigned() {
        PubKeyRing maker = newPubKeyRing();

        WitnessAgeData witnessAgeData = getWitnessAgeData(maker, maker.getSignaturePubKeyBytes());

        assertTrue(witnessAgeData.isAccountSigned());
        assertEquals(WitnessAgeData.TYPE_SIGNED_AND_LIMIT_LIFTED, witnessAgeData.getType());
    }

    @Test
    public void aBannedAccountIsShownAsNotSigned() {
        PubKeyRing maker = newPubKeyRing();
        ban(maker);

        WitnessAgeData witnessAgeData = getWitnessAgeData(maker, maker.getSignaturePubKeyBytes());

        assertFalse(witnessAgeData.isAccountSigned());
        assertEquals(WitnessAgeData.TYPE_NOT_SIGNED, witnessAgeData.getType());
    }

    @Test
    public void aBannedAccountWithAValidArbitratorSignatureIsShownAsNotSigned() {
        PubKeyRing maker = newPubKeyRing();
        ban(maker);
        // Signing by an arbitrator before 09141eba92 put the key of the counterparty into the owner key, so the
        // signature counts although the owner is banned
        byte[] counterpartyKey = Sig.getPublicKeyBytes(Sig.generateKeyPair().getPublic());

        WitnessAgeData witnessAgeData = getWitnessAgeData(maker, counterpartyKey);

        assertFalse(witnessAgeData.isAccountSigned());
        assertEquals(WitnessAgeData.TYPE_NOT_SIGNED, witnessAgeData.getType());
    }

    @Test
    public void aBannedAccountWithoutAnyWitnessIsShownAsNotSigned() {
        PubKeyRing maker = newPubKeyRing();
        ban(maker);
        AccountAgeWitness unknownWitness = accountAgeWitnessService.getNewWitness(newPaymentAccountPayload(), maker);

        WitnessAgeData witnessAgeData = new OfferBookListItem(newOffer(maker, unknownWitness))
                .getWitnessAgeData(accountAgeWitnessService, signedWitnessService);

        assertFalse(witnessAgeData.isAccountSigned());
        assertEquals(WitnessAgeData.TYPE_NOT_SIGNED, witnessAgeData.getType());
    }

    // The offer of an account which an arbitrator signed 100 days ago, with the given key in the owner key
    private WitnessAgeData getWitnessAgeData(PubKeyRing maker, byte[] ownerKey) {
        AccountAgeWitness witness = accountAgeWitnessService.getNewWitness(newPaymentAccountPayload(), maker);
        accountAgeWitnessService.addToMap(witness);
        signedWitnessService.addToMap(new SignedWitness(SignedWitness.VerificationMethod.ARBITRATOR,
                witness.getHash(),
                arbitratorKey.signMessage(Utilities.encodeToHex(witness.getHash())).getBytes(StandardCharsets.UTF_8),
                arbitratorKey.getPubKey(),
                ownerKey,
                NOW - TimeUnit.DAYS.toMillis(100),
                Coin.COIN.value));
        return new OfferBookListItem(newOffer(maker, witness))
                .getWitnessAgeData(accountAgeWitnessService, signedWitnessService);
    }

    private void ban(PubKeyRing owner) {
        when(filterManager.isWitnessSignerPubKeyBanned(Utilities.encodeToHex(owner.getSignaturePubKeyBytes())))
                .thenReturn(true);
    }

    private static PubKeyRing newPubKeyRing() {
        return new PubKeyRing(Sig.generateKeyPair().getPublic(), Encryption.generateKeyPair().getPublic());
    }

    private static SepaAccountPayload newPaymentAccountPayload() {
        return new SepaAccountPayload(PaymentMethod.SEPA_ID, "id", CountryUtil.getAllSepaCountries());
    }

    private static Offer newOffer(PubKeyRing maker, AccountAgeWitness witness) {
        OfferPayloadExtraDataMap extraDataMap = new OfferPayloadExtraDataMap(Map.of(
                OfferPayloadExtraDataMap.Keys.ACCOUNT_AGE_WITNESS_HASH, Utilities.encodeToHex(witness.getHash())));
        return new Offer(new OfferPayload("id",
                0L,
                new NodeAddress("maker.onion", 9999),
                maker,
                OfferDirection.BUY,
                100000L,
                0.0,
                false,
                100000L,
                100000L,
                "BTC",
                "EUR",
                List.of(),
                List.of(),
                PaymentMethod.SEPA_ID,
                "",
                // An offer in the offer book has its fee paid
                "offerFeePaymentTxId",
                null,
                null,
                null,
                null,
                "",
                0L,
                0L,
                0L,
                false,
                0L,
                0L,
                0L,
                0L,
                false,
                false,
                0L,
                0L,
                false,
                null,
                extraDataMap,
                0));
    }
}
