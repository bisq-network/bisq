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
import bisq.core.account.witness.AccountAgeWitnessService.SignState;
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
import bisq.core.payment.payload.PaymentAccountPayload;
import bisq.core.payment.payload.PaymentMethod;
import bisq.core.payment.payload.SepaAccountPayload;
import bisq.core.support.dispute.arbitration.arbitrator.ArbitratorManager;
import bisq.core.trade.model.bisq_v1.Trade;
import bisq.core.trade.protocol.bisq_v1.model.ProcessModel;
import bisq.core.trade.protocol.bisq_v1.model.TradingPeer;

import bisq.network.p2p.P2PService;
import bisq.network.p2p.storage.persistence.AppendOnlyDataStoreService;

import bisq.common.crypto.CryptoException;
import bisq.common.crypto.Encryption;
import bisq.common.crypto.KeyRing;
import bisq.common.crypto.KeyStorage;
import bisq.common.crypto.PubKeyRing;
import bisq.common.crypto.Sig;
import bisq.common.util.Utilities;

import org.bitcoinj.core.Coin;
import org.bitcoinj.core.ECKey;

import java.security.KeyPair;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import java.nio.charset.StandardCharsets;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;

import java.util.Map;
import java.util.Properties;
import java.util.Random;
import java.util.concurrent.TimeUnit;

import javax.annotation.Nullable;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

// The ban of an account is decided by the key which is known to be the owner's, never by the owner key of a signed
// witness, which anyone can publish for the witness hash of any offer.
public class AccountAgeWitnessServiceBanTest {
    private static final long NOW = Instant.parse("2026-11-03T12:00:00Z").toEpochMilli();
    private static final String BANNED_SIGNER_KEYS = "bannedAccountWitnessSignerPubKeys";

    // A witness for the account of another owner which carries a banned key as owner
    private enum WitnessWithBannedOwner {
        // Random bytes as signature, as anyone can publish it
        RANDOM_SIGNATURE,
        // A valid trade signature, as any key can create it for any witness hash
        TRADE_SIGNED,
        // Signing by an arbitrator before 09141eba92 put the key of the counterparty into the owner field
        LEGACY_ARBITRATOR_SIGNED
    }

    private final Clock clock = Clock.fixed(Instant.ofEpochMilli(NOW), ZoneOffset.UTC);
    private final FilterManager filterManager = mock(FilterManager.class);
    private final ECKey arbitratorKey = LowRSigningKey.from(new ECKey());
    @TempDir
    File keyStorageDir;
    private Properties denyListProperties;
    private byte[] bundledBannedKey;
    private KeyRing myKeyRing;
    private SignedWitnessService signedWitnessService;
    private AccountAgeWitnessService service;

    @BeforeEach
    public void setup() throws IOException {
        Res.setup();
        denyListProperties = new Properties();
        try (InputStream in = DenyList.class.getResourceAsStream("/denylist/btc_mainnet.denylist")) {
            denyListProperties.load(in);
        }
        String bundledBannedKeys = denyListProperties.getProperty(BANNED_SIGNER_KEYS, "");
        assertFalse(bundledBannedKeys.isBlank(), "the bundled deny list has a banned key");
        bundledBannedKey = Utilities.decodeFromHex(bundledBannedKeys.split(",")[0].trim());
        myKeyRing = new KeyRing(new KeyStorage(keyStorageDir));
        setupServices();
    }

    private void setupServices() {
        FilterPolicyService filterPolicyService = new FilterPolicyService(DenyList.fromProperties(denyListProperties),
                filterManager);
        @SuppressWarnings("deprecation") ArbitratorManager arbitratorManager = mock(ArbitratorManager.class);
        when(arbitratorManager.isPublicKeyInList(any())).thenReturn(true);
        signedWitnessService = new SignedWitnessService(myKeyRing, mock(P2PService.class), arbitratorManager, null,
                mock(AppendOnlyDataStoreService.class), filterPolicyService, false, clock);
        service = new AccountAgeWitnessService(myKeyRing, null, null, signedWitnessService, null, null,
                mock(AppendOnlyDataStoreService.class), clock, null, filterPolicyService);
    }

    @ParameterizedTest
    @EnumSource(WitnessWithBannedOwner.class)
    public void aWitnessWithABannedOwnerKeyDoesNotBanTheMaker(WitnessWithBannedOwner kind) throws Exception {
        PubKeyRing maker = newPubKeyRing();
        AccountAgeWitness witness = addArbitratorSignedAccount(newPaymentAccountPayload(), maker);
        Offer offer = newOffer(maker, witness);
        assertEquals(SignState.ARBITRATOR, service.getSignState(offer));

        addWitnessWithBannedOwner(kind, witness);

        assertEquals(SignState.ARBITRATOR, service.getSignState(offer));
    }

    @ParameterizedTest
    @EnumSource(WitnessWithBannedOwner.class)
    public void aWitnessWithABannedOwnerKeyDoesNotBanTheTradePeer(WitnessWithBannedOwner kind) throws Exception {
        PubKeyRing peer = newPubKeyRing();
        PaymentAccountPayload peersPaymentAccountPayload = newPaymentAccountPayload();
        AccountAgeWitness witness = addArbitratorSignedAccount(peersPaymentAccountPayload, peer);
        Trade trade = newTrade(peer, peersPaymentAccountPayload);
        assertEquals(SignState.ARBITRATOR, service.getSignState(trade));

        addWitnessWithBannedOwner(kind, witness);

        assertEquals(SignState.ARBITRATOR, service.getSignState(trade));
    }

    @ParameterizedTest
    @EnumSource(WitnessWithBannedOwner.class)
    public void aWitnessWithABannedOwnerKeyDoesNotBanOurAccount(WitnessWithBannedOwner kind) throws Exception {
        PaymentAccountPayload myPaymentAccountPayload = newPaymentAccountPayload();
        AccountAgeWitness witness = addArbitratorSignedAccount(myPaymentAccountPayload, myKeyRing.getPubKeyRing());
        assertEquals(SignState.ARBITRATOR, service.getMySignState(myPaymentAccountPayload));

        addWitnessWithBannedOwner(kind, witness);

        assertEquals(SignState.ARBITRATOR, service.getMySignState(myPaymentAccountPayload));
    }

    @Test
    public void aMakerOnTheDenyListIsBanned() throws Exception {
        PubKeyRing maker = newPubKeyRing();
        addToDenyList(maker);
        Offer offer = newOffer(maker, addArbitratorSignedAccount(newPaymentAccountPayload(), maker));

        assertEquals(SignState.BANNED, service.getSignState(offer));
    }

    @Test
    public void theBanFollowsTheMakerKeyAndNotTheOwnerKeyOfTheWitness() throws Exception {
        PubKeyRing maker = newPubKeyRing();
        addToDenyList(maker);
        AccountAgeWitness witness = service.getNewWitness(newPaymentAccountPayload(), maker);
        service.addToMap(witness);
        // A legacy arbitrator witness carries the key of the counterparty as owner
        addArbitratorSignedWitness(witness, Sig.getPublicKeyBytes(Sig.generateKeyPair().getPublic()));

        assertEquals(SignState.BANNED, service.getSignState(newOffer(maker, witness)));
    }

    @Test
    public void aTradePeerBannedByTheFilterIsBanned() throws Exception {
        PubKeyRing peer = newPubKeyRing();
        PaymentAccountPayload peersPaymentAccountPayload = newPaymentAccountPayload();
        addArbitratorSignedAccount(peersPaymentAccountPayload, peer);
        when(filterManager.isWitnessSignerPubKeyBanned(Utilities.encodeToHex(peer.getSignaturePubKeyBytes())))
                .thenReturn(true);

        assertEquals(SignState.BANNED, service.getSignState(newTrade(peer, peersPaymentAccountPayload)));
    }

    @Test
    public void aMakerOnTheDenyListIsBannedAlsoWithAWitnessWhichIsNotKnown() {
        PubKeyRing maker = newPubKeyRing();
        addToDenyList(maker);
        Offer offer = newOffer(maker, service.getNewWitness(newPaymentAccountPayload(), maker));
        assertTrue(service.findWitness(offer).isEmpty());

        assertEquals(SignState.BANNED, service.getSignState(offer));
    }

    @Test
    public void aBannedTradePeerIsBannedBeforeItsPaymentAccountIsKnown() {
        PubKeyRing peer = newPubKeyRing();
        when(filterManager.isWitnessSignerPubKeyBanned(Utilities.encodeToHex(peer.getSignaturePubKeyBytes())))
                .thenReturn(true);

        assertEquals(SignState.BANNED, service.getSignState(newTrade(peer, null)));
    }

    @Test
    public void aTradeIsUnsignedAsLongAsTheKeyOfThePeerIsNotKnown() {
        when(filterManager.isWitnessSignerPubKeyBanned(any())).thenReturn(true);

        assertEquals(SignState.UNSIGNED, service.getSignState(newTrade(null, newPaymentAccountPayload())));
    }

    @Test
    public void ourBannedAccountIsBannedAlsoWithoutASignedWitness() {
        addToDenyList(myKeyRing.getPubKeyRing());
        PaymentAccountPayload myPaymentAccountPayload = newPaymentAccountPayload();
        service.addToMap(service.getNewWitness(myPaymentAccountPayload, myKeyRing.getPubKeyRing()));

        assertEquals(SignState.BANNED, service.getMySignState(myPaymentAccountPayload));
    }

    private void addWitnessWithBannedOwner(WitnessWithBannedOwner kind,
                                           AccountAgeWitness witness) throws CryptoException {
        byte[] bannedOwnerKey = bundledBannedKey;
        switch (kind) {
            case RANDOM_SIGNATURE:
                byte[] signature = new byte[46];
                new Random(1).nextBytes(signature);
                publish(new SignedWitness(SignedWitness.VerificationMethod.TRADE,
                        witness.getHash(),
                        signature,
                        Sig.getPublicKeyBytes(Sig.generateKeyPair().getPublic()),
                        bannedOwnerKey,
                        NOW,
                        Coin.COIN.value));
                break;
            case TRADE_SIGNED:
                KeyPair signer = Sig.generateKeyPair();
                publish(new SignedWitness(SignedWitness.VerificationMethod.TRADE,
                        witness.getHash(),
                        Sig.sign(signer.getPrivate(), witness.getHash()),
                        Sig.getPublicKeyBytes(signer.getPublic()),
                        bannedOwnerKey,
                        NOW,
                        Coin.COIN.value));
                break;
            case LEGACY_ARBITRATOR_SIGNED:
            default:
                // Such a witness is old and comes with the bundled store, not as a new witness from the network
                addArbitratorSignedWitness(witness, bannedOwnerKey);
        }
    }

    // The checks of the P2P store for a witness from the network, then the store listener of the service
    private void publish(SignedWitness signedWitness) {
        assertTrue(signedWitness.verifyHashSize());
        assertTrue(signedWitness.isDateInTolerance(clock));
        signedWitnessService.addToMap(signedWitness);
        assertTrue(signedWitnessService.getSignedWitnessMapValues().contains(signedWitness));
    }

    private AccountAgeWitness addArbitratorSignedAccount(PaymentAccountPayload paymentAccountPayload,
                                                         PubKeyRing owner) {
        AccountAgeWitness witness = service.getNewWitness(paymentAccountPayload, owner);
        service.addToMap(witness);
        addArbitratorSignedWitness(witness, owner.getSignaturePubKeyBytes());
        return witness;
    }

    private void addArbitratorSignedWitness(AccountAgeWitness witness, byte[] ownerKey) {
        signedWitnessService.addToMap(newArbitratorSignedWitness(witness, ownerKey));
    }

    private SignedWitness newArbitratorSignedWitness(AccountAgeWitness witness, byte[] ownerKey) {
        return new SignedWitness(SignedWitness.VerificationMethod.ARBITRATOR,
                witness.getHash(),
                arbitratorKey.signMessage(Utilities.encodeToHex(witness.getHash())).getBytes(StandardCharsets.UTF_8),
                arbitratorKey.getPubKey(),
                ownerKey,
                NOW - TimeUnit.DAYS.toMillis(100),
                Coin.COIN.value);
    }

    // The services read the deny list when they are created, so this creates them again and must come before any data
    private void addToDenyList(PubKeyRing owner) {
        assertTrue(signedWitnessService.getSignedWitnessMapValues().isEmpty(), "add to the deny list before any data");
        denyListProperties.setProperty(BANNED_SIGNER_KEYS, denyListProperties.getProperty(BANNED_SIGNER_KEYS) + "," +
                Utilities.encodeToHex(owner.getSignaturePubKeyBytes()));
        setupServices();
    }

    private static PubKeyRing newPubKeyRing() {
        return new PubKeyRing(Sig.generateKeyPair().getPublic(), Encryption.generateKeyPair().getPublic());
    }

    private static PaymentAccountPayload newPaymentAccountPayload() {
        return new SepaAccountPayload(PaymentMethod.SEPA_ID, "id", CountryUtil.getAllSepaCountries());
    }

    private static Offer newOffer(PubKeyRing maker, AccountAgeWitness witness) {
        OfferPayloadExtraDataMap extraDataMap = new OfferPayloadExtraDataMap(Map.of(
                OfferPayloadExtraDataMap.Keys.ACCOUNT_AGE_WITNESS_HASH, Utilities.encodeToHex(witness.getHash())));
        return new Offer(new OfferPayload("id",
                0L,
                null,
                maker,
                OfferDirection.BUY,
                100000L,
                0.0,
                false,
                100000L,
                100000L,
                "BTC",
                "EUR",
                null,
                null,
                PaymentMethod.SEPA_ID,
                "",
                null,
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

    private static Trade newTrade(@Nullable PubKeyRing peer,
                                  @Nullable PaymentAccountPayload peersPaymentAccountPayload) {
        TradingPeer tradingPeer = new TradingPeer();
        tradingPeer.setPubKeyRing(peer);
        tradingPeer.setPaymentAccountPayload(peersPaymentAccountPayload);
        ProcessModel processModel = new ProcessModel("id", "accountId", newPubKeyRing(), tradingPeer);
        Trade trade = mock(Trade.class);
        when(trade.getProcessModel()).thenReturn(processModel);
        return trade;
    }
}
